package com.limegroup.gnutella.gui.notify;

import org.limewire.util.OSUtils;

import com.limegroup.gnutella.settings.UISettings;

import java.io.IOException;

/**
 * This class acts as a proxy for a platform-specific user notification class.
 */
 //2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
public class NotifyUserProxy implements NotifyUser {
    /** Singleton */
    private static final NotifyUserProxy INSTANCE = new NotifyUserProxy();
    

    /** The NotifyUser object that this class is serving as a proxy for. */
    private NotifyUser _notifier;

    /** Flag for whether or not the application is currently in the tray. */
    private boolean _inTray;
    
    /**
     * Instance accessor method for the single object of this class,
     * following the singleton pattern.
     *
     * @return a NotifyUserProxy instance for this object
     */
    public static NotifyUserProxy instance() {
        return INSTANCE;
    }

    /**
     * Instantiates the appropriate NotifyUser object depending on the
     * platform.  This class serves as a "proxy" for the object constructed.
     */
    private NotifyUserProxy() {
        if (OSUtils.supportsTray()) {
        	_notifier = new TrayNotifier();
        	// If add notifications failed, we're screwed.
            if(!showTrayIcon())
                _notifier = new BasicNotifier();
        } else {
            _notifier = new BasicNotifier();
        }        
    }
    
    public boolean supportsSystemTray() {
        return _notifier.supportsSystemTray();
    }

    public boolean showTrayIcon() {
        if (_inTray)
            return true;
        boolean notify = _notifier.showTrayIcon();
        _inTray = true;
        return notify;
    }

    public void hideTrayIcon() {
        if (!_inTray)
            return;
        _notifier.hideTrayIcon();
        _inTray = false;
    }

    public void updateUI() {
        _notifier.updateUI();
    }
    
    public void hideMessage(Notification notification) {
        _notifier.hideMessage(notification);
    }

    public void showMessage(Notification notification) {
    	
        if (notification == null || !UISettings.SHOW_NOTIFICATIONS.getValue()) {
            return;
        }

        if (OSUtils.isMacOSX()) {
            ProcessBuilder pb = new ProcessBuilder("osascript","-e","'display notification \"" + notification.getMessage() + "\"");
            try {
                pb.start();
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return;
        }
        
        //_notifier.showMessage(notification);
        
    }

}
