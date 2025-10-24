package com.limegroup.gnutella.gui.options;

import com.limegroup.gnutella.gui.options.panes.PaneItem;

import java.awt.*;
import java.io.IOException;

/**
 * An object that defines the basic functionality of an <i>OptionsPane</i>,
 * or one panel specifying a set of options in the options window.<p>
 * <p>
 * Each `OptionsPane` has a unique identifying name that allows it
 * to be displayed in the `CardLayout`.
 */
//2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
interface OptionsPane {
    /**
     * Returns the name of this `OptionsPane`.
     *
     * @return the name of this `OptionsPane`
     */
    String getName();

    /**
     * Returns the `Container` instance that holds the different
     * elements of this `OptionsPane`.
     *
     * @return the `Container` associated with this `OptionsPane`
     */
    Container getContainer();

    /**
     * Adds a new option item to this pane.
     *
     * @param item the `PaneItem` instance to add to this
     *             `OptionsPane`
     */
    void add(PaneItem item);

    /**
     * Sets the options for the fields in this `OptionsPane` when
     * the window is shown.
     */
    void initOptions();

    /**
     * Applies the currently selected options in this options pane to get
     * stored to disk.  Returns true if LimeWire must be restarted for the
     * option to fully take effect, false otherwise.
     *
     * @return a boolean indicating whether or not a restart is required.
     * @throws IOException if the options could not be fully applied
     */
    boolean applyOptions() throws IOException;

    /**
     * Determines if any of the PaneItems in this OptionPane require saving.
     */
    boolean isDirty();
}
