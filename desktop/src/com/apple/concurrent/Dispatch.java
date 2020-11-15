/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
        checkSecurity();
        if (!LibDispatchNative.nativeIsDispatchSupported()) return null;
        return instance;
    }

    private static void checkSecurity() {
        final SecurityManager security = System.getSecurityManager();
        if (security != null) security.checkPermission(new RuntimePermission("canInvokeInSystemThreadGroup"));
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
