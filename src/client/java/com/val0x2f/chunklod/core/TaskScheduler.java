package com.val0x2f.chunklod.core;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Worker ownership is explicit: no worker receives Minecraft render objects. */
public final class TaskScheduler implements AutoCloseable {
    private static final int QUEUE_CAPACITY = 96;
    private final ThreadPoolExecutor bakeExecutor;
    private final ThreadPoolExecutor ioExecutor;

    public TaskScheduler() {
        // Cache is non-visual in the stable profile: one low-pressure worker is faster for frame pacing than parallel bursts.
        bakeExecutor = executor("Val0x2F-Cache", 1);
        ioExecutor = executor("Val0x2F-IO", 1);
    }

    private static ThreadPoolExecutor executor(String prefix, int threads) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threads, threads, 20L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                new NamedThreadFactory(prefix),
                new ThreadPoolExecutor.DiscardPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    public boolean submitBake(Runnable task) {
        return submit(bakeExecutor, task);
    }

    public boolean submitIo(Runnable task) {
        return submit(ioExecutor, task);
    }

    private static boolean submit(ThreadPoolExecutor executor, Runnable task) {
        if (executor.isShutdown()) return false;
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException ignored) {
            return false;
        }
    }

    public int bakeQueueDepth() { return bakeExecutor.getQueue().size(); }
    public int ioQueueDepth() { return ioExecutor.getQueue().size(); }

    /** Allows already-queued persistence work to finish during world/client shutdown. */
    public void closeGracefully(long timeoutMillis) {
        bakeExecutor.shutdown();
        ioExecutor.shutdown();
        try {
            ioExecutor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS);
            bakeExecutor.awaitTermination(Math.max(1L, timeoutMillis / 2L), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            if (!ioExecutor.isTerminated()) ioExecutor.shutdownNow();
            if (!bakeExecutor.isTerminated()) bakeExecutor.shutdownNow();
        }
    }

    @Override
    public void close() {
        closeGracefully(750L);
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger sequence = new AtomicInteger();

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + '-' + sequence.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((unused, throwable) ->
                    System.err.println("Val0x2F worker failure: " + throwable.getMessage()));
            return thread;
        }
    }
}
