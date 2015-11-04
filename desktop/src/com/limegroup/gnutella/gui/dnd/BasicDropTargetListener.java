package com.limegroup.gnutella.gui.dnd;

import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DropTargetContext;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;

import javax.swing.JComponent;
import javax.swing.TransferHandler;

/**
 * Listener used by LimeDropTarget to enable better LimeTransferHandler methods.
 * 
 * This enables the DropInfo to be passed to LimeTransferHandler during drops (and drags,
 * from the dropper's perspective).
 * 
 * This class will become obsolete with the advent of Java 1.6, which has
 * a TransferSupport class that takes care of all this.
 */
public class BasicDropTargetListener implements DropTargetListener {

    private boolean actionSupported(int action) {
        return (action & (TransferHandler.COPY_OR_MOVE)) != TransferHandler.NONE;
    }

    public void dragEnter(DropTargetDragEvent e) {
        DataFlavor[] flavors = e.getCurrentDataFlavors();
        DropTargetContext ctx = e.getDropTargetContext();
        JComponent c = (JComponent)ctx.getComponent();
        LimeTransferHandler handler = (LimeTransferHandler)c.getTransferHandler();
        DropDragInfo ddi = new DropDragInfo(e);
        
        if(handler != null && handler.canImport(c, flavors, ddi) && actionSupported(ddi.action))
            e.acceptDrag(ddi.action);
        else
            e.rejectDrag();
    }

    public void dragOver(DropTargetDragEvent e) {
        DataFlavor[] flavors = e.getCurrentDataFlavors();
        DropTargetContext ctx = e.getDropTargetContext();
        JComponent c = (JComponent)ctx.getComponent();
        LimeTransferHandler handler = (LimeTransferHandler)c.getTransferHandler();
        DropDragInfo ddi = new DropDragInfo(e);
        
        if(handler != null && handler.canImport(c, flavors, ddi) && actionSupported(ddi.action))
            e.acceptDrag(ddi.action);
        else
            e.rejectDrag();
    }

    public void dropActionChanged(DropTargetDragEvent e) {
        DataFlavor[] flavors = e.getCurrentDataFlavors();
        DropTargetContext ctx = e.getDropTargetContext();
        JComponent c = (JComponent)ctx.getComponent();
        LimeTransferHandler handler = (LimeTransferHandler)c.getTransferHandler();
        DropDragInfo ddi = new DropDragInfo(e);
        
        if(handler != null && handler.canImport(c, flavors, ddi) && actionSupported(ddi.action))
            e.acceptDrag(ddi.action);
        else
            e.rejectDrag();
    }    

    public void dragExit(DropTargetEvent e) {
    }

    public void drop(DropTargetDropEvent e) {
        DataFlavor[] flavors = e.getCurrentDataFlavors();
        DropTargetContext ctx = e.getDropTargetContext();
        JComponent c = (JComponent)ctx.getComponent();
        LimeTransferHandler handler = (LimeTransferHandler)c.getTransferHandler();
        DropDropInfo ddi = new DropDropInfo(e);
        
        if(handler != null && handler.canImport(c, flavors, ddi) && actionSupported(ddi.action)) {
            e.acceptDrop(ddi.action);

            try {
                Transferable t = e.getTransferable();
                e.dropComplete(handler.importData(c, t, ddi));
            } catch (RuntimeException re) {
                e.dropComplete(false);
            }
        } else {
            e.rejectDrop();
        }
    }
    
    // TODO dnd make reusable
    static class DropDragInfo implements DropInfo {
        private final DropTargetDragEvent event;
        int action;
        
        DropDragInfo(DropTargetDragEvent event) {
            this.event = event;
            this.action = event.getDropAction(); 
        }
        
        public void setDropAction(int action) {
            this.action = action;
        }

        public Transferable getTransferable() {
        	return event.getTransferable();
        }

        public int getDropAction() {
            return event.getDropAction();
        }
        
        public Point getPoint() {
            return event.getLocation();
        }
    }

    // TODO dnd make reusable
    static class DropDropInfo implements DropInfo {
        private final DropTargetDropEvent event;
        int action;
        
        DropDropInfo(DropTargetDropEvent event) {
            this.event = event;
            this.action = event.getDropAction();
        }
        
        public void setDropAction(int action) {
            this.action = action;
        }
        
        public Transferable getTransferable() {
            return event.getTransferable();
        }
        
        public int getDropAction() {
            return event.getDropAction();
        }
        
        public Point getPoint() {
            return event.getLocation();
        }
    }
}
