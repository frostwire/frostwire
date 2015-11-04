package com.limegroup.gnutella.gui.tables;

import java.awt.event.MouseEvent;

import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputListener;
import javax.swing.table.JTableHeader;


/**
 * This class handles mouse input to the table.
 */
//2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
public final class HeaderMouseListener implements MouseInputListener {

    private HeaderMouseObserver hmo;
    private boolean isResizing = false;
    
    public HeaderMouseListener(HeaderMouseObserver hmo) {
        this.hmo = hmo;
    }

    /**
     * Invoked when the mouse has been clicked on a component.
	 *
	 * @param event the <tt>MouseEvent</tt> that triggered this call
     */
    public void mouseClicked(MouseEvent ev) {
        if ( !tryPopup(ev) && SwingUtilities.isLeftMouseButton(ev)) {
            if ( !isResizing  )
                //generally used for sorting.
    	        hmo.handleHeaderColumnLeftClick(ev.getPoint());
    	    else
    	        isResizing = false;
		}
	}

    /**
     * Invoked when a mouse button has been pressed on a component.
	 *
	 * @param event the <tt>MouseEvent</tt> that triggered this call
     */
    public void mousePressed(MouseEvent ev) {
        if ( !tryPopup(ev) && SwingUtilities.isLeftMouseButton(ev)) {
            if (((JTableHeader)ev.getSource()).getResizingColumn() == null)
                hmo.handleHeaderColumnPressed(ev.getPoint());
            else
                isResizing = true;
            
        }
    }

    /**
     * Invoked when a mouse button has been released on a component.
	 *
	 * @param event the <tt>MouseEvent</tt> that triggered this call
     */
    public void mouseReleased(MouseEvent ev) {
        if( !tryPopup(ev) && SwingUtilities.isLeftMouseButton(ev)) {
            hmo.handleHeaderColumnReleased(ev.getPoint());
        }
    }

    /**
     * Invoked when the mouse enters a component.
	 *
	 * @param event the <tt>MouseEvent</tt> that triggered this call
     */
    public void mouseEntered(MouseEvent event) {}

    /**
     * Invoked when the mouse exits a component.
	 *
	 * @param event the <tt>MouseEvent</tt> that triggered this call
     */
    public void mouseExited(MouseEvent event) {}
    
    /**
     * Invoked when the mouse drags on a component.
     */
    public void mouseDragged(MouseEvent ev) {
        // we may still be resizing, but the drag cancels
        // the clicked event, so it doesn't matter if we set it to
        // false here.
        isResizing = false;
    }
    
    public void mouseMoved(MouseEvent ev) { }
    
    private boolean tryPopup(MouseEvent ev) {
        if ( ev.isPopupTrigger() ) {
            hmo.handleHeaderPopupMenu( ev.getPoint() );
            return true;
        }
        return false;
    }
}
