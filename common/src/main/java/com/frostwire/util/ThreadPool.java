/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.util;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author gubatron
 * @author aldenml
 */
public final class ThreadPool extends ThreadPoolExecutor {

    private final AtomicInteger threadNumber = new AtomicInteger(1);

    private final String name;

    public ThreadPool(String name, int maximumPoolSize, BlockingQueue<Runnable> workQueue, boolean daemon) {
        super(0, maximumPoolSize, 1L, TimeUnit.SECONDS, workQueue, new PoolThreadFactory(daemon));
        this.name = name;
    }

    public ThreadPool(String name, int corePoolSize, int maximumPoolSize, long keepAliveTime, BlockingQueue<Runnable> workQueue, boolean daemon) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue, new PoolThreadFactory(daemon));
        this.name = name;
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        String threadName = null;
        if (r instanceof Thread) {
            Thread thread = (Thread) r;
            threadName = thread.getName();
        }

        t.setName(name + "-thread-" + threadNumber.getAndIncrement() + "-" + (threadName != null ? threadName : "@" + r.hashCode()));
    }

    public static ExecutorService newThreadPool(String name, int maxThreads, boolean daemon) {
        ThreadPool pool = new ThreadPool(name, maxThreads, new LinkedBlockingQueue<Runnable>(), daemon);
        return Executors.unconfigurableExecutorService(pool);
    }

    public static ExecutorService newThreadPool(String name, int maxThreads) {
        return newThreadPool(name, maxThreads, false);
    }

    public static ExecutorService newThreadPool(String name, boolean daemon) {
        ThreadPool pool = new ThreadPool(name, Integer.MAX_VALUE, new SynchronousQueue<Runnable>(), daemon);
        return Executors.unconfigurableExecutorService(pool);
    }

    public static ExecutorService newThreadPool(String name) {
        return newThreadPool(name, false);
    }

    private static final class PoolThreadFactory implements ThreadFactory {

        private final boolean daemon;

        public PoolThreadFactory(boolean daemon) {
            this.daemon = daemon;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);

            t.setDaemon(daemon);

            return t;
        }
    }
}
