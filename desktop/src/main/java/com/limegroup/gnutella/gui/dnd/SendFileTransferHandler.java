/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.dnd;

import com.frostwire.gui.bittorrent.SendFileProgressDialog;
import com.frostwire.gui.tabs.TransfersTab;
import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import java.io.File;
import javax.swing.*;

/**
 * Handles one local file or folder dropped on FrostWire by asking the user whether to send it, then
 * building and seeding its torrent right away (SendFileProgressDialog).
 */
public class SendFileTransferHandler extends FileOrFolderTransferHandler {

  public SendFileTransferHandler() {
    super(SendFileTransferHandler::handleFile);
  }

  private static void handleFile(final File file) {
    String fileFolder = file.isFile() ? I18n.tr("file") : I18n.tr("folder");
    DialogOption result =
        GUIMediator.showYesNoMessage(
            I18n.tr("Do you want to send this {0} to a friend?", fileFolder)
                + "\n\n\""
                + file.getName()
                + "\"",
            I18n.tr("Send files with FrostWire"),
            JOptionPane.QUESTION_MESSAGE);
    if (result == DialogOption.YES) {
      new SendFileProgressDialog(GUIMediator.getAppFrame(), file).setVisible(true);
      GUIMediator.instance().showTransfers(TransfersTab.FilterMode.ALL);
    }
  }
}
