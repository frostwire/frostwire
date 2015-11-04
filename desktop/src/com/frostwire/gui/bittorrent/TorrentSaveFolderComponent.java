/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.gui.bittorrent;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.limewire.util.CommonUtils;
import org.limewire.util.OSUtils;

import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.ButtonRow;
import com.limegroup.gnutella.gui.FileChooserHandler;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.SharingSettings;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public final class TorrentSaveFolderComponent extends JPanel {

    private JTextField folderTextField;
    private static String errorMessage;

    public TorrentSaveFolderComponent(boolean border) {
        folderTextField = new JTextField(SharingSettings.TORRENT_DATA_DIR_SETTING.getValueAsString());

        setLayout(new GridBagLayout());
        if (border) {
            setBorder(ThemeMediator.createTitledBorder(I18n.tr("Torrent Data Save Folder")));
        }

        GridBagConstraints gbc = new GridBagConstraints();

        // "Save Folder" text field
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridwidth = GridBagConstraints.RELATIVE;
        gbc.anchor = GridBagConstraints.LINE_START;
        add(folderTextField, gbc);

        // "Save Folder" buttons "User Default", "Browse..."
        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        
        ButtonRow buttonRow = new ButtonRow(new Action[] { new DefaultAction(), new BrowseAction() }, ButtonRow.X_AXIS, ButtonRow.LEFT_GLUE);
        boolean isPortable = CommonUtils.isPortable();
        folderTextField.setEnabled(!isPortable);
        buttonRow.setButtonsEnabled(!isPortable);
        add(buttonRow, gbc);
    }

    public String getTorrentSaveFolderPath() {
        return folderTextField.getText();
    }

    public boolean isTorrentSaveFolderPathValid(boolean checkExist) {
        //has to be non empty, writeable, must be a folder, and must not be Saved, Shared, or inside any of them.
        if (folderTextField.getText().trim().length() == 0) {
            errorMessage = I18n.tr("You forgot to enter a path for the Torrent Data Folder.");
            return false;
        }

        String path = folderTextField.getText().trim();
        File folder = new File(path);

        return isTorrentSaveFolderPathValid(checkExist, folder);
    }

    /**
     * The torrent save path is only valid as long as it's not inside (anywhere)
     * the Gnutella Save Folder.
     * 
     * This folder cannot also be a parent of the Gnutella Save folder.
     * 
     * @param gnutellaSaveFolders
     * @return
     */
    public static boolean isTorrentSaveFolderPathValid(boolean checkExist, File folder) {

        if (checkExist) {
            //is folder useable
            if (!(folder.exists() && folder.isDirectory() && folder.canWrite())) {
                errorMessage = I18n.tr("Please enter a valid path for the Torrent Data Folder");
                return false;
            }
        }
        String lowerCaseFolderPath = folder.getAbsolutePath().toLowerCase();

        //avoid user stupidity, do not save files anywhere in program files.
        if (OSUtils.isWindows() && lowerCaseFolderPath.contains(System.getenv("ProgramFiles").toLowerCase())) {
            return false;
        }

        return true;
    }

    public static boolean isParentOrChild(File torrentFolder, File otherFolder, String errorMessageSuffix) {
        //is folder inside gnutella save folder?
        try {
            if (torrentFolder.getCanonicalPath().startsWith(otherFolder.getCanonicalPath())) {
                errorMessage = I18n.tr("The Torrent Data Folder cannot be inside the") +" "+ errorMessageSuffix;
                return true;
            }
        } catch (IOException e) {
            errorMessage = I18n.tr("Could not resolve folder path.");
            return true;
        }

        //is folder a parent of the gnutella save folder?
        try {
            if (otherFolder.getCanonicalPath().startsWith(torrentFolder.getCanonicalPath())) {
                errorMessage = I18n.tr("The Torrent Data Folder cannot be a parent folder of the") + " " + errorMessageSuffix;
                return true;
            }
        } catch (IOException e) {
            errorMessage = I18n.tr("Could not resolve folder path.");
            return true;
        }

        return false;

    }

    private class DefaultAction extends AbstractAction {

        private static final long serialVersionUID = 7266666461649699221L;

        public DefaultAction() {
            putValue(Action.NAME, I18n.tr("Use Default"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Use the Default Folder"));
        }

        public void actionPerformed(ActionEvent e) {
            folderTextField.setText(SharingSettings.DEFAULT_TORRENT_DATA_DIR.getAbsolutePath());
        }
    }

    private class BrowseAction extends AbstractAction {

        private static final long serialVersionUID = 2976380710515726420L;

        public BrowseAction() {
            putValue(Action.NAME, I18n.tr("Browse..."));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Choose Another Folder"));
        }

        public void actionPerformed(ActionEvent e) {
            File saveDir = FileChooserHandler.getInputDirectory(TorrentSaveFolderComponent.this);
            if (saveDir == null || !saveDir.isDirectory())
                return;
            folderTextField.setText(saveDir.getAbsolutePath());
        }
    }

    public static String getError() {
        return errorMessage;
    }
}
