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

package com.frostwire.search.soundcloud;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import com.frostwire.search.AbstractFileSearchResult;
import com.frostwire.search.HttpSearchResult;
import com.frostwire.search.StreamableSearchResult;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class SoundcloudSearchResult extends AbstractFileSearchResult implements HttpSearchResult, StreamableSearchResult {

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
    private final SoundcloudItem item;

    public SoundcloudSearchResult(SoundcloudItem item, String clientId, String appVersion) {
        this.item = item;
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
        System.out.println("SoundCloudSearchResult().downloadUrl => " + this.downloadUrl);
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

    public SoundcloudItem getSoundcloudItem() {
        return item;
    }

    private String buildUsername(SoundcloudItem item) {
        if (item.user != null && item.user.username != null) {
            return item.user.username;
        } else {
            return "";
        }
    }

    private long buildSize(SoundcloudItem item) {
        return ((item.download_url != null) ? item.original_content_size : (int) (0.30 * ((float) item.original_content_size)));
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
        } catch (ParseException e) {
            return System.currentTimeMillis();
        }
    }

    private String buildDownloadUrl(SoundcloudItem item, String clientId, String appVersion) {
        final String clientAppenderChar = (item.download_url != null && item.download_url.contains("?")) ? "&" : "?";
        String downloadUrl = ((item.download_url != null) ? item.download_url : item.stream_url);

        //http://api.soundcloud.com/tracks/#########/download no longer works, has to be /stream now.
        if (downloadUrl.endsWith("/download")) {
            downloadUrl = downloadUrl.replace("/download","/stream");
        }

        // direct download urls don't seem to need client_id & app_version, if passed to the url returns HTTP 404.
        if (clientId != null && appVersion != null) {
            downloadUrl += clientAppenderChar + "client_id=" + clientId + "&app_version=" + appVersion;
        }

        return downloadUrl.replace("https://", "http://");
    }

    @Override
    public String toString() {
        return "SoundcloudSearchResult.downloadUrl: " + getDownloadUrl();
    }
}