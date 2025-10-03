/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml), Himanshu Sharma (HimanshuSharma789)
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

package com.frostwire.search.idope;

import com.frostwire.regex.Pattern;
import com.frostwire.search.torrent.SimpleTorrentSearchPerformer;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * FrostWire's idope search performer.
 * <p>
 * This search performer uses the same API that idope uses to construct search results. It is much simpler than parsing
 * an HTML search page of results (which now isn't possible without using JavaScript). Usage of this API is subject to
 * change without notice, so it will likely require updating whenever idope changes something (assuming they do so in
 * the first place). At least, however, it'll be easier to work with.
 */
public class IdopeSearchPerformer extends SimpleTorrentSearchPerformer {
    /**
     * An idope search result, as represented in FrostWire.
     * <p>
     * idope's internal search API sends a large JSON array of objects. We retrieve this array in the performer and then
     * parse it into an instance of this class, which contains the data we want.
     * <p>
     * Each search result contains the following:
     * - Name
     * - ID (used for constructing the details page in IdopeSearchResult)
     * - Size (in bytes)
     * - Date added
     * - Hash (used for constructing the magnet link)
     * - Number of seeders
     */
    private static class Result {
        public int id;           // Used for the information page
        public String name;      // The name of the torrent
        public String info_hash; // idope's hash of the torrent (used for getting the torrent's details page)
        public int seeders;      // How many seeders are currently seeding the torrent
        public long size;        // The torrent's size (in bytes)
        public long added;       // How old the torrent is
    }

    private static final Logger LOG = Logger.getLogger(IdopeSearchPerformer.class);
    private static Pattern pattern;

    public IdopeSearchPerformer(long token, String keywords, int timeout) {
        super("idope.hair", token, keywords, timeout, 1, 0);
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/api.php?url=/q.php?cat=0&q=" + encodedKeywords;
    }

    @Override
    protected List<? extends IdopeSearchResult> searchPage(String page) {
        if (null == page || page.isEmpty()) {
            stopped = true;
            return Collections.emptyList();
        }

        Result[] responseResults = JsonUtils.toObject(page, Result[].class);
        ArrayList<IdopeSearchResult> results = new ArrayList<>(0);

        for (Result result : responseResults) {
            results.add(
                new IdopeSearchResult(
                        result.id,
                        result.info_hash,
                        result.name,
                        result.size,
                        result.added,
                        result.seeders
                )
            );
        }

        return results;
    }

    @Override
    public boolean isCrawler() {
        return false;
    }
}
