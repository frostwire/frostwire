/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
                // Forward throwable to the handler,
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
