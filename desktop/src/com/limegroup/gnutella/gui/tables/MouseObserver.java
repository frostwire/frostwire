package com.limegroup.gnutella.gui.tables;

import java.awt.event.MouseEvent;

/**
 * An observer for mouse-based events.
 */
public interface MouseObserver {
    
    public void handleMouseClick(MouseEvent e);
    
    /**
     * Handles when the mouse is double-clicked.
     */
    public void handleMouseDoubleClick(MouseEvent e);
    
    /**
     * Handles a right-mouse click.
     */
    public void handleRightMouseClick(MouseEvent e);
    
    /**
     * Handles a trigger to the popup menu.
     */
    public void handlePopupMenu(MouseEvent e);
}    