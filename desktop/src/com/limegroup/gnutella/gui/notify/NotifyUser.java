package com.limegroup.gnutella.gui.notify;

/**
 * Interface the outlines the basic functionality of any native desktop
 * notification mechanism, such as the "system tray" on Windows.
 */
interface NotifyUser {
    /**
     * Returns true if the NotifyUser implementation supports a system tray icon.
     */
    boolean supportsSystemTray();

    /**
     * Adds the notification gui object to the desktop.
     * Returns true if this was successfully able to add a notification.
     */
    boolean showTrayIcon();

    /**
     * Removes the notification gui object from the desktop.
     */
    void hideTrayIcon();
}
