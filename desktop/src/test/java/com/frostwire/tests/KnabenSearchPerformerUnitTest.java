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

package com.frostwire.tests;

import com.frostwire.search.knaben.KnabenSearchPattern;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.util.Logger;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V2 Architecture: Unit tests for KnabenSearchPattern JSON parsing
 * gradle test --tests "com.frostwire.tests.KnabenSearchPerformerUnitTest.testJsonParsing"
 */
public class KnabenSearchPerformerUnitTest {
    private final static Logger LOG = Logger.getLogger(KnabenSearchPerformerUnitTest.class);
    
    @Test
    public void testJsonParsing() {
        // Sample JSON response that might be returned by Knaben API
        String sampleJson = """
        {
            "torrents": [
                {
                    "name": "Ubuntu 22.04 LTS Desktop",
                    "infohash": "1234567890abcdef1234567890abcdef12345678",
                    "size": 4000000000,
                    "seeds": 50,
                    "created": "2024-01-15 10:30:00"
                },
                {
                    "title": "Ubuntu Server 22.04",
                    "hash": "abcdef1234567890abcdef1234567890abcdef12",
                    "length": 2000000000,
                    "seeders": 25,
                    "upload_date": "2024-01-10"
                }
            ]
        }
        """;

        KnabenSearchPattern pattern = new KnabenSearchPattern();
        List<FileSearchResult> results = pattern.parseResults(sampleJson);

        assertNotNull(results);
        assertTrue(results.size() > 0, "Should parse at least one result");

        // Test first result
        CompositeFileSearchResult result1 = (CompositeFileSearchResult) results.get(0);
        assertNotNull(result1.getDisplayName());
        assertNotNull(result1.getTorrentHash());
        assertEquals(4000000000L, result1.getSize());
        assertTrue(result1.getSeeds().isPresent());
        assertEquals("Knaben", result1.getSource());

        LOG.info("JSON parsing test passed successfully");
    }

    @Test
    public void testAlternativeJsonFormat() {
        // Test with a different JSON format (direct array)
        String sampleJson = """
        [
            {
                "filename": "Ubuntu 22.04.torrent",
                "info_hash": "fedcba0987654321fedcba0987654321fedcba09",
                "bytes": 3000000000,
                "seeder": 30,
                "date": "2024-01-12 15:45:30"
            }
        ]
        """;

        KnabenSearchPattern pattern = new KnabenSearchPattern();
        List<FileSearchResult> results = pattern.parseResults(sampleJson);

        assertNotNull(results);
        assertTrue(results.size() > 0, "Should parse results from array format");

        CompositeFileSearchResult result = (CompositeFileSearchResult) results.get(0);
        assertNotNull(result.getDisplayName());
        assertNotNull(result.getTorrentHash());
        assertEquals(3000000000L, result.getSize());

        LOG.info("Alternative JSON format test passed successfully");
    }

    @Test
    public void testActualKnabenApiFormat() {
        // Test with the actual format returned by Knaben API (hits array)
        String sampleJson = """
        {
            "hits": [
                {
                    "bytes": 328613232,
                    "cachedOrigin": "The Pirate Bay (proxy)",
                    "category": "XXX",
                    "categoryId": [5000000],
                    "date": "2019-09-01T22:00:00+00:00",
                    "details": "https://knaben.xyz/thepiratebay/description.php?id=123456",
                    "name": "Sample Torrent File",
                    "infohash": "1234567890abcdef1234567890abcdef12345678",
                    "seeds": 42
                }
            ]
        }
        """;

        KnabenSearchPattern pattern = new KnabenSearchPattern();
        List<FileSearchResult> results = pattern.parseResults(sampleJson);

        assertNotNull(results);
        assertTrue(results.size() > 0, "Should parse Knaben API format");

        CompositeFileSearchResult result = (CompositeFileSearchResult) results.get(0);
        assertNotNull(result.getDisplayName());
        assertNotNull(result.getTorrentHash());
        assertEquals(328613232L, result.getSize());
        assertTrue(result.getSeeds().isPresent());

        LOG.info("Actual Knaben API format test passed successfully");
    }

    @Test
    public void testHtmlErrorHandling() {
        // Test that HTML responses are handled gracefully
        String htmlResponse = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>404 Not Found</title>
        </head>
        <body>
            <h1>404 Not Found</h1>
            <p>The requested resource was not found.</p>
        </body>
        </html>
        """;

        KnabenSearchPattern pattern = new KnabenSearchPattern();
        List<FileSearchResult> results = pattern.parseResults(htmlResponse);

        // Should handle gracefully and return empty list or handle error
        assertNotNull(results);

        LOG.info("HTML error handling test passed successfully");
    }
}