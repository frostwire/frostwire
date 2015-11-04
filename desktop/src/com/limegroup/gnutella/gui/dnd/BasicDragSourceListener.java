package com.limegroup.gnutella.gui.dnd;

import java.awt.dnd.DragSourceContext;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;

import javax.swing.JComponent;
import javax.swing.TransferHandler;

/**
 * The default drag source listener, signifies to the TransferHandler
 * when the drag has finished.
 */
final class BasicDragSourceListener implements DragSourceListener {
    
    private final boolean scrolled;
    
    BasicDragSourceListener(boolean scrolled) {
        this.scrolled = scrolled;
    }

    /** Notifies the TransferHandler that the xport is finished. */
    public void dragDropEnd(DragSourceDropEvent dsde) {
        DragSourceContext dsc = dsde.getDragSourceContext();
        JComponent c = (JComponent)dsc.getComponent();
        if (dsde.getDropSuccess()) {
            ((LimeTransferHandler)c.getTransferHandler()).exportDone(c, dsc.getTransferable(), dsde.getDropAction());
        } else {
            ((LimeTransferHandler)c.getTransferHandler()).exportDone(c, dsc.getTransferable(), TransferHandler.NONE);
        }
        c.setAutoscrolls(scrolled);
    }
    
    public void dragEnter(DragSourceDragEvent dsde) {}
    public void dragExit(DragSourceEvent dse) {}
    public void dragOver(DragSourceDragEvent dsde) {}
    public void dropActionChanged(DragSourceDragEvent dsde) {}
}