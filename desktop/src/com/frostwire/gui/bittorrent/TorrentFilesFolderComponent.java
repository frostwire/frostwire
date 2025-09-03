/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.gui.bittorrent;

import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.ButtonRow;
import com.limegroup.gnutella.gui.FileChooserHandler;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.SharingSettings;
import org.limewire.util.CommonUtils;
import com.frostwire.util.OSUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public final class TorrentFilesFolderComponent extends JPanel {
    private static String errorMessage;
    private final JTextField folderTextField;

    public TorrentFilesFolderComponent(boolean border) {
        folderTextField = new JTextField(SharingSettings.TORRENTS_DIR_SETTING.getValueAsString());
        setLayout(new GridBagLayout());
        if (border) {
            setBorder(ThemeMediator.createTitledBorder(I18n.tr("Torrent Files (.torrent) Folder")));
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
        ButtonRow buttonRow = new ButtonRow(new Action[]{new DefaultAction(), new BrowseAction()}, ButtonRow.X_AXIS, ButtonRow.LEFT_GLUE);
        boolean isPortable = CommonUtils.isPortable();
        folderTextField.setEnabled(!isPortable);
        buttonRow.setButtonsEnabled(!isPortable);
        add(buttonRow, gbc);
    }

    /**
     * The torrent files path is only valid as long as it's a valid directory
     * that can be written to.
     */
    private static boolean isTorrentFilesFolderPathValid(boolean checkExist, File folder) {
        if (checkExist) {
            //is folder useable
            if (!(folder.exists() && folder.isDirectory() && folder.canWrite())) {
                errorMessage = I18n.tr("Please enter a valid path for the Torrent Files Folder");
                return false;
            }
        }
        String lowerCaseFolderPath = folder.getAbsolutePath().toLowerCase();
        //avoid user stupidity, do not save files anywhere in program files.
        String programFiles = System.getenv("ProgramFiles");
        return !OSUtils.isWindows() || programFiles == null || !lowerCaseFolderPath.contains(programFiles.toLowerCase());
    }

    public static String getError() {
        return errorMessage;
    }

    public String getTorrentFilesFolderPath() {
        return folderTextField.getText();
    }

    public boolean isTorrentFilesFolderPathValid(boolean checkExist) {
        //has to be non empty, writeable, must be a folder
        if (folderTextField.getText().trim().length() == 0) {
            errorMessage = I18n.tr("You forgot to enter a path for the Torrent Files Folder.");
            return false;
        }
        String path = folderTextField.getText().trim();
        File folder = new File(path);
        return isTorrentFilesFolderPathValid(checkExist, folder);
    }

    private class DefaultAction extends AbstractAction {

        DefaultAction() {
            putValue(Action.NAME, I18n.tr("Use Default"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Use the Default Folder"));
        }

        public void actionPerformed(ActionEvent e) {
            folderTextField.setText(SharingSettings.DEFAULT_TORRENTS_DIR.getAbsolutePath());
        }
    }

    private class BrowseAction extends AbstractAction {

        BrowseAction() {
            putValue(Action.NAME, I18n.tr("Browse..."));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Choose Another Folder"));
        }

        public void actionPerformed(ActionEvent e) {
            File saveDir = FileChooserHandler.getInputDirectory(TorrentFilesFolderComponent.this);
            if (saveDir == null || !saveDir.isDirectory())
                return;
            folderTextField.setText(saveDir.getAbsolutePath());
        }
    }
}