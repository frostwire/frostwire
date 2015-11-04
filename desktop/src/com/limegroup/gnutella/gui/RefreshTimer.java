package com.limegroup.gnutella.gui;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * This class handles the timer that refreshes the gui after every 
 * specified interval.
 */
public final class RefreshTimer {

	/** 
	 * The interval between statistics updates in milliseconds. 
	 */
	private final int UPDATE_TIME = 1000;

	/**
	 * variable for timer that updates the gui.
	 */
	private Timer _timer;
	  
	/**
	 * Creates the timer and the ActionListener associated with it.
	 */
	public RefreshTimer() {
		ActionListener refreshGUI = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
                refreshGUI();
			}
		};
		
		_timer = new Timer(UPDATE_TIME, refreshGUI);
	}

	/**
	 * Starts the timer that updates the gui.
	 */
	public void startTimer() {
		_timer.start();
	}

	/** 
	 * Refreshes all of the gui elements.
	 */
	private void refreshGUI() {
		GUIMediator.instance().refreshGUI();
	}
}
