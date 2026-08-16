package com.val0x2f.chunklod.memory;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/** Explicit accounting around direct buffers; it does not pretend direct memory is free. */
public final class OffHeapCacheStorage implements AutoCloseable {
    private final long maxBytes;
    private final AtomicLong allocated = new AtomicLong();

    public OffHeapCacheStorage(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public ByteBuffer allocate(int bytes) {
        long next = allocated.addAndGet(bytes);
        if (next > maxBytes) {
            allocated.addAndGet(-bytes);
            return null;
        }
        return ByteBuffer.allocateDirect(bytes);
    }

    public void release(ByteBuffer buffer) {
        if (buffer != null) allocated.addAndGet(-buffer.capacity());
    }

    public long allocatedBytes() {
        return allocated.get();
    }

    @Override
    public void close() {
        allocated.set(0L);
    }
}
