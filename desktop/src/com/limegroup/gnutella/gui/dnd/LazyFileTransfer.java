package com.limegroup.gnutella.gui.dnd;


/**
 * An interface representing a file that can be transfered
 * lazily -- not retrieving the actual file until
 * necessary.
 */
public interface LazyFileTransfer {
    
    /**
     * Retrieve an object which can construct the file when necessary.
     */
    public FileTransfer getFileTransfer();
}