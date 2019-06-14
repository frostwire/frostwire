package com.limegroup.gnutella.gui;

import javax.swing.*;

/**
 * This class handles the timer that refreshes the gui after every
 * specified interval.
 */
final class RefreshTimer {
    /**
     * The interval between statistics updates in milliseconds.
     */
    private static final int UPDATE_TIME = 1000;
    /**
     * variable for timer that updates the gui.
     */
    private final Timer timer;

    /**
     * Creates the timer and the ActionListener associated with it.
     */
    RefreshTimer() {
        timer = new Timer(UPDATE_TIME, e -> refreshGUI());
    }

    /**
     * Starts the timer that updates the gui.
     */
    void startTimer() {
        timer.start();
    }

    void stopTimer() {
        timer.stop();
    }

    /**
     * Refreshes all of the gui elements.
     */
    private void refreshGUI() {
        GUIMediator.instance().refreshGUI();
    }
}
