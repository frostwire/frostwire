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

package com.frostwire.gui.components.slides;

import com.frostwire.gui.player.StreamMediaSource;
import com.limegroup.gnutella.gui.GUIMediator;
import org.limewire.util.StringUtils;

/**
 * @author gubatron
 * @author aldenml
 */
class SlidePanelController {
    private final Slide slide;

    SlidePanelController(Slide slide) {
        this.slide = slide;
    }

    public Slide getSlide() {
        return slide;
    }

    void downloadSlide() {
        switch (slide.method) {
            case Slide.SLIDE_DOWNLOAD_METHOD_HTTP:
                if (slide.httpDownloadURL != null) {
                    GUIMediator.instance().openSlide(slide);
                }
                break;
            case Slide.SLIDE_DOWNLOAD_METHOD_TORRENT:
                if (slide.torrent != null) {
                    if (slide.torrent.toLowerCase().startsWith("http")) {
                        GUIMediator.instance().openTorrentURI(slide.torrent, false);
                    } else if (slide.torrent.toLowerCase().startsWith("magnet:?")) {
                        GUIMediator.instance().openTorrentURI(slide.torrent, false);
                    }
                }
                break;
        }
        if (slide.hasFlag(Slide.OPEN_CLICK_URL_ON_DOWNLOAD) && slide.clickURL != null) {
            GUIMediator.openURL(slide.clickURL);
        }
    }

    /**
     * Note: only for HTTP downloads
     */
    void installSlide() {
        if (slide.method == Slide.SLIDE_DOWNLOAD_METHOD_HTTP && slide.hasFlag(Slide.POST_DOWNLOAD_EXECUTE)) {
            downloadSlide();
        }
    }

    void previewVideo() {
        final String mediaURL = slide.videoURL;
        if (mediaURL != null && mediaURL.contains("youtube.com")) {
            GUIMediator.openURL(slide.videoURL);
        } else {
            previewMedia(mediaURL, true, Slide.PREVIEW_VIDEO_USING_FWPLAYER);
        }
    }

    void previewAudio() {
        if (slide.hasFlag(Slide.PREVIEW_AUDIO_USING_BROWSER)) {
            GUIMediator.openURL(slide.audioURL);
        } else {
            previewMedia(slide.audioURL, false, Slide.PREVIEW_AUDIO_USING_FWPLAYER);
        }
    }

    private void previewMedia(String mediaURL, boolean showMediaPlayer, int flagUsingFWPlayerForMediaType) {
        if (!StringUtils.isNullOrEmpty(mediaURL)) {
            StreamMediaSource mediaSource = new StreamMediaSource(mediaURL, slide.title, slide.clickURL, showMediaPlayer);
            if (slide.hasFlag(flagUsingFWPlayerForMediaType)) {
                GUIMediator.instance().launchMedia(mediaSource, true);
            } else {
                GUIMediator.instance().playInOS(mediaSource);
            }
        }
    }
}
