package com.limegroup.gnutella.gui.dnd;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/**
 * A better TransferHandler.
 */
public class LimeTransferHandler extends TransferHandler {
    /**
     *
     */
    private static final long serialVersionUID = 7753614134944789178L;
    private static TriggerableDragGestureRecognizer recognizer;
    private final int supportedActions;

    /**
     * Constructs a LimeTransferHandler with no supported actions.
     */
    protected LimeTransferHandler() {
        this.supportedActions = NONE;
    }

    /**
     * Creates a new LimeTransferHandler that supports the given actions.
     *
     * @param supportedActions
     */
    protected LimeTransferHandler(int supportedActions) {
        this.supportedActions = supportedActions;
    }

    /**
     * Determines whether or not the given component can accept the given transferFlavors.
     * This returns false by default.
     */
    public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
        return false;
    }

    /**
     * Determines if the data can be imported.
     */
    public boolean canImport(JComponent c, DataFlavor[] flavors, DropInfo ddi) {
        return false;
    }

    /**
     * Attempts to create a transferable from the given component.
     * This returns null by default.
     */
    @Override
    protected Transferable createTransferable(JComponent c) {
        return new BasicTransferableCreator().getTransferable();
    }

    /**
     * Does nothing.
     */
    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        // Does nothing.
    }

    /**
     * Returns the actions supported by the given component.
     * By default, this returns no actions supported.
     */
    public int getSourceActions(JComponent c) {
        return supportedActions;
    }

    /**
     * UNUSED -- This method is an API bug, it should be returning an Image.
     * Use getImageRepresentation instead.
     *
     * @deprecated
     */
    @Deprecated
    public final Icon getVisualRepresentation(Transferable t) {
        throw new IllegalStateException("USE getImageRepresentation INSTEAD");
    }

    /**
     * Returns an image representation of the given transferable.
     *
     * @param t
     * @return
     */
    public Image getImageRepresentation(Transferable t) {
        return new TransferVisualizer(t).getImage();
    }

    /**
     * Imports data into the given component.
     * This returns true if data was imported, false otherwise.
     */
    public boolean importData(JComponent comp, Transferable t) {
        return false;
    }

    /**
     * Imports the data into the given component.
     */
    public boolean importData(JComponent c, Transferable t, DropInfo ddi) {
        // TODO Auto-generated method stub
        return false;
    }

    /**
     * Initiates a drag operation from the given component.
     */
    public void exportAsDrag(JComponent comp, InputEvent e, int action) {
        int srcActions = getSourceActions(comp);
        int dragAction = srcActions & action;
        if (!(e instanceof MouseEvent))
            dragAction = NONE;
        if (dragAction != NONE && !GraphicsEnvironment.isHeadless()) {
            // Use a custom DragGestureRecognizer that we can automatically
            // trigger to fire a dragGestureRecognized event.
            if (recognizer == null)
                recognizer = new TriggerableDragGestureRecognizer(new BasicDragGestureListener());
            recognizer.trigger(comp, (MouseEvent) e, srcActions, dragAction);
        } else {
            exportDone(comp, null, NONE);
        }
    }
}
