/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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

import com.frostwire.gui.tabs.TransfersTab;
import com.frostwire.search.telluride.TellurideSearchResult;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.util.PopupUtils;

import javax.swing.*;

public class TellurideUISearchResult extends AbstractUISearchResult {

    public TellurideUISearchResult(TellurideSearchResult sr, SearchEngine se, String query) {
        super(sr, se, query);
    }

    @Override
    public void download(boolean partial) {
        TellurideSearchResult sr = (TellurideSearchResult) getSearchResult();
        GUIMediator.instance().showTransfers(TransfersTab.FilterMode.ALL);
        GUIMediator.instance().openHttp(sr.getDownloadUrl(), sr.getDisplayName(), sr.getFilename(), sr.getSize());
        showSearchResultWebPage(false);
    }

    @Override
    public JPopupMenu createMenu(JPopupMenu popupMenu, SearchResultDataLine[] lines, SearchResultMediator rp) {
        TellurideSearchResult sr = (TellurideSearchResult) getSearchResult();
        PopupUtils.addMenuItem(I18n.tr("Copy Download URL"), e -> GUIMediator.setClipboardContent(sr.getDownloadUrl()), popupMenu, true, 0);
        PopupUtils.addMenuItem(SearchMediator.DOWNLOAD_STRING, e -> download(false), popupMenu, lines.length > 0, 2);
        PopupUtils.addMenuItem(SearchMediator.TELLURIDE_DETAILS_STRING + " " + sr.getSource(), e -> showSearchResultWebPage(true), popupMenu, lines.length == 1, 3);
        return popupMenu;
    }

    @Override
    public String getHash() {
        return null;
    }

    @Override
    public int getSeeds() {
        return 250;
    }
}
