/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
 *
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
