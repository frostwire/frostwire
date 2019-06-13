package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.gui.bugs.BugManager;
import org.limewire.service.ErrorCallback;

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
        // ThreadDeath must NOT be caught, or a thread will be left zombied     
        if (problem instanceof ThreadDeath)
            throw (ThreadDeath) problem;
        else {
            Runnable doWorkRunnable = new Error(problem, msg);
            GUIMediator.safeInvokeLater(doWorkRunnable);
        }
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
