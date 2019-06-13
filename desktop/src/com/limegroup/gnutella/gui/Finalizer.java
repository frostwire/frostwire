package com.limegroup.gnutella.gui;

import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.LimeWireCore;
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
    private static final Logger LOG = Logger.getLogger(Finalizer.class);

    /**
     * Suppress the default constructor to ensure that this class can never
     * be constructed.
     */
    private Finalizer() {
    }

    /**
     * Exits the virtual machine, making calls to save
     * any necessary settings and to perform any
     * necessary cleanups.
     */
    static void shutdown() {
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
        Thread shutdown = new Thread("Shutdown Thread") {
            public void run() {
                try {
                    LOG.info("Shutdown thread started");
                    VPNStatusRefresher.getInstance().shutdown();
                    //LOG.info("SearchMediator shutting down...");
                    SearchMediator.instance().shutdown();
                    //LOG.info("MediaPlayer stopping...");
                    MediaPlayer.instance().stop();
                    //LOG.info("BugManager stopping...");
                    BugManager.instance().shutdown();
                    sleep(3000);
                    //LOG.info("Shutting down [updateCommand=" + toExecute + "]");
                    LimeWireCore.instance().getLifecycleManager().shutdown(null);
                    LOG.info("System exit");
                    System.exit(0);
                } catch (Throwable t) {
                    t.printStackTrace();
                    System.exit(0);
                }
            }
        };
        shutdown.start();
    }
}
