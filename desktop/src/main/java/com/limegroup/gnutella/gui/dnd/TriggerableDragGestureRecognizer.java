package com.limegroup.gnutella.gui.dnd;

import javax.swing.*;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.event.MouseEvent;

/**
 * A simple DragGestureRecognizer that can be immediately triggered to start a drag.
 */
public class TriggerableDragGestureRecognizer extends DragGestureRecognizer {
    /**
     *
     */
    private static final long serialVersionUID = 514682570826668981L;

    TriggerableDragGestureRecognizer(DragGestureListener dgl) {
        super(DragSource.getDefaultDragSource(), null, TransferHandler.NONE, dgl);
    }

    /**
     * Immediately starts a drag from the given component.
     *
     * @param c          The component to initiate the drag
     * @param e          The MouseEvent that sparked the drag.
     * @param srcActions The actions allowed from the source.
     * @param action     The action that began the drag.
     */
    void trigger(JComponent c, MouseEvent e, int srcActions, int action) {
        setComponent(c);
        setSourceActions(srcActions);
        appendEvent(e);
        fireDragGestureRecognized(action, e.getPoint());
    }

    protected void registerListeners() {
    }

    protected void unregisterListeners() {
    }
}