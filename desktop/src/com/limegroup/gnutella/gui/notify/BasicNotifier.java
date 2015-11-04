package com.limegroup.gnutella.gui.notify;

import java.awt.Dimension;

import javax.swing.SwingUtilities;

import com.limegroup.gnutella.gui.GUIMediator;

/**
 * This class handles user notifications for platform that do not support JDIC.
 * It currently displays notifications only.
 */
//2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
public final class BasicNotifier implements NotifyUser {   

	private NotificationWindow notificationWindow;
	
	public BasicNotifier() {
		notificationWindow = new NotificationWindow(GUIMediator.getAppFrame());
		notificationWindow.setLocationOffset(new Dimension(1, 1));
		notificationWindow.setTitle("FrostWire");
		notificationWindow.setIcon(GUIMediator.getThemeImage("frosticon.gif"));
	}
	
	public boolean supportsSystemTray() {
	    return false;
	}
	
    public boolean showTrayIcon() { return true; }

    public void hideTrayIcon() {}

    public void showMessage(Notification notification) {
        notificationWindow.addNotification(notification);
        notificationWindow.showWindow();
    }

	public void hideMessage(Notification notification) {
		notificationWindow.removeNotification(notification);
	}

    public void updateUI() {
        SwingUtilities.updateComponentTreeUI(notificationWindow);        
    }

    /**
     * implements the NotifyUser interface.
     * currently does nothing, since we have not implemented
     * a user notification mechanism for non-Windows platforms.
     */
    //public void installNotifyCallback(NotifyCallback callback) {}

}
