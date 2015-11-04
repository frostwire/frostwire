package com.limegroup.gnutella.gui.options;

import java.awt.Container;
import java.io.IOException;

import com.limegroup.gnutella.gui.options.panes.PaneItem;

/**
 * An object that defines the basic functionality of an <i>OptionsPane</i>,
 * or one panel specifying a set of options in the options window.<p>
 * 
 * Each <tt>OptionsPane</tt> has a unique identifying name that allows it
 * to be displayed in the <tt>CardLayout</tt>.
 */
//2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
public interface OptionsPane {
	
	/**
	 * Returns the name of this <tt>OptionsPane</tt>.
	 *
	 * @return the name of this <tt>OptionsPane</tt>
	 */
	String getName();

	/**
	 * Returns the <tt>Container</tt> instance that holds the different 
	 * elements of this <tt>OptionsPane</tt>.
	 *
	 * @return the <tt>Container</tt> associated with this <tt>OptionsPane</tt>
	 */
	Container getContainer();

	/**
	 * Adds a new option item to this pane.
	 *
	 * @param item the <tt>PaneItem</tt> instance to add to this 
	 *             <tt>OptionsPane</tt>
	 */
	void add(PaneItem item);

	/**
	 * Sets the options for the fields in this <tt>OptionsPane</tt> when 
	 * the window is shown.
	 */
	void initOptions();

	/**
	 * Applies the currently selected options in this options pane to get
	 * stored to disk.  Returns true if LimeWire must be restarted for the
     * option to fully take effect, false otherwise.
	 *
	 * @throws IOException if the options could not be fully applied
     * @return a boolean indicating whether or not a restart is required.
	 */
	boolean applyOptions() throws IOException;
	
	/**
	 * Determines if any of the PaneItems in this OptionPane require saving.
	 */
	boolean isDirty();
}
