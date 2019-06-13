package com.limegroup.gnutella.gui.actions;

import com.limegroup.gnutella.gui.IconButton;

import javax.swing.*;

/**
 * Extends Swing's action interface to provide more specific keys.
 */
public interface LimeAction extends Action {
    /**
     * Short name for the action which should be complimentary to an icon.
     * See {@link IconButton}.
     */
    String SHORT_NAME = "LimeShortName";
    /**
     * Name of the icon used when displaying this action. See {@link IconButton}.
     */
    String ICON_NAME = "LimeIconName";
    /**
     * Name of the icon to be used when rolling over the mouse if you don't want to use
     * the default brightening effect.
     */
    String ICON_NAME_ROLLOVER = "RollOverFrostIconName";
    /**
     * Type of Object
     * for color objects, specify the color code as well.  Spaces are represented by underscores.
     * ie. color_blue, color_yellow
     */
    String COLOR = "LimeColor";
}
