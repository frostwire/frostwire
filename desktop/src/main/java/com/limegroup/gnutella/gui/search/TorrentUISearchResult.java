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

package com.limegroup.gnutella.gui.search;

import com.frostwire.search.torrent.TorrentSearchResult;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.VPNDropGuard;
import com.limegroup.gnutella.gui.util.PopupUtils;

import javax.swing.*;

/**
 * @author gubatron
 * @author aldenml
 */
public class TorrentUISearchResult extends AbstractUISearchResult {
    private final TorrentSearchResult sr;

    TorrentUISearchResult(TorrentSearchResult sr, SearchEngine se, String query) {
        super(sr, se, query);
        this.sr = sr;
    }

    public String getTorrentUrl() {
        return sr.getTorrentUrl();
    }

    @Override
    public String getHash() {
        return sr.getHash();
    }

    @Override
    public void download(boolean partial) {
        // Check VPN-Drop protection before initiating torrent download
        if (!VPNDropGuard.canUseBitTorrent()) {
            return;
        }
        GUIMediator gm = GUIMediator.instance();
        gm.openTorrentSearchResult(sr, partial);
        showSearchResultWebPage(false);
    }

    @Override
    public JPopupMenu createMenu(JPopupMenu popupMenu, SearchResultDataLine[] lines, SearchResultMediator resultPanel) {
        PopupUtils.addMenuItem(SearchMediator.DOWNLOAD_STRING, e -> download(false), popupMenu, lines.length > 0, 1);
        PopupUtils.addMenuItem(SearchMediator.DOWNLOAD_PARTIAL_FILES_STRING, resultPanel.DOWNLOAD_PARTIAL_FILES_LISTENER, popupMenu, lines.length == 1, 2);
        
        // Only add "Torrent Details" menu item if details URL is available
        String detailsUrl = getDetailsUrl();
        boolean hasDetailsUrl = detailsUrl != null && !detailsUrl.trim().isEmpty();
        PopupUtils.addMenuItem(SearchMediator.TORRENT_DETAILS_STRING, e -> showSearchResultWebPage(true), popupMenu, lines.length == 1 && hasDetailsUrl, 3);
        
        return popupMenu;
    }

    @Override
    public int getSeeds() {
        return sr.getSeeds();
    }
}
