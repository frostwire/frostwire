/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui.search;

import com.frostwire.gui.tabs.TransfersTab;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.util.BackgroundExecutorService;
import com.limegroup.gnutella.gui.util.PopupUtils;

import javax.swing.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SoundcloudUISearchResult extends AbstractUISearchResult {
    private final SoundcloudSearchResult sr;

    SoundcloudUISearchResult(SoundcloudSearchResult sr, SearchEngine se, String query) {
        super(sr, se, query);
        this.sr = sr;
    }

    @Override
    public void download(boolean partial) {
        BackgroundExecutorService.schedule(() -> {
            sr.getDownloadUrl();
            GUIMediator.instance().safeInvokeLater(() -> {
                GUIMediator.instance().showTransfers(TransfersTab.FilterMode.ALL);
                GUIMediator.instance().openSoundcloudTrackUrl(sr.getDetailsUrl(), sr, false);
                showSearchResultWebPage(false);
            });
        });
    }

    @Override
    public JPopupMenu createMenu(JPopupMenu popupMenu, SearchResultDataLine[] lines, SearchResultMediator rp) {
        PopupUtils.addMenuItem(SearchMediator.DOWNLOAD_STRING, e -> download(false), popupMenu, lines.length > 0, 1);
        PopupUtils.addMenuItem(SearchMediator.SOUNDCLOUD_DETAILS_STRING, e -> showSearchResultWebPage(true), popupMenu, lines.length == 1, 2);
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
