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

package com.frostwire.search.soundcloud;

import com.frostwire.mp3.*;
import com.frostwire.platform.Platforms;
import com.frostwire.search.AbstractFileSearchResult;
import com.frostwire.search.HttpSearchResult;
import com.frostwire.search.StreamableSearchResult;
import com.frostwire.util.DateParser;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.http.HttpClient;

import java.io.IOException;


/**
 * @author gubatron
 * @author aldenml
 */
public final class SoundcloudSearchResult extends AbstractFileSearchResult implements HttpSearchResult, StreamableSearchResult {
    private final String displayName;
    private final String username;
    private final String trackUrl;
    private final String filename;
    private final String source;
    private final String thumbnailUrl;
    private final long date;
    private final String downloadUrl;
    private final String progressiveFormatJSONFetcherURL;
    private final long size;
    private final String hash;

    public SoundcloudSearchResult(SoundcloudItem item, String clientId, String appVersion) {
        this.displayName = item.title;
        this.username = buildUsername(item);
        this.trackUrl = item.permalink_url;
        this.filename = item.permalink + "-soundcloud.mp3";
        this.size = buildSize(item);
        this.source = buildSource(item);
        String userAvatarUrl = null;
        if (item.user != null) {
            userAvatarUrl = item.user.avatar_url;
        }
        this.thumbnailUrl = buildThumbnailUrl(item.artwork_url != null ? item.artwork_url : userAvatarUrl);
        this.date = buildDate(item.created_at);
        if (null != item.getProgressiveFormatJSONFetcherURL()) {
            this.progressiveFormatJSONFetcherURL = item.getProgressiveFormatJSONFetcherURL() + "?client_id=" + clientId + "&app_version=" + appVersion;
        } else {
            this.progressiveFormatJSONFetcherURL = null;
        }
        this.hash = Long.toHexString(item.id * 953 * 631);
        this.downloadUrl = null;
    }

    public boolean fetchedDownloadUrl() {
        return downloadUrl != null;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public long getCreationTime() {
        return date;
    }

    @Override
    public String getSource() {
        return source;
    }

    @Override
    public String getDetailsUrl() {
        return trackUrl;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    public String getStreamUrl() {
        return getDownloadUrl();
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public String getDownloadUrl() {
        if (progressiveFormatJSONFetcherURL == null) {
            return null;
        }
        if (downloadUrl != null) {
            return downloadUrl;
        }

        if (Platforms.get().isUIThread()) {
            StackTraceElement[] stackTrace = new Exception().getStackTrace();
            int maxStackShow = 10;
            for (StackTraceElement e : stackTrace) {
                System.out.println(e.toString());
                if (--maxStackShow == 0) {
                    break;
                }
            }
            throw new RuntimeException("SoundcloudSearchResult.getDownloadUrl(): Do not invoke getDownloadUrl() from the main thread if downloadUrl is null");
        }

        HttpClient client = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD);
        try {
            String json = client.get(progressiveFormatJSONFetcherURL);
            SoundcloudTrackURL soundcloudTrackURL = JsonUtils.toObject(json, SoundcloudTrackURL.class);
            if (soundcloudTrackURL != null && soundcloudTrackURL.url != null) {
                return soundcloudTrackURL.url;
            }
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
        return null;
    }

    private String buildUsername(SoundcloudItem item) {
        if (item.user != null && item.user.username != null) {
            return item.user.username;
        } else {
            return "";
        }
    }

    private long buildSize(SoundcloudItem item) {
        // not bit optimized for clarity, compiler will do it
        int x = item.duration;
        int y = 128;
        return ((long) x * y) / 8;
    }

    private String buildSource(SoundcloudItem item) {
        if (item.user != null && item.user.username != null) {
            return "Soundcloud - " + item.user.username;
        } else {
            return "Soundcloud";
        }
    }

    private String buildThumbnailUrl(String str) {
        //http://i1.sndcdn.com/artworks-000019588274-le8r71-crop.jpg?be0edad
        //https://i1.sndcdn.com/artworks-000019588274-le8r71-t500x500.jpg
        //https://i1.sndcdn.com/avatars-000081692254-cxjo72-t500x500.jpg?2aaad5e
        String url = null;
        if (str != null) {
            try {
                url = str.substring(0, str.indexOf("-large.")) + "-t300x300.jpg";
            } catch (Throwable e) {
                // ignore
            }
        }
        return url;
    }

    private long buildDate(String str) {
        return DateParser.parseTorrentDate(str);
    }

    private String buildDownloadUrl(SoundcloudItem item, String clientId, String appVersion) {
        final String clientAppenderChar = (item.download_url != null && item.download_url.contains("?")) ? "&" : "?";
        String downloadUrl = ((item.download_url != null) ? item.download_url : item.stream_url);
        //http://api.soundcloud.com/tracks/#########/download no longer works, has to be /stream now.
        if (downloadUrl.endsWith("/download")) {
            downloadUrl = downloadUrl.replace("/download", "/stream");
        }
        // direct download urls don't seem to need client_id & app_version, if passed to the url returns HTTP 404.
        if (clientId != null && appVersion != null) {
            downloadUrl += clientAppenderChar + "client_id=" + clientId + "&app_version=" + appVersion;
        }
        return downloadUrl;
    }

    @Override
    public String toString() {
        return "SoundcloudSearchResult.getDisplayName(): " + getDisplayName();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SoundcloudSearchResult)) {
            return false;
        }
        SoundcloudSearchResult other = (SoundcloudSearchResult) o;
        return this.getDetailsUrl().equals(other.getDetailsUrl()) &&
                this.getDisplayName().equals(other.getDisplayName()) &&
                this.getHash().equals(other.getHash());
    }

    @Override
    public int hashCode() {
        return getHash().hashCode();
    }

    public String getHash() {
        return hash;
    }

    public static boolean prepareMP3File(String mp3Filename, String mp3outputFilename, byte[] imageBytes, SoundcloudSearchResult sr) throws IOException, UnsupportedTagException, InvalidDataException, NotSupportedException {
        Mp3File mp3 = new Mp3File(mp3Filename);
        ID3Wrapper newId3Wrapper = new ID3Wrapper(new ID3v1Tag(), new ID3v23Tag());
        newId3Wrapper.setAlbum(sr.getUsername() + ": " + sr.getDisplayName() + " via SoundCloud.com");
        newId3Wrapper.setArtist(sr.getUsername());
        newId3Wrapper.setTitle(sr.getDisplayName());
        newId3Wrapper.setAlbumImage(imageBytes, "image/jpg");
        newId3Wrapper.setUrl(sr.getDetailsUrl());
        newId3Wrapper.getId3v2Tag().setPadding(true);
        mp3.setId3v1Tag(newId3Wrapper.getId3v1Tag());
        mp3.setId3v2Tag(newId3Wrapper.getId3v2Tag());
        mp3.save(mp3outputFilename);
        return true;
    }    
}
