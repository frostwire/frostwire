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

import com.frostwire.bittorrent.PaymentOptions;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class Slide {

    /** Just Open The URL */
    public static final int SLIDE_DOWNLOAD_METHOD_OPEN_URL = 0;

    /** Download using the torrent URL */
    public static final int SLIDE_DOWNLOAD_METHOD_TORRENT = 1;

    /** Download via HTTP */
    public static final int SLIDE_DOWNLOAD_METHOD_HTTP = 2;

    public static final int POST_DOWNLOAD_UNZIP = 1;
    public static final int POST_DOWNLOAD_DELETE_ZIP_FILE = 1 << 1;
    public static final int POST_DOWNLOAD_EXECUTE = 1 << 2;
    public static final int PREVIEW_AUDIO_USING_FWPLAYER = 1 << 3;
    public static final int PREVIEW_AUDIO_USING_BROWSER = 1 << 4;
    public static final int PREVIEW_VIDEO_USING_FWPLAYER = 1 << 5;
    public static final int PREVIEW_VIDEO_USING_BROWSER = 1 << 6;
    public static final int SHOW_AUDIO_PREVIEW_BUTTON = 1 << 7;
    public static final int SHOW_VIDEO_PREVIEW_BUTTON = 1 << 8;
    public static final int OPEN_CLICK_URL_ON_DOWNLOAD = 1 << 9;
    public static final int SHOW_PREVIEW_BUTTONS_ON_THE_LEFT = 1 << 10;

    public Slide() {

    }

    /**
     * 
     * @param imgSrc - slide overlay image url
     * @param clickURL - url where to take user on click (optional)
     * @param durationInMilliseconds - for how long to show the overlay before autoswitching
     * @param torrentURL - .torrent file (optional)
     * @param httpDownloadURL - an http url where to download the file from (check downloadMethod on how to procede)
     * @param lang - language code in case you want to filter slides by language
     * @param OS - comma separated os names (windows,mac,linux,android)
     * @param theTitle - the title of this download (useful for download manager and human presentation)
     * @param author - content creator(s) name(s)
     * @param theSize - size in bytes of this download
     * @param downloadMethod - what to do with the slide.
     * @param md5hash - optional, string with md5 hash of the finished http download
     * @param saveAs - optional, name of the file if downloaded via http
     * @param executionParameters - parameters to pass to executable download
     * @param includeTheseVersions - comma separated versions that are not supposed to see this slide.
     * @param audioPreviewURL - HTTP URL of audio file so user can preview before download.
     * @param videoPreviewURL - HTTP URL of video file (youtube maybe) so user can preview promo.
     * @param facebookURL - optional, related Facebook page url
     * @param twitterURL - optional, related Twitter page url
     * @param gPlusURL - optional, related Google Plus page url
     * @param youtubeURL - optional, youtube channel
     * @param instagramURL - optional, instagram feed
     * @param flags - these determine how the slide will behave
     */
    public Slide(String imgSrc, String clickUrl, long durationInMilliseconds, String torrentURL, String httpDownloadUrl, String lang, String OS, String theTitle, String theAuthor, long theSize, int downloadMethod, String md5hash, String saveAs, String executionParameters,
            String includeTheseVersions, String audioPreviewURL, String videoPreviewURL, String facebookURL, String twitterURL, String gPlusURL, String youtubeURL, String instagramURL, int slideFlags) {
        imageSrc = imgSrc;
        clickURL = clickUrl;
        duration = durationInMilliseconds;
        torrent = torrentURL;
        httpDownloadURL = httpDownloadUrl;
        language = lang;
        os = OS;
        title = theTitle;
        author = theAuthor;
        size = theSize;
        method = downloadMethod;
        md5 = md5hash;
        saveFileAs = saveAs;
        executeParameters = executionParameters;
        includedVersions = includeTheseVersions;
        audioURL = audioPreviewURL;
        videoURL = videoPreviewURL;
        facebook = facebookURL;
        twitter = twitterURL;
        gplus = gPlusURL;
        youtube = youtubeURL;
        instagram = instagramURL;
        flags = slideFlags;
    }

    /**
     * http address where to go if user clicks on this slide
     */
    public String clickURL;

    /**
     * url of torrent file that should be opened if user clicks on this slide
     */
    public String torrent;

    /**
     * 
     */
    public String httpDownloadURL;

    /**
     * url of image that will be displayed on this slide
     */
    public String imageSrc;

    /**
     * length of time this slide will be shown
     */
    public long duration;

    /**
     * language (optional filter) = Can be given in the forms of:
     * *
     * en
     * en_US
     * 
     */
    public String language;

    /**
     * os (optional filter) = Can be given in the forms of commq separated:
     * windows
     * mac
     * linux
     * android
     */
    public String os;

    /**
     * The Download title.
     */
    public String title;

    /**
     * Content creator name
     */
    public String author;

    /**
     * Download size in bytes.
     */
    public long size;

    /**
     * decide what to do with this Slide onClick.
     */
    public int method;

    /** Optional MD5 hash */
    public String md5;

    /** If != null, rename file to this file name. */
    public String saveFileAs;

    /** If != null && execute, pass these parameters to the finished downloaded file. */
    public String executeParameters;

    /** Comma separated list of versions that should not use this */
    public String includedVersions;

    /** audio file url so user can play preview/promotional audio for promo. */
    public String audioURL;

    /** video file url so frostwire player can be opened, could be a youtube url, player
     * should default to high quality playback */
    public String videoURL;

    /** Facebook page associated with slide */
    public String facebook;

    /** Twitter page associated with slide */
    public String twitter;

    /** Google Plus page associated with slide */
    public String gplus;

    /** Youtube channel */
    public String youtube;

    /** Instagram feed */
    public String instagram;

    /** Use these flags to determine how the slide will behave. */
    public int flags;
    
    public PaymentOptions paymentOptions;

    public boolean hasFlag(int flag) {
        return (flags & flag) == flag;
    }
}