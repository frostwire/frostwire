package com.limegroup.gnutella.gui;

import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.uxstats.UXStats;
import com.limegroup.gnutella.gui.bugs.BugManager;
import com.limegroup.gnutella.gui.notify.NotifyUserProxy;
import com.limegroup.gnutella.gui.search.SearchMediator;

/**
 * This class provides the "shutdown" method that should be
 * the only method for closing the application for production
 * (non_testing) code.  This method makes sure that all of
 * the necessary classes are notified that the virtual machine
 * is about to be exited.
 */
final class Finalizer {

    /** Stores whether a shutdown operation has been
     * initiated.
     */
    private static boolean _shutdownImminent;

    /** Indicates whether file uploads are complete.
     */
    private static boolean _uploadsComplete;

    /** Indicates whether file downloads are complete.
     */
    private static boolean _downloadsComplete;

    /**
     * An update command to execute upon shutdown, if any.
     */
    private static volatile String _updateCommand;

    /**
     * Suppress the default constructor to ensure that this class can never
     * be constructed.
     */
    private Finalizer() {
    }

    /** Indicates whether the application is waiting to
     * shutdown.
     * @return true if the application is waiting to
     * shutdown, false otherwise
     */
    static boolean isShutdownImminent() {
        return _shutdownImminent;
    }

    /**
     * Exits the virtual machine, making calls to save
     * any necessary settings and to perform any
     * necessary cleanups.
     */
    static void shutdown() {
        UXStats.instance().flush();

        SearchMediator.instance().shutdown();

        MediaPlayer.instance().stop();

        GUIMediator.applyWindowSettings();

        GUIMediator.setAppVisible(false);
        ShutdownWindow window = new ShutdownWindow();
        GUIUtils.centerOnScreen(window);
        window.setVisible(true);

        // remove any user notification icons
        NotifyUserProxy.instance().hideTrayIcon();

        // Do shutdown stuff in another thread.
        // We don't want to lockup the event thread
        // (which this was called on).
        final String toExecute = _updateCommand;
        Thread shutdown = new Thread("Shutdown Thread") {
            public void run() {
                try {
                    sleep(3000);
                    BugManager.instance().shutdown();
                    GuiCoreMediator.getLifecycleManager().shutdown(toExecute);
                    System.exit(0);
                } catch (Throwable t) {
                    t.printStackTrace();
                    System.exit(0);
                }
            }
        };
        shutdown.start();
    }

    static void flagUpdate(String toExecute) {
        _updateCommand = toExecute;
    }

    /** Notifies the <tt>Finalizer</tt> that all
     * downloads have been completed.
     */
    static void setDownloadsComplete() {
        _downloadsComplete = true;
        checkForShutdown();
    }

    /** Notifies the <tt>Finalizer</tt> that all uploads
     * have been completed.
     */
    static void setUploadsComplete() {
        _uploadsComplete = true;
        checkForShutdown();
    }

    /** Attempts to shutdown the application.  This
     * method does nothing if all file transfers are
     * not yet complete.
     */
    private static void checkForShutdown() {
        if (_shutdownImminent && _uploadsComplete && _downloadsComplete) {
            GUIMediator.shutdown();
        }
    }

    /**
     * Adds the specified <tt>Finalizable</tt> instance to the list of
     * classes to notify prior to shutdown.
     * 
     * @param fin the <tt>Finalizable</tt> instance to register
     */
    static void addFinalizeListener(final FinalizeListener fin) {
        Thread t = new Thread("FinalizeItem") {
            public void run() {
                fin.doFinalize();
            }
        };
        GuiCoreMediator.getLifecycleManager().addShutdownItem(t);
    }

}
