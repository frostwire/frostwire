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

package com.limegroup.gnutella.gui.search;

import com.frostwire.search.torrent.TorrentSearchResult;
import com.limegroup.gnutella.gui.GUIMediator;
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
