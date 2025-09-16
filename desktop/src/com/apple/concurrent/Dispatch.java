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

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Factory for {@link Executor}s and {@link ExecutorService}s backed by
 * libdispatch.
 * <p>
 * Access is controlled through the Dispatch.getInstance() method, because
 * performed tasks occur on threads owned by libdispatch. These threads are
 * not owned by any particular AppContext or have any specific context
 * classloader installed.
 *
 * @since Java for Mac OS X 10.6 Update 2
 */
public final class Dispatch {
    private final static Dispatch instance = new Dispatch();
    private Executor nonBlockingMainQueue = null;
    private Executor blockingMainQueue = null;

    private Dispatch() {
    }

    /**
     * Factory method returns an instance of Dispatch if supported by the
     * underlying operating system, and if the caller's security manager
     * permits "canInvokeInSystemThreadGroup".
     *
     * @return a factory instance of Dispatch, or null if not available
     */
    public static Dispatch getInstance() {
        if (!LibDispatchNative.nativeIsDispatchSupported()) return null;
        return instance;
    }

    /**
     * Returns an {@link Executor} that performs the provided Runnables on the main queue of the process.
     * Runnables submitted to this {@link Executor} will not run until the AWT is started or another native toolkit is running a CFRunLoop or NSRunLoop on the main thread.
     * <p>
     * Submitting a Runnable to this {@link Executor} does not wait for the Runnable to complete.
     *
     * @return an asynchronous {@link Executor} that is backed by the main queue
     */
    public synchronized Executor getNonBlockingMainQueueExecutor() {
        if (nonBlockingMainQueue != null) return nonBlockingMainQueue;
        return nonBlockingMainQueue = new LibDispatchMainQueue.ASync();
    }

    /**
     * Returns an {@link Executor} that performs the provided Runnables on the main queue of the process.
     * Runnables submitted to this {@link Executor} will not run until the AWT is started or another native toolkit is running a CFRunLoop or NSRunLoop on the main thread.
     * <p>
     * Submitting a Runnable to this {@link Executor} will block until the Runnable has completed.
     *
     * @return an {@link Executor} that is backed by the main queue
     */
    public synchronized Executor getBlockingMainQueueExecutor() {
        if (blockingMainQueue != null) return blockingMainQueue;
        return blockingMainQueue = new LibDispatchMainQueue.Sync();
    }
}
