/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.gui.bugs.BugManager;
import com.frostwire.service.ErrorCallback;

/**
 * Forwards error messages to the BugManager on the Swing thread.
 */
public final class ErrorHandler implements ErrorCallback {
    /**
     * Displays the error to the user.
     */
    public void error(Throwable problem) {
        error(problem, null);
    }

    /**
     * Displays the error to the user with a specific message.
     */
    public void error(Throwable problem, String msg) {
        Runnable doWorkRunnable = new Error(problem, msg);
        GUIMediator.safeInvokeLater(doWorkRunnable);
    }

    /**
     * This class handles error callbacks.
     */
    private static class Error implements Runnable {
        /**
         * The stack trace of the error.
         */
        private final Throwable PROBLEM;
        /**
         * An extra message associated with the error.
         */
        private final String MESSAGE;
        /**
         * The name of the thread the error occurred in.
         */
        private final String CURRENT_THREAD_NAME;

        private Error(Throwable problem, String msg) {
            PROBLEM = problem;
            MESSAGE = msg;
            CURRENT_THREAD_NAME = Thread.currentThread().getName();
        }

        public void run() {
            try {
                GUIMediator.closeStartupDialogs();
                BugManager.instance().handleBug(PROBLEM, CURRENT_THREAD_NAME, MESSAGE);
            } catch (Throwable ignored) {
            }
        }
    }
}
