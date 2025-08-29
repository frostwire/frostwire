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