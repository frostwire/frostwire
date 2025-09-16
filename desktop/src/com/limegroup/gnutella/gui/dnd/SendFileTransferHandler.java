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

import com.frostwire.gui.bittorrent.SendFileProgressDialog;
import com.frostwire.gui.tabs.TransfersTab;
import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;

/**
 * Handles local files being dropped on limewire by asking the user if
 * s/he wants to share them.
 */
public class SendFileTransferHandler extends LimeTransferHandler {
    /**
     * Returns true if files were shared
     *
     * @param files
     * @return
     */
    private static boolean handleFiles(final File[] files) {
        String fileFolder = files[0].isFile() ? I18n.tr("file") : I18n.tr("folder");
        DialogOption result = GUIMediator.showYesNoMessage(I18n.tr("Do you want to send this {0} to a friend?", fileFolder) + "\n\n\"" + files[0].getName() + "\"", I18n.tr("Send files with FrostWire"), JOptionPane.QUESTION_MESSAGE);
        if (result == DialogOption.YES) {
            new SendFileProgressDialog(GUIMediator.getAppFrame(), files[0]).setVisible(true);
            GUIMediator.instance().showTransfers(TransfersTab.FilterMode.ALL);
            return true;
        }
        return false;
    }

    @Override
    public boolean canImport(JComponent c, DataFlavor[] flavors, DropInfo ddi) {
        return canImport(c, flavors);
    }

    @Override
    public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
        return DNDUtils.containsFileFlavors(transferFlavors);
    }

    @Override
    public boolean importData(JComponent c, Transferable t, DropInfo ddi) {
        return importData(c, t);
    }

    @Override
    public boolean importData(JComponent comp, Transferable t) {
        if (!canImport(comp, t.getTransferDataFlavors())) {
            return false;
        }
        try {
            final File[] files = DNDUtils.getFiles(t);
            //We will only send either 1 folder, or 1 file.
            if (files.length == 1) {
                //return handleFiles(files);
                SwingUtilities.invokeLater(() -> handleFiles(files));
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
