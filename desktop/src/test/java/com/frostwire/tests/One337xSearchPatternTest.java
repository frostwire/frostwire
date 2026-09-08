/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.tests;

import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.one337x.One337xSearchPattern;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public final class One337xSearchPatternTest {
    private static final String CAPTURED_SEARCH_HTML =
            "<table>" +
                    "<tr><td><a href=\"/torrent/12345/ubuntu-24-04-lts/\">" +
                    "Ubuntu <strong>24.04 LTS</strong></a></td></tr>" +
                    "<tr><td><a href=\"/torrent/67890/debian-12-netinst/\">" +
                    "Debian 12 netinst</a></td></tr>" +
                    "</table>";

    @Test
    public void one337xSearchTest() {
        One337xSearchPattern pattern = new One337xSearchPattern();
        List<FileSearchResult> results = pattern.parseResults(CAPTURED_SEARCH_HTML);

        assertNotNull(results, "Results should not be null");
        assertEquals(2, results.size());

        CompositeFileSearchResult first = (CompositeFileSearchResult) results.get(0);
        assertEquals("Ubuntu 24.04 LTS", first.getDisplayName());
        assertEquals("https://www.1377x.to/torrent/12345/ubuntu-24-04-lts/",
                first.getDetailsUrl());
        assertEquals("1337x", first.getSource());
        assertFalse(first.isPreliminary());
        assertTrue(first.isCrawlable());

        CompositeFileSearchResult second = (CompositeFileSearchResult) results.get(1);
        assertEquals("Debian 12 netinst", second.getDisplayName());
        assertEquals("https://www.1377x.to/torrent/67890/debian-12-netinst/",
                second.getDetailsUrl());
    }
}
