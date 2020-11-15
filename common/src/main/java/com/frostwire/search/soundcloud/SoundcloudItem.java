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

package com.frostwire.search.soundcloud;

/**
 * @author gubatron
 * @author aldenml
 */
final class SoundcloudItem {
    public int id;
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
