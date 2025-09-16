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