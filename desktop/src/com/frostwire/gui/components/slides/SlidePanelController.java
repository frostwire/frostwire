/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.components.slides;

import java.io.File;

import org.limewire.util.StringUtils;

import com.frostwire.gui.player.MediaSource;
import com.frostwire.gui.player.StreamMediaSource;
import com.frostwire.logging.Logger;
import com.limegroup.gnutella.gui.GUIMediator;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
class SlidePanelController {

    private static Logger LOG = Logger.getLogger(SlidePanelController.class);

    private Slide slide;

    private String cachedVideoStreamURL;

    public SlidePanelController(Slide slide) {
        this.slide = slide;
    }

    public Slide getSlide() {
        return slide;
    }

    public void downloadSlide() {

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
    public void installSlide() {
        if (slide.method == Slide.SLIDE_DOWNLOAD_METHOD_HTTP && slide.hasFlag(Slide.POST_DOWNLOAD_EXECUTE)) {
            downloadSlide();
        }
    }

    private void playInOS(MediaSource source) {
        if (source == null) {
            return;
        }

        if (source.getFile() != null) {
            GUIMediator.launchFile(source.getFile());
        } else if (source.getPlaylistItem() != null) {
            GUIMediator.launchFile(new File(source.getPlaylistItem().getFilePath()));
        } else if (source.getURL() != null) {
            GUIMediator.openURL(source.getURL());
        }
    }

    public void previewVideo() {
        final String mediaURL = slide.videoURL;
        if (mediaURL != null && mediaURL.contains("youtube.com")) {
            GUIMediator.openURL(slide.videoURL);
            /*
            if (slide.hasFlag(Slide.PREVIEW_VIDEO_USING_BROWSER)) {
                GUIMediator.openURL(slide.videoURL);
            } else {
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            if (cachedVideoStreamURL == null) {
                                cachedVideoStreamURL = new YouTubeStreamURLExtractor(mediaURL).getYoutubeStreamURL();
                            }
                            previewMedia(cachedVideoStreamURL, true, Slide.PREVIEW_VIDEO_USING_FWPLAYER);
                        } catch (Exception e) {
                            LOG.error("Could not extract/play youtube stream.", e);
                        }
                    }
                }.start();
            }*/
        } else {
            previewMedia(mediaURL, true, Slide.PREVIEW_VIDEO_USING_FWPLAYER);
        }
    }

    public void previewAudio() {
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
                playInOS(mediaSource);
            }
        }
    }
}
