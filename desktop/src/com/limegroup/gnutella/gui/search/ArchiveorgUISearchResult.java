/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.search;

import com.frostwire.gui.tabs.TransfersTab;
import com.frostwire.search.archiveorg.ArchiveorgCrawledSearchResult;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.util.PopupUtils;

import javax.swing.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class ArchiveorgUISearchResult extends AbstractUISearchResult {
    private final ArchiveorgCrawledSearchResult sr;

    ArchiveorgUISearchResult(ArchiveorgCrawledSearchResult sr, SearchEngine se, String query) {
        super(sr, se, query);
        this.sr = sr;
    }

    @Override
    public void download(boolean partial) {
        GUIMediator.instance().showTransfers(TransfersTab.FilterMode.ALL);
        GUIMediator.instance().openHttp(sr.getDownloadUrl(), sr.getDisplayName(), sr.getFilename(), sr.getSize());
        showSearchResultWebPage(false);
    }

    @Override
    public JPopupMenu createMenu(JPopupMenu popupMenu, SearchResultDataLine[] lines, SearchResultMediator rp) {
        PopupUtils.addMenuItem(SearchMediator.DOWNLOAD_STRING, e -> download(false), popupMenu, lines.length > 0, 1);
        PopupUtils.addMenuItem(SearchMediator.ARCHIVEORG_DETAILS_STRING, e -> showSearchResultWebPage(true), popupMenu, lines.length == 1, 2);
        return popupMenu;
    }

    @Override
    public String getHash() {
        return null;
    }

    @Override
    public int getSeeds() {
        return 200;
    }
}
