package com.limegroup.gnutella.gui;

/**
 * This interface outlines the functionality of a class that should be explicitly
 * notified prior to exiting the virtual machine.
 */
public interface FinalizeListener {
    
    /**
     * Perform any necessary operations necessary prior to shutdown.
     */
    void doFinalize();
}
