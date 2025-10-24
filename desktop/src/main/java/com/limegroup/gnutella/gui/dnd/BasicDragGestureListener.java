package com.limegroup.gnutella.gui.dnd;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;

/**
 * A listener for drag gesture events.
 */
class BasicDragGestureListener implements DragGestureListener {
    /**
     * Initiates a drag with the files in the selected rows.
     */
    public void dragGestureRecognized(DragGestureEvent dge) {
        JComponent c = (JComponent) dge.getComponent();
        LimeTransferHandler th = (LimeTransferHandler) c.getTransferHandler();
        //This is a LibraryTableTransferHandler.createTransferable(component)
        Transferable t = th.createTransferable(c);
        if (t != null) {
            boolean scrolls = c.getAutoscrolls();
            c.setAutoscrolls(false);
            try {
                Image img = null;
                if (DragSource.isDragImageSupported())
                    img = th.getImageRepresentation(t);
                if (img != null) {
                    dge.startDrag(null,
                            img,
                            new Point(2, 2),
                            t,
                            new BasicDragSourceListener(scrolls));
                } else {
                    dge.startDrag(null,
                            t,
                            new BasicDragSourceListener(scrolls));
                }
                return;
            } catch (RuntimeException re) {
                c.setAutoscrolls(scrolls);
            }
        }
        th.exportDone(c, t, TransferHandler.NONE); // only reached if no transferable or RuntimeException
    }
}