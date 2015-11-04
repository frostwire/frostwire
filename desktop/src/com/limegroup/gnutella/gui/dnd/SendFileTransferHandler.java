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

import com.frostwire.gui.bittorrent.SendFileProgressDialog;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;
import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;

/**
 * Handles local files being dropped on limewire by asking the user if
 * s/he wants to share them.
 */
public class SendFileTransferHandler extends LimeTransferHandler {

    private static final long serialVersionUID = 6541019610960958928L;

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
        if (!canImport(comp, t.getTransferDataFlavors()))
            return false;

        try {
            final File[] files = DNDUtils.getFiles(t);

            //We will only send either 1 folder, or 1 file.
            if (files.length == 1) {
                //return handleFiles(files);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        handleFiles(files);
                    }
                });
                return true;
            }
        } catch (UnsupportedFlavorException e) {
        } catch (IOException e) {
        }
        return false;
    }

    /**
     * Returns true if files were shared
     *
     * @param files
     * @return
     */
    public static boolean handleFiles(final File[] files) {

        String fileFolder = files[0].isFile() ? I18n.tr("file") : I18n.tr("folder");
        DialogOption result = GUIMediator.showYesNoMessage(I18n.tr("Do you want to send this {0} to a friend?", fileFolder) + "\n\n\"" + files[0].getName() + "\"", I18n.tr("Send files with FrostWire"), JOptionPane.QUESTION_MESSAGE);

        if (result == DialogOption.YES) {
            new SendFileProgressDialog(GUIMediator.getAppFrame(), files[0]).setVisible(true);
            GUIMediator.instance().setWindow(GUIMediator.Tabs.SEARCH);
            UXStats.instance().log(UXAction.SHARING_TORRENT_CREATED_WITH_SEND_TO_FRIEND_FROM_DND);
            return true;
        }

        return false;
    }


}
