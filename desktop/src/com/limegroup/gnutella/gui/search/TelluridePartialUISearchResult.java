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

import com.frostwire.search.AbstractFileSearchResult;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.util.PopupUtils;

import javax.swing.*;

public class TelluridePartialUISearchResult<S extends AbstractFileSearchResult> extends AbstractUISearchResult {
    public TelluridePartialUISearchResult(S sr, SearchEngine se, String query) {
        super(sr, se, query);
    }

    @Override
    public void download(boolean partial) {
        SearchInformation searchInformation =
                SearchInformation.createTitledKeywordSearch(
                        getSearchResult().getDetailsUrl(),
                        null,
                        MediaType.getVideoMediaType(),
                        "yt:" + getQuery()); //title
        SearchMediator.instance().triggerSearch(searchInformation);
    }

    @Override
    public JPopupMenu createMenu(JPopupMenu popupMenu, SearchResultDataLine[] lines, SearchResultMediator rp) {
        PopupUtils.addMenuItem(SearchMediator.DOWNLOAD_STRING, e -> download(false), popupMenu, lines.length > 0, 2);
        PopupUtils.addMenuItem(SearchMediator.TELLURIDE_DETAILS_STRING + " " + getSearchResult().getSource(), e -> showSearchResultWebPage(true), popupMenu, lines.length == 1, 3);
        return popupMenu;
    }

    @Override
    public String getHash() {
        return null;
    }

    @Override
    public int getSeeds() {
        return ((S) getSearchResult()).getViewCount();
    }
}
