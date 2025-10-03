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

package com.frostwire.search.magnetdl;

import com.frostwire.regex.Pattern;
import com.frostwire.search.torrent.SimpleTorrentSearchPerformer;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * @author gubatron
 */
public class MagnetDLSearchPerformer extends SimpleTorrentSearchPerformer {
    /**
     * A MagnetDL search result, as represented in FrostWire.
     * <p>
     * MagnetDL's internal search API sends a large JSON array of objects. We retrieve this array in the performer and
     * then parse it into an instance of this class, which contains the data we want.
     * <p>
     * Each search result contains the following:
     * - Name
     * - ID (used for constructing the details page in MagnetDLSearchResult)
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

    private static Logger LOG = Logger.getLogger(MagnetDLSearchPerformer.class);
    private static Pattern pattern;
    private final String nonEncodedKeywords;
    private int minSeeds = 1;

    public MagnetDLSearchPerformer(long token, String keywords, int timeout) {
        super("magnetdl.homes", token, keywords, timeout, 1, 0);
        nonEncodedKeywords = keywords;
    }

    public void setMinSeeds(int minSeeds) {
        this.minSeeds = minSeeds;
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        //disregard encoded keywords when it comes to the URL
        String transformedKeywords = nonEncodedKeywords.replace("%20", "-");
        return "https://" + getDomainName() + "/api.php?url=/q.php?q=" + transformedKeywords;
    }

    @Override
    protected List<MagnetDLSearchResult> searchPage(String page) {
        Result[] searchResults = JsonUtils.toObject(page, Result[].class);
        ArrayList<MagnetDLSearchResult> results = new ArrayList<>(0);

        for (Result result : searchResults) {
            results.add(
                new MagnetDLSearchResult(
                    result.id,
                    result.name,
                    result.info_hash,
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
