/*
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

import com.frostwire.service.ErrorService;

/**
 * A <code>Thread</code> that sets the <code>UncaughtExceptionHandler</code> to
 * forward uncaught exceptions to {@link ErrorService}.
 */
class ManagedThread extends Thread {
    private static final UncaughtExceptionHandler HANDLER =
            new ErrorServiceHandler();

    /**
     * Constructs a ManagedThread with the specified target and name.
     */
    ManagedThread(Runnable r, String name) {
        super(r, name);
        setPriority(Thread.NORM_PRIORITY);
        setUncaughtExceptionHandler(HANDLER);
    }

    private static class ErrorServiceHandler implements UncaughtExceptionHandler {
        public void uncaughtException(Thread t, Throwable e) {
            ErrorService.error(e, "Uncaught thread error: " + t.getName());
        }
    }
}
