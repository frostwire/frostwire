package com.limegroup.gnutella;

public interface LifecycleManager {

    public boolean isLoaded();

    public boolean isStarted();

    /** Phase 1 of the startup process -- wires listeners together. */
    public void installListeners();

    /** Phase 2 of the startup process -- loads any tasks that can be run in the background. */
    public void loadBackgroundTasks();

    /** The core of the startup process, initializes all classes. */
    public void start();

    /**
     * Shuts down anything that requires shutdown.
     *
     * TODO: Make all of these things Shutdown Items.
     */
    public void shutdown();

    /** Shuts down & executes something after shutdown completes. */
    public void shutdown(String toExecute);

    /** Gets the time this finished starting. */
    public long getStartFinishedTime();

    /**
     * Adds something that requires shutting down.
     *
     * TODO: Make this take a 'Service' or somesuch that
     *       has a shutdown method, and run the method in its
     *       own thread.
     */
    public boolean addShutdownItem(Thread t);

}