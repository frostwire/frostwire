package com.limegroup.gnutella.gui;

/**
 * This interface defines the functions of a class that should be
 * notified of UI refresh events.
 */
public interface RefreshListener {
	
	/**
	 * Called when a UI refresh event has occurred.  Refresh any elements
	 * of this component that need refreshing.
	 */
	void refresh();
}
