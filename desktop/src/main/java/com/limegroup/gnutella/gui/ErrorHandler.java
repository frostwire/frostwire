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
