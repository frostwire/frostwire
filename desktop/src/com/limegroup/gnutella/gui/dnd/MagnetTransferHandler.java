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

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

import javax.swing.JComponent;

import com.limegroup.gnutella.ExternalControl;
import com.limegroup.gnutella.MagnetOptions;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.search.MagnetClipboardListener;

/**
 * Transferhandler that handles drags of magnet links onto limewire by
 * starting downloads for them. Defers actual handling of magnets to {@link
 * ExternalControl#handleMagnetRequest(String)}.
 */
public class MagnetTransferHandler extends LimeTransferHandler {

	/**
     * 
     */
    private static final long serialVersionUID = 5866840096804306495L;

    @Override
	public boolean canImport(JComponent c, DataFlavor[] flavors, DropInfo ddi) {
		return canImport(c, flavors);
	}
	
	@Override
	public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
		if (DNDUtils.contains(transferFlavors, FileTransferable.URIFlavor)) {
			return true;
		}
		return false;
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
				MagnetOptions.parseMagnets((String)t.getTransferData(FileTransferable.URIFlavor));
			
			
			if (magnets.length > 0) {
				MagnetClipboardListener.handleMagnets(magnets, false);
				return true;
			}
		} catch (UnsupportedFlavorException e) {
		} catch (IOException e) {
		}
		return false;
	}
	
}
