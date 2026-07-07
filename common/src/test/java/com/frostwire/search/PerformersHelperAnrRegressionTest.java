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

package com.frostwire.search;

import com.frostwire.licenses.CreativeCommonsLicense;
import com.frostwire.licenses.License;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the SearchResultListAdapter ANR: relevance sort must pre-compute
 * normalized strings once and must not recompile regex patterns per search result.
 */
class PerformersHelperAnrRegressionTest {

    private static final long ANR_THRESHOLD_MS = 5_000;

    @Test
    void relevanceSortCompletesUnderAnrThresholdForLargeResultSet() {
        List<FileSearchResult> searchResults = new ArrayList<>();
        String[] problematicNames = {
                "Ubuntu.18.04.desktop.amd64.iso.torrent",
                "www.example.com_Ultimate.Movie.Collection[2020]",
                "Linux\\Distribution/Complete&Set*@#$%.torrent",
                "Software_Package-v2.3;Final(Release).zip",
                "Music|Album{Best}Of[Artist]_2021.mp3.torrent",
                "Àlbum_Mùsíc&Sóngs*Collection#2021.torrent",
                "www.site.net/download/file_name-with.many.dots...",
                "Complex(Movie)Title[2021]www.tracker.com.mkv.torrent"
        };

        for (int i = 0; i < 1000; i++) {
            for (String name : problematicNames) {
                searchResults.add(new MockFileSearchResult(name, name + "_" + i + ".file"));
            }
        }

        String currentQuery = "ubuntu movie music";
        List<String> tokens = PerformersHelper.tokenizeSearchKeywords(currentQuery.toLowerCase());
        tokens.removeIf(PerformersHelper.stopwords::contains);

        Map<SearchResult, String> normalized = new HashMap<>();
        Map<SearchResult, Integer> levenshtein = new HashMap<>();

        long startTime = System.nanoTime();

        for (SearchResult r : searchResults) {
            String n = PerformersHelper.searchResultAsNormalizedString(r).toLowerCase();
            normalized.put(r, n);
            levenshtein.put(r, PerformersHelper.levenshteinDistance(n, currentQuery));
        }

        searchResults.sort((a, b) -> {
            int m1 = PerformersHelper.countMatchedTokens(normalized.get(a), tokens);
            int m2 = PerformersHelper.countMatchedTokens(normalized.get(b), tokens);
            if (m1 != m2) {
                return Integer.compare(m2, m1);
            }
            return Integer.compare(levenshtein.get(a), levenshtein.get(b));
        });

        long totalTimeMs = (System.nanoTime() - startTime) / 1_000_000L;

        assertTrue(totalTimeMs < ANR_THRESHOLD_MS,
                "Relevance sort took " + totalTimeMs + " ms, exceeds ANR threshold of " + ANR_THRESHOLD_MS + " ms");
        assertTrue(PerformersHelper.countMatchedTokens(normalized.get(searchResults.get(0)), tokens) > 0,
                "Top-ranked result should match at least one query token");
    }

    @Test
    void sanitizeStripsHtmlAndCombiningMarks() {
        MockFileSearchResult sr = new MockFileSearchResult(
                "Café <b>www.example.com</b> release",
                "café_release.mkv");
        String normalized = PerformersHelper.searchResultAsNormalizedString(sr);

        assertTrue(normalized.contains("cafe"), "Expected ASCII form of accented characters");
        assertTrue(normalized.contains("example"), "Expected host fragment after www. removal");
        assertTrue(!normalized.contains("<"), "HTML tags should be stripped");
    }

    private static final class MockFileSearchResult implements FileSearchResult {
        private final String displayName;
        private final String filename;

        MockFileSearchResult(String displayName, String filename) {
            this.displayName = displayName;
            this.filename = filename;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String getFilename() {
            return filename;
        }

        @Override
        public long getSize() {
            return 1024;
        }

        @Override
        public String getDetailsUrl() {
            return "http://example.com";
        }

        @Override
        public String getSource() {
            return "test";
        }

        @Override
        public long getCreationTime() {
            return System.currentTimeMillis();
        }

        @Override
        public License getLicense() {
            return CreativeCommonsLicense.standard("Attribution", "BY", "4.0");
        }

        @Override
        public String getThumbnailUrl() {
            return null;
        }
    }
}