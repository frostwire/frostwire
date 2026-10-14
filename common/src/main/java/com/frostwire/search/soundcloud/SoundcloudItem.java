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

package com.frostwire.search.soundcloud;

/**
 * @author gubatron
 * @author aldenml
 */
public class SoundcloudItem {
    public long id;
    public SoundcloudUser user;
    public String uri;
    public int duration;
    public String permalink;
    public String title;
    public String permalink_url;
    public String artwork_url;
    public String stream_url;
    public String created_at;
    public boolean downloadable;
    public String download_url;
    public SoundcloudMedia media;
    public String track_authorization;

    boolean isValidSearchResult() {
        return isValidSearchResult(false);
    }

    boolean isValidSearchResult(boolean fromPastedUrl) {
        return downloadable && getSupportedTranscoding() != null;
    }

    boolean hasProgressiveFormat() {
        return getProgressiveTranscoding() != null;
    }

    SoundcloudTranscodings getSupportedTranscoding() {
        SoundcloudTranscodings progressive = getProgressiveTranscoding();
        if (progressive != null) {
            return progressive;
        }
        if (media != null && media.transcodings != null) {
            for (SoundcloudTranscodings transcoding : media.transcodings) {
                if (isUsable(transcoding) && "hls".equals(transcoding.format.protocol)
                        && transcoding.format.mime_type != null
                        && "audio/mp4".equalsIgnoreCase(transcoding.format.mime_type.split(";", 2)[0].trim())) {
                    return transcoding;
                }
            }
        }
        return null;
    }

    private SoundcloudTranscodings getProgressiveTranscoding() {
        if (media != null && media.transcodings != null) {
            for (SoundcloudTranscodings transcoding : media.transcodings) {
                if (isUsable(transcoding) && "progressive".equals(transcoding.format.protocol)) {
                    return transcoding;
                }
            }
        }
        return null;
    }

    private boolean isUsable(SoundcloudTranscodings transcoding) {
        return transcoding != null && !transcoding.snipped && transcoding.format != null
                && transcoding.url != null && !transcoding.url.trim().isEmpty();
    }

    /**
     * Returns URL that fetches JSON with actual stream URL. You need to concatenate the client_id=XXXX to this URL
     * to obtain a valid JSON response.
     */
    String getProgressiveFormatJSONFetcherURL() {
        SoundcloudTranscodings progressive = getProgressiveTranscoding();
        return progressive != null ? progressive.url : null;
    }
}
