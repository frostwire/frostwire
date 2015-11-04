package com.limegroup.gnutella.gui.options.panes;

import java.awt.Container;
import java.io.IOException;

/**
 * An object that defines the basic functions of one <i>option item</i>, or
 * one individual panel that displays a set of configurable options to the 
 * user.<p>
 *
 * The <tt>PaneItem</tt> interface provides the important ability to apply 
 * the changes to options provided in the panel.
 */
//2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
public interface PaneItem {
	
	/**
	 * Returns the <tt>Container</tt> for this set of options.
	 *
	 * @return the <tt>Container</tt> for this set of options
	 */
	Container getContainer();

	/**
	 * Sets the options for the fields in this <tt>PaneItem</tt> when the 
	 * window is shown.
	 */
	void initOptions();

	/**
	 * Applies the options currently set in this <tt>PaneItem</tt>.
	 *
	 * @return <code>true</code> if the changed settings require a restart
	 * of the application
	 * @throws IOException if the options could not be fully applied
	 */
	boolean applyOptions() throws IOException;
	
	/**
	 * Determines if any elements on this pane require saving.
	 */
	boolean isDirty();
}
