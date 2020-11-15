/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2007-2020, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.concurrent.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Creates {@link ManagedThread} daemon threads and executes {@link Runnable
 * Runnables} on threads from a thread pool. Since the created threads are of
 * type <code> ManagedThread</code>, uncaught errors are reported to ErrorService.
 */
public class ThreadExecutor {
    /**
     * The factory threads are created from.
     */
    private static final ThreadFactory FACTORY =
            ExecutorsHelper.daemonThreadFactory("ThreadExecutor.FACTORY");
    /**
     * The thread pool to use when running threads.
     */
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
        THREAD_POOL.execute(() -> {
            try {
                Thread.currentThread().setName(name);
                runner.run();
            } catch (Throwable t) {
                // Forward throwables to the handler,
                // and reset the name back to idle.
                Thread.currentThread().
                        getUncaughtExceptionHandler().
                        uncaughtException(Thread.currentThread(), t);
            } finally {
                Thread.currentThread().setName("IdleThread");
            }
        });
    }
}
