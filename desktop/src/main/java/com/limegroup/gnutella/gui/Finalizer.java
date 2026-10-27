package com.limegroup.gnutella.gui;

import com.frostwire.util.Logger;
import com.limegroup.gnutella.LimeWireCore;
import com.limegroup.gnutella.gui.notify.NotifyUserProxy;
import com.limegroup.gnutella.gui.search.SearchMediator;

/**
 * This class provides the "shutdown" method that should be the only method for closing the
 * application for production (non_testing) code. This method makes sure that all of the necessary
 * classes are notified that the virtual machine is about to be exited.
 */
final class Finalizer {
  private static final Logger LOG = Logger.getLogger(Finalizer.class);

  /** Suppress the default constructor to ensure that this class can never be constructed. */
  private Finalizer() {}

  /**
   * Exits the virtual machine, making calls to save any necessary settings and to perform any
   * necessary cleanups.
   */
  static void shutdown() {
    GUIMediator.applyWindowSettings();
    GUIMediator.setAppVisible(false);
    ShutdownWindow window = new ShutdownWindow();
    GUIUtils.centerOnScreen(window);
    window.setVisible(true);
    // Do shutdown stuff in another thread.
    // We don't want to lockup the event thread
    // (which this was called on).
    startShutdownThread(
        // Removing the tray icon is a native call that stalled the EDT, so it runs first on the
        // shutdown thread.
        () -> NotifyUserProxy.instance().hideTrayIcon(),
        () -> {
          VPNStatusRefresher.getInstance().shutdown();
          SearchMediator.instance().shutdown();
          LimeWireCore.instance().getLifecycleManager().shutdown(null);
        },
        () -> System.exit(0));
  }

  /**
   * Starts the "Shutdown Thread": removes the tray icon, shuts the core down, then exits, even if
   * one of the first two steps fails.
   */
  static Thread startShutdownThread(Runnable removeTrayIcon, Runnable shutdownCore, Runnable exit) {
    Thread shutdown =
        new Thread(
            () -> {
              try {
                LOG.info("Shutdown thread started");
                try {
                  removeTrayIcon.run();
                } catch (Throwable t) {
                  LOG.warn("Unable to remove the tray icon", t);
                }
                shutdownCore.run();
                LOG.info("System exit");
              } catch (Throwable t) {
                t.printStackTrace();
              } finally {
                exit.run();
              }
            },
            "Shutdown Thread");
    shutdown.start();
    return shutdown;
  }
}
