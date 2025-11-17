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

import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchResult;
import com.frostwire.search.StreamableSearchResult;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.telluride.TellurideSearchResult;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import com.limegroup.gnutella.settings.SearchSettings;
import org.apache.commons.io.FilenameUtils;

import java.awt.*;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractUISearchResult implements UISearchResult {
    private final FileSearchResult sr;
    private final SearchEngine se;
    private final String query;
    private final String extension;

    AbstractUISearchResult(FileSearchResult sr, SearchEngine se, String query) {
        this.sr = sr;
        this.se = se;
        this.query = query;
        this.extension = FilenameUtils.getExtension(sr.getFilename());
    }

    @Override
    public long getCreationTime() {
        return sr.getCreationTime();
    }

    @Override
    public String getFilename() {
        return sr.getFilename();
    }

    @Override
    public double getSize() {
        return sr.getSize();
    }

    @Override
    public String getSource() {
        return sr.getSource();
    }

    @Override
    public SearchEngine getSearchEngine() {
        return se;
    }

    @Override
    public String getDisplayName() {
        return sr.getDisplayName();
    }

    @Override
    public SearchResult getSearchResult() {
        return sr;
    }

    @Override
    public String getExtension() {
        return extension;
    }

    @Override
    public String getDetailsUrl() {
        return sr.getDetailsUrl();
    }

    @Override
    public void showSearchResultWebPage(boolean now) {
        if (now) {
            GUIMediator.openURL(getSearchResult().getDetailsUrl());
        } else {
            if (SearchSettings.SHOW_DETAIL_PAGE_AFTER_DOWNLOAD_START.getValue()) {
                GUIMediator.openURL(getSearchResult().getDetailsUrl(), SearchSettings.SHOW_DETAILS_DELAY);
            }
        }
    }

    @Override
    public String getQuery() {
        return query;
    }

    @Override
    public void play() {
        // this gets invoked when clicking on a search result play preview button.
        if (sr instanceof StreamableSearchResult ssr) {
            if (EventQueue.isDispatchThread() && sr instanceof SoundcloudSearchResult scsr) {
                if (scsr.fetchedDownloadUrl()) {
                    playStream(ssr.getStreamUrl());
                } else {
                    BackgroundQueuedExecutorService.schedule(() -> {
                        String url = ssr.getStreamUrl();
                        GUIMediator.safeInvokeLater(() -> playStream(url));
                    });
                }
            } else {
                playStream(ssr.getStreamUrl());
            }
        } else if (sr instanceof TellurideSearchResult) {
            playStream(((TellurideSearchResult) sr).getDownloadUrl());
        } else if (sr.isPreliminary()) {
            // Preliminary results (YouTube, etc.) show web page for selection
            showSearchResultWebPage(true);
        }
    }

    private void playStream(String streamUrl) {
        // Stream playback - open in browser
        GUIMediator.openURL(streamUrl);
    }
}
