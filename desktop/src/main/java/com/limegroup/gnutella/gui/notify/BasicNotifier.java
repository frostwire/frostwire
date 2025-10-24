package com.limegroup.gnutella.gui.notify;

/**
 * This class handles user notifications for platform that do not support JDIC.
 * It currently displays notifications only.
 */
public final class BasicNotifier implements NotifyUser {
    BasicNotifier() {
    }

    @Override
    public boolean supportsSystemTray() {
        return false;
    }

    @Override
    public boolean showTrayIcon() {
        return true;
    }

    @Override
    public void hideTrayIcon() {
    }
}
