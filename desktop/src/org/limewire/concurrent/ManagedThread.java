package org.limewire.concurrent;

import org.limewire.service.ErrorService;

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
