/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui.dnd;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;

/**
 * Listener used by LimeDropTarget to enable better LimeTransferHandler methods.
 * <p>
 * This enables the DropInfo to be passed to LimeTransferHandler during drops (and drags,
 * from the dropper's perspective).
 * <p>
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
        JComponent c = (JComponent) ctx.getComponent();
        LimeTransferHandler handler = (LimeTransferHandler) c.getTransferHandler();
        DropDragInfo ddi = new DropDragInfo(e);
        if (handler != null && handler.canImport(c, flavors, ddi) && actionSupported(ddi.action))
            e.acceptDrag(ddi.action);
        else
            e.rejectDrag();
    }

    public void dragOver(DropTargetDragEvent e) {
        DataFlavor[] flavors = e.getCurrentDataFlavors();
        DropTargetContext ctx = e.getDropTargetContext();
        JComponent c = (JComponent) ctx.getComponent();
        LimeTransferHandler handler = (LimeTransferHandler) c.getTransferHandler();
        DropDragInfo ddi = new DropDragInfo(e);
        if (handler != null && handler.canImport(c, flavors, ddi) && actionSupported(ddi.action))
            e.acceptDrag(ddi.action);
        else
            e.rejectDrag();
    }

    public void dropActionChanged(DropTargetDragEvent e) {
        DataFlavor[] flavors = e.getCurrentDataFlavors();
        DropTargetContext ctx = e.getDropTargetContext();
        JComponent c = (JComponent) ctx.getComponent();
        LimeTransferHandler handler = (LimeTransferHandler) c.getTransferHandler();
        DropDragInfo ddi = new DropDragInfo(e);
        if (handler != null && handler.canImport(c, flavors, ddi) && actionSupported(ddi.action))
            e.acceptDrag(ddi.action);
        else
            e.rejectDrag();
    }

    public void dragExit(DropTargetEvent e) {
    }

    public void drop(DropTargetDropEvent e) {
        DataFlavor[] flavors = e.getCurrentDataFlavors();
        DropTargetContext ctx = e.getDropTargetContext();
        JComponent c = (JComponent) ctx.getComponent();
        LimeTransferHandler handler = (LimeTransferHandler) c.getTransferHandler();
        DropDropInfo ddi = new DropDropInfo(e);
        if (handler != null && handler.canImport(c, flavors, ddi) && actionSupported(ddi.action)) {
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
        final int action;

        DropDragInfo(DropTargetDragEvent event) {
            this.event = event;
            this.action = event.getDropAction();
        }

        public Transferable getTransferable() {
            return event.getTransferable();
        }

        public Point getPoint() {
            return event.getLocation();
        }
    }

    // TODO dnd make reusable
    static class DropDropInfo implements DropInfo {
        private final DropTargetDropEvent event;
        final int action;

        DropDropInfo(DropTargetDropEvent event) {
            this.event = event;
            this.action = event.getDropAction();
        }

        public Transferable getTransferable() {
            return event.getTransferable();
        }

        public Point getPoint() {
            return event.getLocation();
        }
    }
}
