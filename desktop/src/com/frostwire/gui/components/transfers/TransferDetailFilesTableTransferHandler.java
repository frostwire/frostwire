/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.components.transfers;

import com.limegroup.gnutella.gui.dnd.FileTransferable;

import javax.swing.*;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TransferDetailFilesTableTransferHandler extends TransferHandler {
    private final TransferDetailFilesTableMediator mediator;

    public TransferDetailFilesTableTransferHandler(TransferDetailFilesTableMediator transferDetailFilesTableMediator) {
        this.mediator = transferDetailFilesTableMediator;
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return false;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return COPY;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        List<TransferDetailFilesDataLine> lines = mediator.getSelectedLines();
        List<File> files = new ArrayList<>(lines.size());
        for (TransferDetailFilesDataLine line : lines) {
            files.add(line.getTransferItem().getFile());
        }
        return new FileTransferable(files);
    }
}
