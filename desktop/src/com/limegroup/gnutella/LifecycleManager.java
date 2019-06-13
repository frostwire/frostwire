package com.limegroup.gnutella;

public interface LifecycleManager {
    boolean isLoaded();

    boolean isStarted();

    /**
     * Phase 1 of the startup process -- wires listeners together.
     */
    void installListeners();

    /**
     * Phase 2 of the startup process -- loads any tasks that can be run in the background.
     */
    void loadBackgroundTasks();

    /**
     * The core of the startup process, initializes all classes.
     */
    void start();

    /**
     * Shuts down anything that requires shutdown.
     * <p>
     * TODO: Make all of these things Shutdown Items.
     */
    void shutdown();

    /**
     * Shuts down & executes something after shutdown completes.
     */
    void shutdown(String toExecute);

    /**
     * Adds something that requires shutting down.
     * <p>
     * TODO: Make this take a 'Service' or some such that
     * has a shutdown method, and run the method in its
     * own thread.
     */
    void addShutdownItem(Thread t);
}