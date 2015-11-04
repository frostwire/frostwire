package com.limegroup.gnutella.gui;

import org.apache.commons.io.FilenameUtils;
import org.limewire.util.OSUtils;

import javax.swing.*;
import java.io.File;

/**
 * Manages finding native icons for files and file types.
 */
public class IconManager {
    /**
     * The sole instance of this IconManager class.
     */
    private static volatile IconManager INSTANCE;
    
    /** The current FileIconController. */
    private FileIconController fileController;
    
    /** The current ButtonIconController. */
    private ButtonIconController buttonController;

    /**
     * Returns the sole instance of this IconManager class.
     */
    public static IconManager instance() {
        if (INSTANCE == null) {
            synchronized(IconManager.class) {
                if (INSTANCE == null)
                    INSTANCE = new IconManager();
            }
        }
        return INSTANCE;
    }
    
    /**
     * Constructs a new IconManager.
     */
    private IconManager() {
        buttonController = new ButtonIconController();
        
        // Always begin with the basic controller, whose
        // contruction can never block.
        fileController = new BasicFileIconController();
        
        // Then, in a new thread, try to change it to a controller
        // that can block.
        if(OSUtils.isMacOSX() || OSUtils.isWindows()) {
        	SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					FileIconController newController = new NativeFileIconController();
                    if(newController.isValid()) {
                    	fileController = newController;
                    }
				}
			});
        }
    }
    
    /**
     * Returns the icon associated with this file.
     * If the file does not exist, or no icon can be found, returns
     * the icon associated with the extension.
     */
    public Icon getIconForFile(File f) {
        validate();

        String ext = f != null ? FilenameUtils.getExtension(f.getName()) : null;

        if (f != null && ext != null && ext.toLowerCase().endsWith("torrent")) {
            return GUIMediator.getThemeImage("frosthires");
        }
        
        if (f != null && ext != null && ext.toLowerCase().endsWith("youtube")) {
            return GUIMediator.getThemeImage("youtube");
        }

        return fileController.getIconForFile(f);
    }
    
    /**
     * Returns the icon associated with the extension.
     * TODO: Implement better.
     */
    public Icon getIconForExtension(String ext) {
        validate();
        
        if (ext != null && ext.toLowerCase().endsWith("torrent")) {
            return GUIMediator.getThemeImage("frosthires");
        }
        
        if (ext != null && ext.toLowerCase().endsWith("youtube")) {
            return GUIMediator.getThemeImage("youtube");
        }
        
        return fileController.getIconForExtension(ext);
    }
    
    /** Returns true if the icon can be returned immediately. */
    public boolean isIconForFileAvailable(File f) {
        validate();
        return fileController.isIconForFileAvailable(f);
    }
    
    /**
     * Erases the button's history.
     */
    public void wipeButtonIconCache() {
        buttonController.wipeButtonIconCache();
    }
    
    /**
     * Retrieves the icon for the specified button name.
     */
    public Icon getIconForButton(String buttonName) {
        return buttonController.getIconForButton(buttonName);
    }
    
    /**
     * Retrieves the rollover image for the specified button name.
     */
    public Icon getRolloverIconForButton(String buttonName) {
        return buttonController.getRolloverIconForButton(buttonName);
    }
    
    /**
     * Reverts the IconController to a basic controller if at any point
     * in time the controller becomes invalid.
     * 
     * Returns true if the current controller is already valid.
     */
    private void validate() {
        if(!fileController.isValid())
            fileController = new BasicFileIconController();
    }

	public Icon getSmallIconForButton(String buttonName) {
		return buttonController.getSmallIconForButton(buttonName);
	}
}
