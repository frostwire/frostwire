package com.limegroup.gnutella.gui.notify;

import com.limegroup.gnutella.gui.GUIMediator;

import javax.swing.*;
import java.awt.*;

/**
 * This class handles user notifications for platform that do not support JDIC.
 * It currently displays notifications only.
 */
public final class BasicNotifier implements NotifyUser {

	private NotificationWindow notificationWindow;
	
	BasicNotifier() {
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

	public void hideMessage(Notification notification) {
		notificationWindow.removeNotification(notification);
	}

    public void updateUI() {
        SwingUtilities.updateComponentTreeUI(notificationWindow);        
    }
}
