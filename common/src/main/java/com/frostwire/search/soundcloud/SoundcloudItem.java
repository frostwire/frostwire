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

    boolean isValidSearchResult() {
        return isValidSearchResult(false);
    }

    boolean isValidSearchResult(boolean fromPastedUrl) {
        if (fromPastedUrl) {
            return hasProgressiveFormat();
        }
        return downloadable && hasProgressiveFormat();
    }

    boolean hasProgressiveFormat() {
        if (media == null) {
            return false;
        }

        for (SoundcloudTranscodings transcodings : media.transcodings) {
            if ("progressive".equals(transcodings.format.protocol)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns URL that fetches JSON with actual stream URL. You need to concatenate the client_id=XXXX to this URL
     * to obtain a valid JSON response.
     */
    String getProgressiveFormatJSONFetcherURL() {
        if (media == null) {
            return null;
        }
        for (SoundcloudTranscodings transcodings : media.transcodings) {
            if ("progressive".equals(transcodings.format.protocol)) {
                return transcodings.url;
            }
        }
        return null;
    }
}
