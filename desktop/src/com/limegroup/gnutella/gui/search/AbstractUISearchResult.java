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

import com.frostwire.gui.player.StreamMediaSource;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchResult;
import com.frostwire.search.StreamableSearchResult;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.telluride.TellurideSearchResult;
import com.frostwire.search.yt.YTSearchResult;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.util.BackgroundExecutorService;
import com.limegroup.gnutella.settings.SearchSettings;
import org.apache.commons.io.FilenameUtils;

import javax.swing.*;

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
        if (sr instanceof StreamableSearchResult) {
            StreamableSearchResult ssr = (StreamableSearchResult) sr;

            String streamUrl;
            if (SwingUtilities.isEventDispatchThread() && sr instanceof SoundcloudSearchResult) {
                SoundcloudSearchResult scsr = (SoundcloudSearchResult) sr;
                if (scsr.fetchedDownloadUrl()) {
                    playStream(ssr.getStreamUrl());
                } else {
                    BackgroundExecutorService.schedule(() -> {
                        String url = ssr.getStreamUrl();
                        GUIMediator.safeInvokeLater(() -> playStream(url));
                    });
                }
            } else {
                playStream(ssr.getStreamUrl());
            }
        } else if (sr instanceof TellurideSearchResult) {
            playStream(((TellurideSearchResult) sr).getDownloadUrl());
        } else if (sr instanceof YTSearchResult) {
            showSearchResultWebPage(true);
        }
    }

    private void playStream(String streamUrl) {
        MediaType mediaType = MediaType.getMediaTypeForExtension(extension);
        if (mediaType != null) {
            boolean isVideo = mediaType.equals(MediaType.getVideoMediaType());
            if (isVideo) {
                GUIMediator.instance().launchMedia(new StreamMediaSource(streamUrl, sr.getDisplayName(), sr.getDetailsUrl(), true), true);
            } else {
                GUIMediator.instance().launchMedia(new StreamMediaSource(streamUrl, sr.getDisplayName(), sr.getDetailsUrl(), false), true);
            }
        }
    }
}