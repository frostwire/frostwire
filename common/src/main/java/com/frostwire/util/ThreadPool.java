/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class is not final, but it's not meant to be inherited but
 * only in very specific situations.
 *
 * @author gubatron
 * @author aldenml
 */
public class ThreadPool extends ThreadPoolExecutor {
    private static boolean DEBUG_MODE_ON = false;
    private static final long THREAD_STACK_SIZE = 1024 * 4;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String name;
    private static Logger LOG = Logger.getLogger(ThreadPool.class);

    // ThreadLocal StringBuilder pool to avoid per-task string allocations
    private final ThreadLocal<StringBuilder> nameBuilder = ThreadLocal.withInitial(() -> new StringBuilder(64));

    public ThreadPool(String name, int maximumPoolSize, BlockingQueue<Runnable> workQueue, boolean daemon) {
        super(maximumPoolSize, maximumPoolSize, 1L, TimeUnit.SECONDS, workQueue, new PoolThreadFactory(daemon));
        this.name = name;
    }

    public ThreadPool(String name, int corePoolSize, int maximumPoolSize, long keepAliveTimeInSeconds, BlockingQueue<Runnable> workQueue, boolean daemon) {
        super(corePoolSize, maximumPoolSize, keepAliveTimeInSeconds, TimeUnit.SECONDS, workQueue, new PoolThreadFactory(daemon));
        this.name = name;
    }

    public static ExecutorService newThreadPool(String name, int maxThreads, boolean daemon) {
        ThreadPool pool = new ThreadPool(name, maxThreads, new LinkedBlockingQueue<>(), daemon);
        return Executors.unconfigurableExecutorService(pool);
    }

    public static ExecutorService newThreadPool(String name, int maxThreads) {
        return newThreadPool(name, maxThreads, false);
    }

    public static ExecutorService newThreadPool(String name, boolean daemon) {
        ThreadPool pool = new ThreadPool(name, Integer.MAX_VALUE, new LinkedBlockingQueue<>(), daemon);
        return Executors.unconfigurableExecutorService(pool);
    }

    public static ExecutorService newThreadPool(String name) {
        return newThreadPool(name, false);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        // Reuse ThreadLocal StringBuilder to avoid 3-4 string allocations per task
        StringBuilder sb = nameBuilder.get();
        sb.setLength(0); // reset for reuse
        sb.append(name).append("-thread-").append(threadNumber.getAndIncrement()).append("-");

        if (r instanceof Thread) {
            sb.append(((Thread) r).getName());
        } else {
            sb.append('@').append(r.hashCode());
        }

        t.setName(sb.toString());

        if (DEBUG_MODE_ON && name.startsWith("SearchManager")) {
            LOG.info("ThreadPool(" + name + "): beforeExecute: " + t.getName());
            LOG.info("ThreadPool(" + name + "): pool size: " + getPoolSize());
            LOG.info("ThreadPool(" + name + "): active count: " + getActiveCount());
            LOG.info("ThreadPool(" + name + "): queue size: " + getQueue().size());
            LOG.info("ThreadPool(" + name + "): task count: " + getTaskCount());
            LOG.info("ThreadPool(" + name + "): completed task count: " + getCompletedTaskCount());
            LOG.info("ThreadPool(" + name + "): end of beforeExecute: " + t.getName());
        }
    }

    @Override
    protected void afterExecute(Runnable r, Throwable throwable) {
        Thread t = Thread.currentThread();
        Thread.currentThread().setName(name + "-thread-idle");
    }

    private static final class PoolThreadFactory implements ThreadFactory {
        private final boolean daemon;
        private final ThreadGroup threadGroup = new ThreadGroup("PoolThreadFactoryGroup");

        PoolThreadFactory(boolean daemon) {
            this.daemon = daemon;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(threadGroup, r, "", THREAD_STACK_SIZE);
            t.setDaemon(daemon);
            return t;
        }
    }
}
