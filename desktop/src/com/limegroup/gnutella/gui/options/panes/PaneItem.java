package com.limegroup.gnutella.gui.options.panes;

import java.awt.*;
import java.io.IOException;

/**
 * An object that defines the basic functions of one <i>option item</i>, or
 * one individual panel that displays a set of configurable options to the
 * user.<p>
 * <p>
 * The `PaneItem` interface provides the important ability to apply
 * the changes to options provided in the panel.
 */
//2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
public interface PaneItem {
    /**
     * Returns the `Container` for this set of options.
     *
     * @return the `Container` for this set of options
     */
    Container getContainer();

    /**
     * Sets the options for the fields in this `PaneItem` when the
     * window is shown.
     */
    void initOptions();

    /**
     * Applies the options currently set in this `PaneItem`.
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
