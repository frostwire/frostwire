/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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

package com.frostwire.search.torrentparadise;

import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.UrlUtils;

public class TorrentParadiseSearchResult extends AbstractTorrentSearchResult {

    // no need to copy each property here to each search result, reuse object reference
    private final TorrentParadiseSearchPerformer.TPSearchResult tpSearchResult;
    private final String magnetUrl;

    public TorrentParadiseSearchResult(TorrentParadiseSearchPerformer.TPSearchResult paramTPSearchResult) {
        tpSearchResult = paramTPSearchResult;
        magnetUrl = UrlUtils.buildMagnetUrl(tpSearchResult.id, tpSearchResult.text, UrlUtils.USUAL_TORRENT_TRACKERS_MAGNET_URL_PARAMETERS);
    }

    @Override
    public String getTorrentUrl() {
        return magnetUrl;
    }

    @Override
    public int getSeeds() {
        return tpSearchResult.s;
    }

    @Override
    public String getHash() {
        return tpSearchResult.id;
    }

    @Override
    public String getFilename() {
        return tpSearchResult.text;
    }

    @Override
    public double getSize() {
        return tpSearchResult.len;
    }

    @Override
    public String getDisplayName() {
        return tpSearchResult.text;
    }

    @Override
    public String getDetailsUrl() {
        return "https://torrent-paradise.ml/";
    }

    @Override
    public String getSource() {
        return "TorrentParadise";
    }
}
