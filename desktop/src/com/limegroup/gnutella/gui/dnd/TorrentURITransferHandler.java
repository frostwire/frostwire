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

import com.limegroup.gnutella.gui.GUIMediator;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;

/**
 * TransferHandler that handles uris pointing to http urls of torrent files.
 * Downloads are started if all uris are of this type.
 * <p>
 * The scheme of the uri has to be "http" and the path name of the uri has to
 * end with ".torrent" not regarding casing.
 */
public class TorrentURITransferHandler extends LimeTransferHandler {
    /**
     *
     */
    private static final long serialVersionUID = 5212990516247901330L;

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
            URI[] uris = DNDUtils.getURIs(t);
            if (areAllTorrentURLs(uris)) {
                if (uris.length == 1) {
                    GUIMediator.instance().openTorrentURI(uris[0].toString(), true);
                } else {
                    for (URI uri : uris) {
                        GUIMediator.instance().openTorrentURI(uri.toString(), false);
                    }
                }
                return true;
            }
        } catch (UnsupportedFlavorException | IOException e) {
        }
        return false;
    }

    // made package private for tests
    private boolean areAllTorrentURLs(URI[] uris) {
        for (URI uri : uris) {
            String scheme = uri.getScheme();
            if (scheme == null || !scheme.equalsIgnoreCase("http")) {
                return false;
            }
            String path = uri.getPath();
            if (path == null || !path.toLowerCase(Locale.US).endsWith(".torrent")) {
                return false;
            }
        }
        return true;
    }
}
