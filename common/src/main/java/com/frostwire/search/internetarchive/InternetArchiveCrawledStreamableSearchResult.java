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

package com.frostwire.search.internetarchive;

import com.frostwire.search.StreamableSearchResult;

/**
 * @author gubatron
 * @author aldenml
 */
public class InternetArchiveCrawledStreamableSearchResult extends InternetArchiveCrawledSearchResult implements StreamableSearchResult {
    public InternetArchiveCrawledStreamableSearchResult(InternetArchiveSearchResult sr, InternetArchiveFile file) {
        super(sr, file);
    }

    @Override
    public String getStreamUrl() {
        return getDownloadUrl();
    }
}
