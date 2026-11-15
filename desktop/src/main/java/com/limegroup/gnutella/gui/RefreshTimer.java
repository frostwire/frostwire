package com.limegroup.gnutella.gui;

import com.frostwire.gui.PerformanceMode;
import javax.swing.*;

/** This class handles the timer that refreshes the gui after every specified interval. */
final class RefreshTimer {
  /** The interval between statistics updates in milliseconds. */
  static final int UPDATE_TIME = 1000;

  /**
   * Lite refreshes the tables, the transfer details and the status bar half as often
   * (frostwire/frostwire#1555). Only what they show waits; transfers and searches run as fast.
   */
  static final int LITE_UPDATE_TIME = 2000;

  /** variable for timer that updates the gui. */
  private final Timer timer;

  /** Creates the timer and the ActionListener associated with it. */
  RefreshTimer() {
    this(() -> GUIMediator.instance().refreshGUI());
  }

  RefreshTimer(Runnable refreshGUI) {
    timer = new Timer(interval(), null);
    // Each tick follows the mode, so a change in Options applies from the next tick.
    timer.addActionListener(
        e -> {
          timer.setDelay(interval());
          refreshGUI.run();
        });
  }

  static int interval() {
    return PerformanceMode.isLite() ? LITE_UPDATE_TIME : UPDATE_TIME;
  }

  int delay() {
    return timer.getDelay();
  }

  /** Runs one tick as the timer does, for tests. */
  void tick() {
    for (java.awt.event.ActionListener listener : timer.getActionListeners()) {
      listener.actionPerformed(null);
    }
  }

  /** Starts the timer that updates the gui. */
  void startTimer() {
    timer.start();
  }

  void stopTimer() {
    timer.stop();
  }
}
