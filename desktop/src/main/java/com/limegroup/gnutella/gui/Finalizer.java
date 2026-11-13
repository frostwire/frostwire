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
public final class Finalizer {
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
    shutdown(
        () -> {
          ShutdownWindow window = new ShutdownWindow();
          GUIUtils.centerOnScreen(window);
          window.setVisible(true);
        },
        // Removing the tray icon is a native call that stalled the EDT, so it runs first on the
        // shutdown thread.
        () -> NotifyUserProxy.instance().hideTrayIcon(),
        () -> {
          VPNStatusRefresher.getInstance().shutdown();
          // Not instance(): it would build the search UI after a start that failed before that,
          // and fail there before the core below shuts down.
          SearchMediator searchMediator = SearchMediator.instanceIfConstructed();
          if (searchMediator != null) {
            searchMediator.shutdown();
          }
          LimeWireCore.instance().getLifecycleManager().shutdown(null);
        },
        () -> System.exit(0));
  }

  /**
   * Shows the shutdown window, then shuts down on the "Shutdown Thread" so the event thread this
   * runs on is not locked up. The window is a courtesy: Windows refuses to create new windows for a
   * process while the session is ending (the JDK then throws InternalError from {@code
   * WComponentPeer.checkCreation}), and that used to stop the shutdown before it started, leaving
   * FrostWire running with its main window hidden (frostwire/frostwire#1524).
   */
  static Thread shutdown(
      Runnable showShutdownWindow, Runnable removeTrayIcon, Runnable shutdownCore, Runnable exit) {
    try {
      showShutdownWindow.run();
    } catch (Throwable t) {
      LOG.warn("Unable to show the shutdown window, shutting down without it", t);
    }
    return startShutdownThread(removeTrayIcon, shutdownCore, exit);
  }

  /**
   * Exits the virtual machine from a new thread, for exits that skip {@link #shutdown()} because
   * the core never started (the setup wizard, the fatal error dialog). System.exit runs every
   * shutdown hook and waits for it, which must not happen on the EDT: the UI freezes meanwhile, and
   * a hook that needs the EDT would never finish.
   */
  public static void exitOffEdt(int status) {
    startExitThread(() -> System.exit(status));
  }

  static Thread startExitThread(Runnable exit) {
    Thread thread = new Thread(exit, "Exit Thread");
    thread.start();
    return thread;
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
