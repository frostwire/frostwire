/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.search.btdigg;

import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.HtmlManipulator;
import com.frostwire.util.UrlUtils;

public class BTDiggSearchResult extends AbstractTorrentSearchResult {
    private final String detailsUrl;
    private final String displayName;
    private final String torrentUrl;
    private final String filename;
    private final String infoHash;
    private final int seeds;
    private final long size;

    BTDiggSearchResult(String domainName, SearchMatcher matcher) {
        this.detailsUrl = matcher.group("detailUrl");
        this.displayName = HtmlManipulator.replaceHtmlEntities(matcher.group("displayName")).trim().replaceAll("\\<.*?\\>", "");
        this.torrentUrl = matcher.group("magnet");
        this.filename = displayName + ".torrent";
        this.infoHash = UrlUtils.extractInfoHash(torrentUrl);
        this.seeds = 500;
        // sizes are not separated by " " but instead by char 160.
        this.size = parseSize(matcher.group("size").replace((char)160, ' '));
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
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDetailsUrl() {
        return detailsUrl;
    }

    @Override
    public String getSource() {
        return "BTDigg";
    }

    @Override
    public String getTorrentUrl() {
        return torrentUrl;
    }

    @Override
    public int getSeeds() {
        return seeds;
    }

    @Override
    public String getHash() {
        return infoHash;
    }
}
