package com.val0x2f.chunklod.cache;

import com.val0x2f.chunklod.core.TaskScheduler;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Client-thread snapshots plus worker baking and one-file-session persistence.
 * World access is limited to capture(); disk access is limited to the IO worker.
 */
public final class ChunkCacheManager implements AutoCloseable {
    private static final int DATA_VERSION = 2;
    private static final int WRITE_BATCH_RECORDS = 48;
    private static final long WRITE_BATCH_INTERVAL_NANOS = 750_000_000L;
    // 2,048 entries retain a useful warm radius without rebuilding thousands of heap holders at join.
    private static final int MAX_RESTORE_ENTRIES = 2_048;
    private static final int MAX_PENDING_UNLOAD_CAPTURES = 24;
    private final TaskScheduler scheduler;
    private final CacheInvalidator invalidator = new CacheInvalidator();
    private final CacheEntryBaker baker = new CacheEntryBaker();
    private final ChunkSnapshotPool snapshots = new ChunkSnapshotPool(48);
    private final ConcurrentHashMap<CacheKey, ChunkCacheEntry> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheKey, Long> queuedRevisions = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ChunkCacheEntry> pendingWrites = new ConcurrentLinkedQueue<>();
    // Client events and tick run on the same client thread; bounded to prevent teleport/unload bursts.
    private final ArrayDeque<PendingCapture> pendingUnloadCaptures = new ArrayDeque<>(MAX_PENDING_UNLOAD_CAPTURES);
    private int captureTick;
    private final AtomicBoolean writeDrainScheduled = new AtomicBoolean();
    private final AtomicInteger pendingWriteCount = new AtomicInteger();
    private final AtomicBoolean openingSession = new AtomicBoolean();
    private final SessionCacheStore sessionStore = new SessionCacheStore();
    private final Path cacheRoot = FabricLoader.getInstance().getGameDir().resolve("val0x2f-cache");
    private volatile long worldEpoch;
    private volatile long activeWorldFingerprint = Long.MIN_VALUE;
    private volatile int activeDimensionHash;
    private volatile boolean sessionReady;
    private volatile long lastWriteScheduleNanos;

    public ChunkCacheManager(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void captureLoadedChunk(ClientLevel world, LevelChunk chunk) {
        // Joining a server can stream dozens of chunks in one frame. Opening the session is cheap;
        // the expensive 256-column snapshot is deferred until vanilla releases the chunk.
        ensureSession(world);
    }

    public void captureUnloadingChunk(ClientLevel world, LevelChunk chunk) {
        ensureSession(world);
        if (pendingUnloadCaptures.size() >= MAX_PENDING_UNLOAD_CAPTURES) pendingUnloadCaptures.removeFirst();
        pendingUnloadCaptures.addLast(new PendingCapture(world, chunk));
    }

    private void ensureSession(ClientLevel world) {
        long fingerprint = worldFingerprint(world);
        int dimensionHash = world.dimension().toString().hashCode();
        if (fingerprint == activeWorldFingerprint && dimensionHash == activeDimensionHash) return;
        if (!openingSession.compareAndSet(false, true)) return;
        long epoch = ++worldEpoch;
        activeWorldFingerprint = fingerprint;
        activeDimensionHash = dimensionHash;
        sessionReady = false;
        entries.clear();
        queuedRevisions.clear();
        pendingUnloadCaptures.clear();
        invalidator.clear();
        String dimensionId = world.dimension().toString();
        Path sessionFile = cacheRoot.resolve(Long.toUnsignedString(fingerprint, 16))
                .resolve(Integer.toUnsignedString(dimensionHash, 16)).resolve("session-active.v0ls");
        scheduler.submitIo(() -> {
            try {
                sessionStore.close();
                sessionStore.open(sessionFile, fingerprint, dimensionHash, dimensionId);
                List<ChunkCacheEntry> restored = sessionStore.loadLatestEntries(MAX_RESTORE_ENTRIES, DATA_VERSION);
                if (epoch == worldEpoch) {
                    for (ChunkCacheEntry entry : restored) {
                        entries.put(entry.key(), entry);
                    }
                    sessionReady = true;
                    scheduleWriteDrain();
                }
            } catch (IOException ignored) {
                sessionReady = false;
            } finally {
                openingSession.set(false);
            }
        });
    }

    private void capture(ClientLevel world, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        long fingerprint = worldFingerprint(world);
        CacheKey key = CacheKey.of(fingerprint, world.dimension(), pos, DATA_VERSION);
        long revision = invalidator.revision(pos);
        ChunkCacheEntry current = entries.get(key);
        if (current != null && current.isCurrent(revision)) return;
        Long queuedRevision = queuedRevisions.put(key, revision);
        if (queuedRevision != null && queuedRevision.longValue() == revision) return;

        ChunkSnapshot snapshot = snapshots.acquire();
        if (snapshot == null) {
            queuedRevisions.remove(key, revision);
            return; // Preserve frame pacing: cache work is deliberately lossy under pressure.
        }
        int minY = world.getMinY();
        snapshot.reset(key, revision, minY);
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int index = (z << 4) | x;
                int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                snapshot.heights()[index] = (short) Math.max(0, height - minY);
                mutablePos.set(pos.getMinBlockX() + x, height - 1, pos.getMinBlockZ() + z);
                // Read from the unloading chunk directly; world lookup is unnecessary and unsafe after unload.
                BlockState surface = chunk.getBlockState(mutablePos);
                snapshot.colors()[index] = rgb565(surface.getMapColor(world, mutablePos).col);
                if (surface.canOcclude()) snapshot.solidMask()[index >>> 6] |= 1L << (index & 63);
            }
        }
        long epoch = worldEpoch;
        if (!scheduler.submitBake(() -> {
            try {
                ChunkCacheEntry entry = baker.bake(snapshot);
                if (entry == null || epoch != worldEpoch || !entry.isCurrent(invalidator.revision(pos))) return;
                entries.put(key, entry);
                pendingWrites.offer(entry);
                if (pendingWriteCount.incrementAndGet() >= WRITE_BATCH_RECORDS) scheduleWriteDrain();
            } finally {
                queuedRevisions.remove(key, revision);
                snapshots.release(snapshot);
            }
        })) {
            queuedRevisions.remove(key, revision);
            snapshots.release(snapshot);
        }
    }

    /**
     * Runs on the client tick. At most one unload snapshot is captured every two ticks,
     * and only when the bake worker has no queued work, to preserve frame pacing.
     */
    public void tick(Minecraft client) {
        // Below 35 FPS, cache is strictly opportunistic: it yields most client ticks to
        // Sodium, Entity Culling, and the game engine instead of competing for CPU.
        int captureInterval = client.getFps() < 35 ? 10 : 2;
        if (++captureTick >= captureInterval && scheduler.bakeQueueDepth() == 0) {
            captureTick = 0;
            PendingCapture pending = pendingUnloadCaptures.pollFirst();
            if (pending != null) capture(pending.world(), pending.chunk());
        }
        if (pendingWriteCount.get() != 0 && System.nanoTime() - lastWriteScheduleNanos >= WRITE_BATCH_INTERVAL_NANOS) {
            scheduleWriteDrain();
        }
    }

    private void scheduleWriteDrain() {
        if (!sessionReady || !writeDrainScheduled.compareAndSet(false, true)) return;
        lastWriteScheduleNanos = System.nanoTime();
        if (!scheduler.submitIo(this::drainWrites)) writeDrainScheduled.set(false);
    }

    private void drainWrites() {
        try {
            ChunkCacheEntry entry;
            int count = 0;
            while (sessionReady && (entry = pendingWrites.poll()) != null) {
                pendingWriteCount.decrementAndGet();
                sessionStore.append(entry);
                if (++count >= WRITE_BATCH_RECORDS) break;
            }
        } catch (IOException ignored) {
            sessionReady = false;
        } finally {
            writeDrainScheduled.set(false);
            if (!pendingWrites.isEmpty()) scheduleWriteDrain();
        }
    }

    public void onBlockChanged(BlockPos pos) {
        invalidator.invalidateBlock(pos);
        entries.entrySet().removeIf(entry -> entry.getKey().chunkX() == (pos.getX() >> 4)
                && entry.getKey().chunkZ() == (pos.getZ() >> 4));
    }

    public int snapshotPoolMisses() { return snapshots.misses(); }

    public void resetWorld() {
        worldEpoch++;
        entries.clear();
        queuedRevisions.clear();
        pendingWrites.clear();
        pendingWriteCount.set(0);
        invalidator.clear();
        sessionReady = false;
    }

    @Override
    public void close() {
        scheduler.submitIo(() -> {
            try {
                drainWrites();
                sessionStore.flushAtWorldBoundary();
                sessionStore.close();
            } catch (IOException ignored) {
            }
        });
    }

    private record PendingCapture(ClientLevel world, LevelChunk chunk) {
    }

    private static short rgb565(int rgb) {
        int red = (rgb >>> 19) & 0x1F;
        int green = (rgb >>> 10) & 0x3F;
        int blue = (rgb >>> 3) & 0x1F;
        return (short) ((red << 11) | (green << 5) | blue);
    }

    private static long worldFingerprint(ClientLevel world) {
        Minecraft client = Minecraft.getInstance();
        String identity;
        ServerData remote = client.getCurrentServer();
        if (remote != null) {
            identity = "remote:" + remote.ip;
        } else if (client.getSingleplayerServer() != null) {
            identity = "local:" + client.getSingleplayerServer().getServerDirectory().toAbsolutePath();
        } else {
            identity = "fallback:" + world.dimension();
        }
        long high = Integer.toUnsignedLong(identity.hashCode());
        long low = Integer.toUnsignedLong(world.dimension().toString().hashCode());
        return (high << 32) | low;
    }
}
