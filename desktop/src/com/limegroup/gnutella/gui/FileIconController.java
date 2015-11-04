package com.limegroup.gnutella.gui;

import java.io.File;

import javax.swing.Icon;

public interface FileIconController {
    /**
     * Retrieves the icon for a given file.
     * 
     * This call may take a short while to complete if disk access
     * is required.
     * 
     * @param f
     * @return
     */
    public Icon getIconForFile(File f);
    
    /**
     * Retrieves the icon for a given extension.
     * 
     * This call may take a short while to complete if disk access
     * is required.
     * 
     * @param ext
     * @return
     */
    public Icon getIconForExtension(String ext);
    
    /**
     * Returns true if the controller thinks it can return the icon
     * for the given file without any waiting.
     * 
     * @param f
     * @return
     */
    public boolean isIconForFileAvailable(File f);
    
    /**
     * Determines if this FileIconController is valid.
     */
    public boolean isValid();
}