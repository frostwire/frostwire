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

package com.frostwire.search.torlock;

import com.frostwire.search.AbstractSearchResult;
import com.frostwire.search.CrawlableSearchResult;

/**
 * @author gubatron
 * @author aldenml
 */
public class TorLockTempSearchResult extends AbstractSearchResult implements CrawlableSearchResult {
    private final String itemId;
    private final String detailsUrl;

    public TorLockTempSearchResult(String domainName, String itemId) {
        this.itemId = itemId;
        // Try to construct a reasonable details URL for the new site structure
        // This may need adjustment based on the actual new site structure
        this.detailsUrl = "https://" + domainName + "/torrent/" + itemId;
    }

    public String getItemId() {
        return itemId;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getDetailsUrl() {
        return detailsUrl;
    }

    @Override
    public String getSource() {
        return "TorLock";
    }

    @Override
    public boolean isComplete() {
        return false;
    }
}
