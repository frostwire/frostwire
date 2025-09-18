/*
 *     Created by Angel Leon (@gubatron)
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
