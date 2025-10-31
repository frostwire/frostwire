/*
 *     Created by Angel Leon (@gubatron)
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

import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.util.PopupUtils;
import com.limegroup.gnutella.gui.GUIMediator;

import javax.swing.*;

/**
 * UI wrapper for V2 CompositeFileSearchResult (e.g., YouTube videos, torrents, crawlable results).
 *
 * The wrapped CompositeFileSearchResult indicates via isPreliminary() whether it requires a secondary search.
 * This eliminates the need for instanceof checks in the UI layer.
 *
 * @author gubatron
 */
public final class FileSearchResultUIWrapper extends AbstractUISearchResult {
    private final CompositeFileSearchResult searchResult;

    public FileSearchResultUIWrapper(CompositeFileSearchResult sr, SearchEngine se, String query) {
        super(createFileSearchResultAdapter(sr), se, query);
        this.searchResult = sr;
    }

    private static FileSearchResult createFileSearchResultAdapter(CompositeFileSearchResult v2Result) {
        // Adapt V2 CompositeFileSearchResult to old FileSearchResult interface
        return new FileSearchResult() {
            @Override
            public String getFilename() { return v2Result.getFilename(); }
            @Override
            public long getSize() { return v2Result.getSize(); }
            @Override
            public String getDisplayName() { return v2Result.getDisplayName(); }
            @Override
            public String getDetailsUrl() { return v2Result.getDetailsUrl(); }
            @Override
            public long getCreationTime() { return v2Result.getCreationTime(); }
            @Override
            public String getSource() { return v2Result.getSource(); }
            @Override
            public com.frostwire.licenses.License getLicense() { return v2Result.getLicense(); }
            @Override
            public String getThumbnailUrl() { return v2Result.getThumbnailUrl(); }
            @Override
            public boolean isPreliminary() { return v2Result.isPreliminary(); }
        };
    }

    @Override
    public void download(boolean partial) {
        if (searchResult.isPreliminary()) {
            // YouTube videos and other streaming content - route through Telluride
            SearchInformation searchInformation =
                    SearchInformation.createTitledKeywordSearch(
                            searchResult.getDetailsUrl(),
                            null,
                            MediaType.getVideoMediaType(),
                            searchResult.getDetailsUrl());
            SearchMediator.instance().triggerSearch(searchInformation);
        } else if (searchResult.isTorrent()) {
            // Torrents (1337X, etc) - open torrent dialog to select files
            // Pass partial=true to ensure file selection dialog appears
            TorrentSearchResult torrentAdapter = new TorrentSearchResult() {
                @Override
                public String getFilename() { return searchResult.getFilename(); }
                @Override
                public long getSize() { return searchResult.getSize(); }
                @Override
                public String getDisplayName() { return searchResult.getDisplayName(); }
                @Override
                public String getDetailsUrl() { return searchResult.getDetailsUrl(); }
                @Override
                public long getCreationTime() { return searchResult.getCreationTime(); }
                @Override
                public String getSource() { return searchResult.getSource(); }
                @Override
                public com.frostwire.licenses.License getLicense() { return searchResult.getLicense(); }
                @Override
                public String getThumbnailUrl() { return searchResult.getThumbnailUrl(); }
                @Override
                public boolean isPreliminary() { return searchResult.isPreliminary(); }
                @Override
                public String getTorrentUrl() { return searchResult.getTorrentUrl().orElse(null); }
                @Override
                public String getHash() { return searchResult.getTorrentHash().orElse(null); }
                @Override
                public int getSeeds() { return searchResult.getSeeds().orElse(0); }
                @Override
                public String getReferrerUrl() { return searchResult.getReferrerUrl().orElse(null); }
            };
            // Always use partial=true for torrents to show file selection dialog
            GUIMediator.instance().openTorrentSearchResult(torrentAdapter, true);
        }
    }

    @Override
    public JPopupMenu createMenu(JPopupMenu popupMenu, SearchResultDataLine[] lines, SearchResultMediator rp) {
        PopupUtils.addMenuItem(SearchMediator.DOWNLOAD_STRING, e -> download(false), popupMenu, lines.length > 0, 2);
        PopupUtils.addMenuItem(
                SearchMediator.TELLURIDE_DETAILS_STRING + " " + searchResult.getSource(),
                e -> showSearchResultWebPage(true),
                popupMenu,
                lines.length == 1,
                3
        );
        return popupMenu;
    }

    @Override
    public String getHash() {
        // For YouTube/streaming results, no hash available
        return null;
    }

    @Override
    public int getSeeds() {
        // For YouTube/streaming results, use view count
        return searchResult.getViewCount().orElse(1000);
    }
}
