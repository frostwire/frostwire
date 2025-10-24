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

import com.frostwire.gui.tabs.TransfersTab;
import com.frostwire.search.telluride.TellurideSearchResult;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.util.PopupUtils;

import javax.swing.*;

public class TellurideUISearchResult extends AbstractUISearchResult {

    private final boolean extractAudio;

    public TellurideUISearchResult(TellurideSearchResult sr, SearchEngine se, String query, boolean extractAudio) {
        super(sr, se, query);
        this.extractAudio = extractAudio;
    }

    @Override
    public void download(boolean partial) {
        TellurideSearchResult sr = (TellurideSearchResult) getSearchResult();
        GUIMediator.instance().showTransfers(TransfersTab.FilterMode.ALL);
        GUIMediator.instance().openHttp(sr.getDownloadUrl(), sr.getDisplayName(), sr.getFilename(), sr.getSize(), extractAudio);
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
