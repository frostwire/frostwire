package com.limegroup.gnutella.gui.dnd;

import javax.swing.*;
import java.awt.dnd.*;

/**
 * The default drag source listener, signifies to the TransferHandler
 * when the drag has finished.
 */
final class BasicDragSourceListener implements DragSourceListener {
    private final boolean scrolled;

    BasicDragSourceListener(boolean scrolled) {
        this.scrolled = scrolled;
    }

    /**
     * Notifies the TransferHandler that the xport is finished.
     */
    public void dragDropEnd(DragSourceDropEvent dsde) {
        DragSourceContext dsc = dsde.getDragSourceContext();
        JComponent c = (JComponent) dsc.getComponent();
        if (dsde.getDropSuccess()) {
            ((LimeTransferHandler) c.getTransferHandler()).exportDone(c, dsc.getTransferable(), dsde.getDropAction());
        } else {
            ((LimeTransferHandler) c.getTransferHandler()).exportDone(c, dsc.getTransferable(), TransferHandler.NONE);
        }
        c.setAutoscrolls(scrolled);
    }

    public void dragEnter(DragSourceDragEvent dsde) {
    }

    public void dragExit(DragSourceEvent dse) {
    }

    public void dragOver(DragSourceDragEvent dsde) {
    }

    public void dropActionChanged(DragSourceDragEvent dsde) {
    }
}