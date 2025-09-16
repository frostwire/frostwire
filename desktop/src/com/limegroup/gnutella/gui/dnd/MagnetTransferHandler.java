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

import com.limegroup.gnutella.ExternalControl;
import com.limegroup.gnutella.MagnetOptions;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.search.MagnetClipboardListener;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

/**
 * Transferhandler that handles drags of magnet links onto limewire by
 * starting downloads for them. Defers actual handling of magnets to {@link
 * ExternalControl#handleMagnetRequest(String)}.
 */
public class MagnetTransferHandler extends LimeTransferHandler {
    @Override
    public boolean canImport(JComponent c, DataFlavor[] flavors, DropInfo ddi) {
        return canImport(c, flavors);
    }

    @Override
    public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
        return DNDUtils.contains(transferFlavors, FileTransferable.URIFlavor);
    }

    @Override
    public boolean importData(JComponent c, Transferable t, DropInfo ddi) {
        return importData(c, t);
    }

    @Override
    public boolean importData(JComponent comp, Transferable t) {
        if (!canImport(comp, t.getTransferDataFlavors()))
            return false;
        try {
            String request = (String) t.getTransferData(FileTransferable.URIFlavor);
            if (request.contains("xt=urn:btih")) {
                GUIMediator.instance().openTorrentURI(request, true);
                return true;
            }
            MagnetOptions[] magnets =
                    MagnetOptions.parseMagnets((String) t.getTransferData(FileTransferable.URIFlavor));
            if (magnets.length > 0) {
                MagnetClipboardListener.handleMagnets(magnets);
                return true;
            }
        } catch (UnsupportedFlavorException | IOException ignored) {
        }
        return false;
    }
}
