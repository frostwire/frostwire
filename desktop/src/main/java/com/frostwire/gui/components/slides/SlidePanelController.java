/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.components.slides;

import com.frostwire.util.MediaSource;
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
            // Stream playback - open in browser
            GUIMediator.openURL(mediaURL);
        }
    }
}
