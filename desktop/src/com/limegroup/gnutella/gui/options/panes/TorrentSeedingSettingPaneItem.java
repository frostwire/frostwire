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

package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.gui.bittorrent.BTDownloadMediator;
import com.frostwire.gui.bittorrent.TorrentSeedingSettingComponent;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.SharingSettings;

/**
 * @author gubatron
 * @author aldenml
 */
public class TorrentSeedingSettingPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Seeding Settings");
    private final static String LABEL = I18n.tr("Seeding is the process of connecting to a torrent when you have a complete file(s). Pieces of the seeded file(s) will be available to everybody. While downloading pieces are always available to other peers in the swarm.");
    private final TorrentSeedingSettingComponent COMPONENT;

    public TorrentSeedingSettingPaneItem() {
        super(TITLE, LABEL);
        COMPONENT = new TorrentSeedingSettingComponent(true, false);
        add(COMPONENT);
    }

    @Override
    public boolean isDirty() {
        // nothing the component does it.
        return false;
    }

    @Override
    public void initOptions() {
        // nothing the component does it.
    }

    @Override
    public boolean applyOptions() {
        SharingSettings.SEED_FINISHED_TORRENTS.setValue(COMPONENT.wantsSeeding());
        if (!COMPONENT.wantsSeeding()) {
            BTDownloadMediator.instance().stopCompleted();
        }
        GUIMediator.instance().getStatusLine().refresh();
        return false;
    }
}
