/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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

package com.apple.concurrent;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

class LibDispatchSerialQueue extends AbstractExecutorService {
    private static final int RUNNING = 0;
    private static final int SHUTDOWN = 1;
    //  static final int STOP       = 2; // not supported by GCD
    private static final int TERMINATED = 3;
    private final Object lock = new Object();
    private LibDispatchQueue nativeQueueWrapper;
    private volatile int runState;

    LibDispatchSerialQueue(final long queuePtr) {
        nativeQueueWrapper = new LibDispatchQueue(queuePtr);
    }

    @Override
    public void execute(final Runnable task) {
        if (nativeQueueWrapper == null) return;
        LibDispatchNative.nativeExecuteAsync(nativeQueueWrapper.ptr, task);
    }

    @Override
    public boolean isShutdown() {
        return runState != RUNNING;
    }

    @Override
    public boolean isTerminated() {
        return runState == TERMINATED;
    }

    @Override
    public void shutdown() {
        synchronized (lock) {
            if (runState != RUNNING) return;
            runState = SHUTDOWN;
            execute(() -> {
                synchronized (lock) {
                    runState = TERMINATED;
                    lock.notifyAll(); // for the benefit of awaitTermination()
                }
            });
            nativeQueueWrapper = null;
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown();
        return null;
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        if (runState == TERMINATED) return true;
        final long millis = unit.toMillis(timeout);
        if (millis <= 0) return false;
        synchronized (lock) {
            if (runState == TERMINATED) return true;
            lock.wait(timeout);
            if (runState == TERMINATED) return true;
        }
        return false;
    }
}
