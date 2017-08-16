/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.search.soundcloud;

import com.frostwire.search.AbstractFileSearchResult;
import com.frostwire.search.HttpSearchResult;
import com.frostwire.search.StreamableSearchResult;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SoundcloudSearchResult extends AbstractFileSearchResult implements HttpSearchResult, StreamableSearchResult {

    private static final String DATE_FORMAT = "yyyy/mm/dd HH:mm:ss Z";

    private final String displayName;
    private final String username;
    private final String trackUrl;
    private final String filename;
    private final String source;
    private final String thumbnailUrl;
    private final long date;
    private final String downloadUrl;
    private final long size;

    SoundcloudSearchResult(SoundcloudItem item, String clientId, String appVersion) {
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
        this.downloadUrl = buildDownloadUrl(item, clientId, appVersion);
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
        return downloadUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public String getDownloadUrl() {
        return downloadUrl;
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
        return (x * y) / 8;
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
        try {
            return new SimpleDateFormat(DATE_FORMAT, Locale.US).parse(str).getTime();
        } catch (Throwable e) {
            return System.currentTimeMillis();
        }
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
        if (o == null || !(o instanceof SoundcloudSearchResult)) {
            return false;
        }
        SoundcloudSearchResult other = (SoundcloudSearchResult) o;
        return this.getDetailsUrl().equals(other.getDetailsUrl()) &&
                this.getDisplayName().equals(other.getDisplayName()) &&
                this.getDownloadUrl().equals(other.getDownloadUrl());
    }

    @Override
    public int hashCode() {
        return getDetailsUrl().hashCode() +
                getDisplayName().hashCode() +
                getDownloadUrl().hashCode();
    }
}
