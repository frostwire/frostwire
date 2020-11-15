package com.limegroup.gnutella.gui.tables;

import java.awt.event.MouseEvent;

/**
 * An observer for mouse-based events.
 */
public interface MouseObserver {
    void handleMouseClick(MouseEvent e);

    /**
     * Handles when the mouse is double-clicked.
     */
    void handleMouseDoubleClick();

    /**
     * Handles a right-mouse click.
     */
    void handleRightMouseClick(MouseEvent e);

    /**
     * Handles a trigger to the popup menu.
     */
    void handlePopupMenu(MouseEvent e);
}    