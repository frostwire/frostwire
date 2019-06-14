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

import com.frostwire.bittorrent.PaymentOptions;

/**
 * @author gubatron
 * @author aldenml
 */
public class Slide {
    /**
     * Just Open The URL
     */
    public static final int SLIDE_DOWNLOAD_METHOD_OPEN_URL = 0;
    @SuppressWarnings("unused")
    public static final int POST_DOWNLOAD_UNZIP = 1;
    @SuppressWarnings("unused")
    public static final int POST_DOWNLOAD_DELETE_ZIP_FILE = 1 << 1;
    @SuppressWarnings("unused")
    public static final int PREVIEW_VIDEO_USING_BROWSER = 1 << 6;
    public static final int OPEN_CLICK_URL_ON_DOWNLOAD = 1 << 9;
    @SuppressWarnings("unused")
    public static final int IS_ADVERTISEMENT = 1 << 11;
    /**
     * Download using the torrent URL
     */
    static final int SLIDE_DOWNLOAD_METHOD_TORRENT = 1;
    /**
     * Download via HTTP
     */
    static final int SLIDE_DOWNLOAD_METHOD_HTTP = 2;
    static final int POST_DOWNLOAD_EXECUTE = 1 << 2;
    static final int PREVIEW_AUDIO_USING_FWPLAYER = 1 << 3;
    static final int PREVIEW_AUDIO_USING_BROWSER = 1 << 4;
    static final int PREVIEW_VIDEO_USING_FWPLAYER = 1 << 5;
    static final int SHOW_AUDIO_PREVIEW_BUTTON = 1 << 7;
    static final int SHOW_VIDEO_PREVIEW_BUTTON = 1 << 8;
    static final int SHOW_PREVIEW_BUTTONS_ON_THE_LEFT = 1 << 10;
    /**
     * url of torrent file that should be opened if user clicks on this slide
     */
    public final String torrent;
    /**
     *
     */
    public final String httpDownloadURL;
    /**
     * length of time this slide will be shown
     */
    public final long duration;
    /**
     * language (optional filter) = Can be given in the forms of:
     * *
     * en
     * en_US
     */
    public final String language;
    /**
     * os (optional filter) = Can be given in the forms of comma separated:
     * windows
     * mac
     * linux
     * android
     */
    public final String os;
    /**
     * The Download title.
     */
    public final String title;
    /**
     * Content creator name
     */
    public final String author;
    /**
     * Download size in bytes.
     */
    public final long size;
    /**
     * decide what to do with this Slide onClick.
     */
    public final int method;
    /**
     * Optional MD5 hash
     */
    public final String md5;
    /**
     * If != null, rename file to this file name.
     */
    public final String saveFileAs;
    /**
     * Twitter page associated with slide
     */
    public final String twitter;
    /**
     * Use these flags to determine how the slide will behave.
     */
    public final int flags;
    public PaymentOptions paymentOptions;
    /**
     * http address where to go if user clicks on this slide
     */
    final String clickURL;
    /**
     * url of image that will be displayed on this slide
     */
    final String imageSrc;
    /**
     * Comma separated list of versions that should not use this
     */
    final String includedVersions;
    /**
     * audio file url so user can play preview/promotional audio for promo.
     */
    final String audioURL;
    /**
     * video file url so frostwire player can be opened, could be a youtube url, player
     * should default to high quality playback
     */
    final String videoURL;
    /**
     * Facebook page associated with slide
     */
    final String facebook;
    /**
     * Youtube channel
     */
    final String youtube;
    /**
     * Instagram feed
     */
    final String instagram;

    /**
     * @param imgSrc                 - slide overlay image url
     * @param clickUrl               - url where to take user on click (optional)
     * @param durationInMilliseconds - for how long to show the overlay before auto switching
     * @param torrentURL             - .torrent file (optional)
     * @param httpDownloadUrl        - an http url where to download the file from (check downloadMethod on how to proceed)
     * @param lang                   - language code in case you want to filter slides by language
     * @param OS                     - comma separated os names (windows,mac,linux,android)
     * @param theTitle               - the title of this download (useful for download manager and human presentation)
     * @param theAuthor              - content creator(s) name(s)
     * @param theSize                - size in bytes of this download
     * @param downloadMethod         - what to do with the slide.
     * @param md5hash                - optional, string with md5 hash of the finished http download
     * @param saveAs                 - optional, name of the file if downloaded via http
     * @param includeTheseVersions   - comma separated versions that are not supposed to see this slide.
     * @param audioPreviewURL        - HTTP URL of audio file so user can preview before download.
     * @param videoPreviewURL        - HTTP URL of video file (youtube maybe) so user can preview promo.
     * @param facebookURL            - optional, related Facebook page url
     * @param twitterURL             - optional, related Twitter page url
     * @param youtubeURL             - optional, youtube channel
     * @param instagramURL           - optional, Instagram feed
     * @param slideFlags             - these determine how the slide will behave
     */
    public Slide(String imgSrc, String clickUrl, long durationInMilliseconds, String torrentURL, String httpDownloadUrl, String lang, String OS, String theTitle, String theAuthor, long theSize, int downloadMethod, String md5hash, String saveAs,
                 String includeTheseVersions, String audioPreviewURL, String videoPreviewURL, String facebookURL, String twitterURL, String youtubeURL, String instagramURL, int slideFlags) {
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
        /* If != null && execute, pass these parameters to the finished downloaded file. */
        includedVersions = includeTheseVersions;
        audioURL = audioPreviewURL;
        videoURL = videoPreviewURL;
        facebook = facebookURL;
        twitter = twitterURL;
        youtube = youtubeURL;
        instagram = instagramURL;
        flags = slideFlags;
    }

    boolean hasFlag(int flag) {
        return (flags & flag) == flag;
    }
}