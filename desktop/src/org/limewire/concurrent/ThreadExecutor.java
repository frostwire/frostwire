package org.limewire.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import org.limewire.service.ErrorService;

/**
 * Creates {@link ManagedThread} daemon threads and executes {@link Runnable 
 * Runnables} on threads from a thread pool. Since the created threads are of 
 * type <code> ManagedThread</code>, uncaught errors are reported to {@link 
 * ErrorService}.
 */

public class ThreadExecutor {
    
    /** The factory threads are created from. */
    private static final ThreadFactory FACTORY =
        ExecutorsHelper.daemonThreadFactory("IdleThread");
    
    /** The thread pool to use when running threads. */
    private static final ExecutorService THREAD_POOL =
        ExecutorsHelper.newThreadPool(FACTORY);
    
    /**
     * A static helper Method to create Threads
     */
    public static Thread newManagedThread(Runnable r) {
        return FACTORY.newThread(r);
    }

    /**
     * A static helper Method to create Threads
     */
    public static Thread newManagedThread(Runnable r, String name) {
        Thread thread = newManagedThread(r);
        thread.setName(name);
        return thread;
    }

    /**
     * Adds and runs the given named Runnable on a ThreadPool
     */
    public static void startThread(final Runnable runner, final String name) {
        THREAD_POOL.execute(new Runnable() {
            public void run() {
                try {
                    Thread.currentThread().setName(name);
                    runner.run();
                } catch(Throwable t) {
                    // Forward throwables to the handler,
                    // and reset the name back to idle.
                    Thread.currentThread().
                      getUncaughtExceptionHandler().
                        uncaughtException(Thread.currentThread(), t);
                } finally {
                    Thread.currentThread().setName("IdleThread");
                }
            }
        });
    }

}
