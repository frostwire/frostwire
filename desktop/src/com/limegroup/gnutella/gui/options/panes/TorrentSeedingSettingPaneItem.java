/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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
