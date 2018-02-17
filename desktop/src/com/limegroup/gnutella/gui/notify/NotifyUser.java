package com.limegroup.gnutella.gui.notify;

/**
 * Interface the outlines the basic functionality of any native desktop
 * notification mechanism, such as the "system tray" on Windows.
 */
public interface NotifyUser {
    
    /** Returns true if the NotifyUser implementation supports a system tray icon. */
    public boolean supportsSystemTray();

    /**
     * Adds the notification gui object to the desktop.
     * Returns true if this was succesfully able to add a notification.
     */
    public boolean showTrayIcon();

    /**
     * Removes the notification gui object from the desktop.
     */
    public void hideTrayIcon();

    /** Hides a message. Does nothing if message is not displayed. */
	public void hideMessage(Notification notification);

	/** Invoked when the theme has changed. */
    public void updateUI();

}
