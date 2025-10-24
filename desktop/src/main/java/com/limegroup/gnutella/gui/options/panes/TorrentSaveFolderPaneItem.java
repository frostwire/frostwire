/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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
