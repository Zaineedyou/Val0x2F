package com.val0x2f.chunklod.cache;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.zip.CRC32C;

/** Fixed-record cache format optimized for a 16x16 height/color column grid. */
public final class CacheFileFormat {
    public static final int MAGIC = 0x56304C46; // V0LF
    public static final short FORMAT_VERSION = 1;
    private static final int HEADER_BYTES = 64;
    private static final int PAYLOAD_BYTES = 512 + 512 + 32;

    public void write(ChunkCacheEntry entry, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        ByteBuffer payload = ByteBuffer.allocate(PAYLOAD_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (short height : entry.heights()) payload.putShort(height);
        for (short color : entry.colors()) payload.putShort(color);
        for (long mask : entry.solidMask()) payload.putLong(mask);
        payload.flip();

        CRC32C crc = new CRC32C();
        crc.update(payload.duplicate());
        entry.crc32c((int) crc.getValue());

        ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(MAGIC).putShort(FORMAT_VERSION).putShort((short) HEADER_BYTES);
        header.putInt(entry.key().dataVersion()).putLong(entry.key().worldFingerprint());
        header.putInt(entry.key().dimensionId().hashCode()).putInt(entry.key().chunkX()).putInt(entry.key().chunkZ());
        header.putInt(entry.minBuildHeight()).putLong(entry.revision()).putInt(PAYLOAD_BYTES).putInt(entry.crc32c());
        while (header.position() < HEADER_BYTES) header.put((byte) 0);
        header.flip();

        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.write(header);
            channel.write(payload);
            channel.force(false);
        }
        try {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public boolean read(Path source, ChunkCacheEntry target, CacheKey expectedKey, long expectedRevision) throws IOException {
        if (!Files.isRegularFile(source) || Files.size(source) != HEADER_BYTES + PAYLOAD_BYTES) return false;
        try (FileChannel channel = FileChannel.open(source, StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            if (channel.read(header) != HEADER_BYTES) return false;
            header.flip();
            if (header.getInt() != MAGIC || header.getShort() != FORMAT_VERSION || header.getShort() != HEADER_BYTES) return false;
            int dataVersion = header.getInt();
            long world = header.getLong();
            int dimensionHash = header.getInt();
            int chunkX = header.getInt();
            int chunkZ = header.getInt();
            int minBuildHeight = header.getInt();
            long revision = header.getLong();
            int payloadBytes = header.getInt();
            int expectedCrc = header.getInt();
            if (dataVersion != expectedKey.dataVersion() || world != expectedKey.worldFingerprint()
                    || dimensionHash != expectedKey.dimensionId().hashCode() || chunkX != expectedKey.chunkX()
                    || chunkZ != expectedKey.chunkZ() || revision != expectedRevision || payloadBytes != PAYLOAD_BYTES) return false;

            ByteBuffer payload = ByteBuffer.allocate(PAYLOAD_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            if (channel.read(payload) != PAYLOAD_BYTES) return false;
            payload.flip();
            CRC32C crc = new CRC32C();
            crc.update(payload.duplicate());
            if ((int) crc.getValue() != expectedCrc) return false;

            target.reset(expectedKey, revision, minBuildHeight);
            for (int i = 0; i < ChunkCacheEntry.COLUMN_COUNT; i++) target.heights()[i] = payload.getShort();
            for (int i = 0; i < ChunkCacheEntry.COLUMN_COUNT; i++) target.colors()[i] = payload.getShort();
            for (int i = 0; i < target.solidMask().length; i++) target.solidMask()[i] = payload.getLong();
            target.crc32c(expectedCrc);
            return true;
        }
    }
}
