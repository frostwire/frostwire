/*
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
