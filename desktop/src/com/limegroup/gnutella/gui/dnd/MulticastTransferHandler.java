/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.dnd;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Forwards implemented methods to list of handlers until it finds one
 * that returns <code>true</code>.
 */
public class MulticastTransferHandler extends LimeTransferHandler {
    private final ArrayList<LimeTransferHandler> handlers;
    private LimeTransferHandler lastTransferable;

    @SuppressWarnings("unused")
    public MulticastTransferHandler() {
        handlers = new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public MulticastTransferHandler(LimeTransferHandler handler) {
        handlers = new ArrayList<>();
        handlers.add(handler);
    }

    public MulticastTransferHandler(LimeTransferHandler head,
                                    Collection<LimeTransferHandler> tail) {
        handlers = new ArrayList<>();
        handlers.add(head);
        handlers.addAll(tail);
    }

    public MulticastTransferHandler(Collection<LimeTransferHandler> defaultHandlers) {
        handlers = new ArrayList<>();
        handlers.addAll(defaultHandlers);
    }

    @SuppressWarnings("unused")
    public void addTransferHandler(LimeTransferHandler handler) {
        handlers.add(handler);
    }

    @SuppressWarnings("unused")
    public void removeTransferHandler(LimeTransferHandler handler) {
        handlers.remove(handler);
    }

    private LimeTransferHandler[] getHandlers() {
        return handlers.toArray(new LimeTransferHandler[0]);
    }

    @Override
    public boolean canImport(JComponent c, DataFlavor[] flavors, DropInfo ddi) {
        for (LimeTransferHandler handler : getHandlers()) {
            if (handler.canImport(c, flavors, ddi)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
        for (LimeTransferHandler handler : getHandlers()) {
            if (handler.canImport(comp, transferFlavors)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        for (LimeTransferHandler handler : getHandlers()) {
            Transferable t = handler.createTransferable(c);
            if (t != null) {
                lastTransferable = handler;
                return t;
            }
        }
        lastTransferable = null;
        return null;
    }

    @Override
    public boolean importData(JComponent c, Transferable t, DropInfo ddi) {
        for (LimeTransferHandler handler : getHandlers()) {
            if (handler.importData(c, t, ddi)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean importData(JComponent comp, Transferable t) {
        for (LimeTransferHandler handler : getHandlers()) {
            if (handler.importData(comp, t)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getSourceActions(JComponent c) {
        int sourceActions = NONE;
        for (LimeTransferHandler handler : getHandlers()) {
            sourceActions |= handler.getSourceActions(c);
        }
        return sourceActions;
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        if (lastTransferable != null) {
            lastTransferable.exportDone(source, data, action);
            lastTransferable = null;
        }
    }
}
