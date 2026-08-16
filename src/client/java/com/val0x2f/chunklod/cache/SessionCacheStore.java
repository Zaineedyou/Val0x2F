package com.val0x2f.chunklod.cache;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32C;

/**
 * One append-only cache container per world/dimension. This class is owned by the
 * single Val0x2F IO thread: no write, fsync, or file open runs on the render thread.
 */
public final class SessionCacheStore implements AutoCloseable {
    private static final int MAGIC = 0x56304C53; // V0LS
    private static final short VERSION = 2;
    private static final int HEADER_BYTES = 32;
    private static final int PAYLOAD_BYTES = 512 + 512 + 32;
    private static final int RECORD_BYTES = 40 + PAYLOAD_BYTES;

    private final PrimitiveLongLongMap latestOffsets = new PrimitiveLongLongMap(1024);
    private final ByteBuffer recordBuffer = ByteBuffer.allocateDirect(RECORD_BYTES).order(ByteOrder.LITTLE_ENDIAN);
    private FileChannel channel;
    private Path file;
    private long worldFingerprint;
    private int dimensionHash;
    private String dimensionId;
    private int dirtyRecords;

    public void open(Path path, long expectedWorldFingerprint, int expectedDimensionHash, String expectedDimensionId) throws IOException {
        close();
        Files.createDirectories(path.getParent());
        file = path;
        worldFingerprint = expectedWorldFingerprint;
        dimensionHash = expectedDimensionHash;
        dimensionId = expectedDimensionId;
        channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        if (channel.size() == 0L) {
            writeHeader();
        } else if (!readHeader()) {
            channel.truncate(0L);
            writeHeader();
        } else {
            scanIndex();
        }
        channel.position(channel.size());
    }

    public void append(ChunkCacheEntry entry) throws IOException {
        if (channel == null) return;
        recordBuffer.clear();
        recordBuffer.putLong(entry.key().packedChunkPos());
        recordBuffer.putInt(entry.key().chunkX());
        recordBuffer.putInt(entry.key().chunkZ());
        recordBuffer.putLong(entry.revision());
        recordBuffer.putInt(entry.minBuildHeight());
        recordBuffer.putInt(PAYLOAD_BYTES);
        int crcPosition = recordBuffer.position();
        recordBuffer.putInt(0);
        recordBuffer.putInt(0); // flags/reserved
        int payloadStart = recordBuffer.position();
        for (short value : entry.heights()) recordBuffer.putShort(value);
        for (short value : entry.colors()) recordBuffer.putShort(value);
        for (long value : entry.solidMask()) recordBuffer.putLong(value);
        CRC32C crc = new CRC32C();
        ByteBuffer payload = recordBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        payload.position(payloadStart).limit(payloadStart + PAYLOAD_BYTES);
        crc.update(payload);
        int checksum = (int) crc.getValue();
        recordBuffer.putInt(crcPosition, checksum);
        entry.crc32c(checksum);
        recordBuffer.flip();
        long offset = channel.position();
        while (recordBuffer.hasRemaining()) channel.write(recordBuffer);
        latestOffsets.put(entry.key().packedChunkPos(), offset);
        dirtyRecords++;
    }

    /** Force only at an explicit world/session flush, never once per chunk. */
    public void flushAtWorldBoundary() throws IOException {
        if (channel != null && dirtyRecords != 0) {
            channel.force(false);
            dirtyRecords = 0;
        }
    }

    public List<ChunkCacheEntry> loadLatestEntries(int maxEntries, int dataVersion) throws IOException {
        List<ChunkCacheEntry> loaded = new ArrayList<>(Math.min(maxEntries, latestOffsets.size()));
        latestOffsets.forEach((packedChunk, offset) -> {
            if (loaded.size() >= maxEntries) return;
            try {
                ChunkCacheEntry entry = readEntry(offset, dataVersion);
                if (entry != null) loaded.add(entry);
            } catch (IOException ignored) {
                // Corrupt records are ignored; append-only tail keeps earlier valid data usable.
            }
        });
        return loaded;
    }

    private ChunkCacheEntry readEntry(long offset, int dataVersion) throws IOException {
        recordBuffer.clear();
        if (channel.read(recordBuffer, offset) != RECORD_BYTES) return null;
        recordBuffer.flip();
        long packed = recordBuffer.getLong();
        int chunkX = recordBuffer.getInt();
        int chunkZ = recordBuffer.getInt();
        long revision = recordBuffer.getLong();
        int minY = recordBuffer.getInt();
        int payloadBytes = recordBuffer.getInt();
        int expectedCrc = recordBuffer.getInt();
        recordBuffer.getInt();
        if (payloadBytes != PAYLOAD_BYTES || packed != net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ)) return null;
        int payloadStart = recordBuffer.position();
        CRC32C crc = new CRC32C();
        ByteBuffer payload = recordBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        payload.position(payloadStart).limit(payloadStart + PAYLOAD_BYTES);
        crc.update(payload);
        if ((int) crc.getValue() != expectedCrc) return null;
        CacheKey key = new CacheKey(worldFingerprint, dimensionId, chunkX, chunkZ, dataVersion);
        ChunkCacheEntry entry = new ChunkCacheEntry();
        entry.reset(key, revision, minY);
        for (int i = 0; i < ChunkCacheEntry.COLUMN_COUNT; i++) entry.heights()[i] = recordBuffer.getShort();
        for (int i = 0; i < ChunkCacheEntry.COLUMN_COUNT; i++) entry.colors()[i] = recordBuffer.getShort();
        for (int i = 0; i < entry.solidMask().length; i++) entry.solidMask()[i] = recordBuffer.getLong();
        entry.crc32c(expectedCrc);
        return entry;
    }

    private boolean readHeader() throws IOException {
        if (channel.size() < HEADER_BYTES) return false;
        ByteBuffer header = ByteBuffer.allocateDirect(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        if (channel.read(header, 0L) != HEADER_BYTES) return false;
        header.flip();
        return header.getInt() == MAGIC && header.getShort() == VERSION && header.getShort() == HEADER_BYTES
                && header.getLong() == worldFingerprint && header.getInt() == dimensionHash;
    }

    private void writeHeader() throws IOException {
        ByteBuffer header = ByteBuffer.allocateDirect(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(MAGIC).putShort(VERSION).putShort((short) HEADER_BYTES).putLong(worldFingerprint).putInt(dimensionHash);
        while (header.position() < HEADER_BYTES) header.put((byte) 0);
        header.flip();
        while (header.hasRemaining()) channel.write(header);
    }

    private void scanIndex() throws IOException {
        latestOffsets.clear();
        long offset = HEADER_BYTES;
        long size = channel.size();
        while (offset + RECORD_BYTES <= size) {
            recordBuffer.clear();
            if (channel.read(recordBuffer, offset) != RECORD_BYTES) break;
            recordBuffer.flip();
            long packed = recordBuffer.getLong();
            int chunkX = recordBuffer.getInt();
            int chunkZ = recordBuffer.getInt();
            recordBuffer.position(28);
            int payloadBytes = recordBuffer.getInt();
            if (packed != net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ) || payloadBytes != PAYLOAD_BYTES) break;
            latestOffsets.put(packed, offset);
            offset += RECORD_BYTES;
        }
        if (offset != size) channel.truncate(offset);
    }

    @Override
    public void close() throws IOException {
        if (channel != null) {
            flushAtWorldBoundary();
            channel.close();
            channel = null;
        }
        latestOffsets.clear();
        dirtyRecords = 0;
    }
}
