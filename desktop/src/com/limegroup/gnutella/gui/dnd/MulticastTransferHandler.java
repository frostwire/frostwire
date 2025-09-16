/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
