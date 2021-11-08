/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
 * Copyright (c) 2011-2021, FrostWire(R). All rights reserved.
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
