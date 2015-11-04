package com.limegroup.gnutella.gui.notify;

/**
 * This class outlines the basic functionality required in any class that
 * receives callback from native user notification objects.
 *
 * @author Adam Fisk
 */
//2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
public interface NotifyCallback {

    /**
     * restore the application to its normal state.
     */
    public void restoreApplication();

    /**
     * exit the application.
     */
    public void exitApplication();

    /**
     * show a menu of options to the user.
     */
    public void showMenu(int x, int y);

    /**
     * shows the about window with more information about the
     * application.
     */
    public void showAboutWindow();
}
