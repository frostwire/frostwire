/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.

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

package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.gui.bittorrent.TorrentSaveFolderComponent;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.options.OptionsConstructor;
import com.limegroup.gnutella.gui.options.OptionsMediator;
import com.limegroup.gnutella.settings.LibrarySettings;
import com.limegroup.gnutella.settings.SharingSettings;

import java.io.File;
import java.io.IOException;

/**
 * @author gubatron
 * @author aldenml
 */
public final class TorrentSaveFolderPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Default Save Folder");
    private final TorrentSaveFolderComponent COMPONENT;

    public TorrentSaveFolderPaneItem() {
        super(TITLE, I18n.tr("Choose the folder where downloads will be saved to"));
        COMPONENT = new TorrentSaveFolderComponent(false);
        add(COMPONENT);
    }

    @Override
    public void initOptions() {
        // nothing the component does it.
    }

    @Override
    public boolean applyOptions() throws IOException {
        if (!COMPONENT.isTorrentSaveFolderPathValid(true)) {
            GUIMediator.showError(TorrentSaveFolderComponent.getError());
            throw new IOException();
        }
        boolean dirty = isDirty();
        if (dirty) {
            final File newSaveFolder = new File(COMPONENT.getTorrentSaveFolderPath());
            updateLibraryFolders(newSaveFolder);
            updateDefaultSaveFolders(newSaveFolder);
        }
        return dirty;
    }

    /**
     * Adds this save folder to the Library so the user can find the files he's going to save in the different sections of the Library.
     * If the user wants the previous save folder out of the library she'll have to remove it by hand.
     *
     * @param newSaveFolder
     */
    private void updateLibraryFolders(final File newSaveFolder) {
        LibrarySettings.DIRECTORIES_TO_INCLUDE.add(newSaveFolder);
        //if we don't re-init the Library Folders Pane, it will exclude this folder when options are applied.
        //so we reload it with our new folder from here.
        OptionsMediator.instance().reinitPane(OptionsConstructor.LIBRARY_KEY);
    }

    private void updateDefaultSaveFolders(File newSaveFolder) {
        SharingSettings.TORRENT_DATA_DIR_SETTING.setValue(newSaveFolder);
        BTEngine.getInstance().moveStorage(SharingSettings.TORRENT_DATA_DIR_SETTING.getValue());
    }

    @Override
    public boolean isDirty() {
        return !SharingSettings.TORRENT_DATA_DIR_SETTING.getValueAsString().equals(COMPONENT.getTorrentSaveFolderPath());
    }
}
