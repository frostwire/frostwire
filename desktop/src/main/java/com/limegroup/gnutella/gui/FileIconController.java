package com.limegroup.gnutella.gui;

import javax.swing.*;
import java.io.File;

interface FileIconController {
    /**
     * Retrieves the icon for a given file.
     * <p>
     * This call may take a short while to complete if disk access
     * is required.
     *
     * @param f
     * @return
     */
    Icon getIconForFile(File f);

    /**
     * Retrieves the icon for a given extension.
     * <p>
     * This call may take a short while to complete if disk access
     * is required.
     *
     * @param ext
     * @return
     */
    Icon getIconForExtension(String ext);

    /**
     * Returns true if the controller thinks it can return the icon
     * for the given file without any waiting.
     *
     * @param f
     * @return
     */
    boolean isIconForFileAvailable(File f);

    /**
     * Determines if this FileIconController is valid.
     */
    boolean isValid();
}