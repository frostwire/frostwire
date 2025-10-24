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

import com.frostwire.gui.bittorrent.TorrentFilesFolderComponent;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.SharingSettings;

import java.io.File;
import java.io.IOException;

/**
 * @author gubatron
 * @author aldenml
 */
public final class TorrentFilesFolderPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Torrent Files Folder");
    private final TorrentFilesFolderComponent COMPONENT;

    public TorrentFilesFolderPaneItem() {
        super(TITLE, I18n.tr("Choose the folder where .torrent files will be saved to"));
        COMPONENT = new TorrentFilesFolderComponent(false);
        add(COMPONENT);
    }

    @Override
    public void initOptions() {
        // nothing the component does it.
    }

    @Override
    public boolean applyOptions() throws IOException {
        if (!COMPONENT.isTorrentFilesFolderPathValid(true)) {
            GUIMediator.showError(TorrentFilesFolderComponent.getError());
            throw new IOException();
        }
        boolean dirty = isDirty();
        if (dirty) {
            final File newTorrentFilesFolder = new File(COMPONENT.getTorrentFilesFolderPath());
            updateDefaultTorrentFilesFolder(newTorrentFilesFolder);
        }
        return dirty;
    }

    private void updateDefaultTorrentFilesFolder(File newTorrentFilesFolder) {
        SharingSettings.TORRENTS_DIR_SETTING.setValue(newTorrentFilesFolder);
    }

    @Override
    public boolean isDirty() {
        return !SharingSettings.TORRENTS_DIR_SETTING.getValueAsString().equals(COMPONENT.getTorrentFilesFolderPath());
    }
}