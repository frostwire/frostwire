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

package com.limegroup.gnutella.gui.search;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

/**
 * A Search Result transferable.  Contains the ResultPanel & TableLines
 * that are being transferred.
 */
public class SearchResultTransferable implements Transferable {
    public static final DataFlavor dataFlavor = new DataFlavor(SearchResultTransferable.class, "FrostWire Search Result");
    private final SearchResultMediator panel;
    private final SearchResultDataLine[] lines;

    public SearchResultTransferable(SearchResultMediator panel, SearchResultDataLine[] lines) {
        this.panel = panel;
        this.lines = lines;
    }

    public SearchResultMediator getResultPanel() {
        return panel;
    }

    public SearchResultDataLine[] getTableLines() {
        return lines;
    }

    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{dataFlavor};
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor.equals(dataFlavor);
    }

    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!isDataFlavorSupported(flavor))
            throw new UnsupportedFlavorException(flavor);
        else
            return this;
    }
}