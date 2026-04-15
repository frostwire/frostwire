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
     */
    void shutdown();

    /**
     * Shuts down and executes something after shutdown completes.
     */
    void shutdown(String toExecute);

    /**
     * Adds something that requires shutting down.
     */
    void addShutdownItem(Thread t);
}