/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.limewire.concurrent;

import java.util.concurrent.*;

/**
 * A factory that builds {@link ExecutorService}, {@link ThreadFactory}
 * and {@link ScheduledExecutorService} objects via static methods.
 * <p>
 * <code>ExecutorsHelper</code> differs from {@link Executors} since
 * <code>ExecutorsHelper</code> returns the thread factory for daemon threads
 * and creates non-fixed size thread pools. Additionally,
 * <code>ExecutorsHelper</code> guarantees the returned <code>ExecutorService
 * </code> will allow worker threads to expire. On the other hand,
 * <code>Executors</code> create <code>ExecutorService</code>s whose core-pool
 * of worker threads never die.
 */
public class ExecutorsHelper {
    /**
     * Creates a new "ProcessingQueue" using
     * {@link #daemonThreadFactory(String)} as thread factory.
     * <p>
     * See {@link #newProcessingQueue(ThreadFactory)}.
     *
     * @param name the name of the processing thread that is created
     *             with the daemon thread factory.
     */
    public static ExecutorService newProcessingQueue(String name) {
        return newProcessingQueue(daemonThreadFactory(name));
    }

    /**
     * Creates a new "ProcessingQueue".
     * <p>
     * A <code>ProcessingQueue</code> is an <code>ExecutorService</code> that
     * will process all Runnables/Callables sequentially, creating one thread
     * for processing when it needs it.
     * <p>
     * See {@link #newSingleThreadExecutor(ThreadFactory)}.
     *
     * @param factory the factory used for creating a new processing thread
     */
    public static ExecutorService newProcessingQueue(ThreadFactory factory) {
        return Executors.unconfigurableExecutorService(newSingleThreadExecutor(factory));
    }

    /**
     * A ProcessingQueue Executor is an <code>ExecutorService</code> that
     * processes all Runnables/Callables sequentially, creating one thread
     * for processing when it needs it.
     * <p>
     * This kind of Executor is ideal for long-lived tasks
     * that require processing rarely.
     * <p>
     * If there are no tasks the thread will be terminated after a timeout of
     * 5 seconds and a new one will be created when necessary.
     *
     * @param factory the factory used for creating a new processing thread
     */
    private static java.util.concurrent.ThreadPoolExecutor newSingleThreadExecutor(ThreadFactory factory) {
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(1, 1,
                5L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                factory);
        tpe.allowCoreThreadTimeOut(true);
        return tpe;
    }

    /**
     * Creates a new ThreadPool.
     * The pool is tuned to begin with zero threads and maintain zero threads,
     * although an unlimited number of threads will be created to handle
     * the tasks.  Each thread is set to linger for a short period of time,
     * ready to handle new tasks, before the thread terminates.
     *
     * @param factory the factory used for creating a new processing thread
     */
    static ExecutorService newThreadPool(@SuppressWarnings("SameParameterValue") ThreadFactory factory) {
        return Executors.unconfigurableExecutorService(
                new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                        5L, TimeUnit.SECONDS,
                        new SynchronousQueue<>(),
                        factory));
    }

    /**
     * Creates a new ThreadPool with the maximum number of available threads.
     * Items added while no threads are available to process them will wait
     * until an executing item is finished and then be processed.
     */
    public static ExecutorService newFixedSizeThreadPool(int size, String name) {
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(size, size,
                5L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                daemonThreadFactory(name));
        tpe.allowCoreThreadTimeOut(true);
        return Executors.unconfigurableExecutorService(tpe);
    }

    /**
     * Returns the a thread factory of daemon threads, using the given name.
     */
    static ThreadFactory daemonThreadFactory(String name) {
        return new DefaultThreadFactory(name, true);
    }

    /**
     * A thread factory that can create threads with a name.
     */
    private static class DefaultThreadFactory implements ThreadFactory {
        /**
         * The name created threads will use.
         */
        private final String name;
        /**
         * Whether or not the created thread is a daemon thread.
         */
        private final boolean daemon;

        /**
         * Constructs a thread factory that will created named threads.
         */
        DefaultThreadFactory(String name, boolean daemon) {
            this.name = name;
            this.daemon = daemon;
        }

        public Thread newThread(Runnable r) {
            Thread t = new ManagedThread(r, name);
            if (daemon)
                t.setDaemon(true);
            return t;
        }
    }
}
