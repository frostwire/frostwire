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

package com.frostwire.search;

import java.util.*;

/**
 * Regression test for ANR issue in SearchResultListAdapter.
 * This test simulates the exact scenario that was causing ANR when sorting
 * large numbers of search results during relevance ranking.
 * 
 * @author gubatron
 * @author aldenml
 */
public class ANRRegressionTest {
    
    public static void main(String[] args) {
        System.out.println("=== ANR Regression Test ===");
        
        // Create test data similar to what caused the original ANR
        List<MockSearchResult> searchResults = new ArrayList<>();
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
        
        // Create realistic dataset size that could cause ANR
        for (int i = 0; i < 1000; i++) {
            for (String name : problematicNames) {
                String filename = name + "_" + i + ".file";
                searchResults.add(new MockSearchResult(name, filename));
            }
        }
        
        System.out.println("Testing " + searchResults.size() + " search results...");
        
        // Simulate the exact operation that was causing ANR
        String currentQuery = "ubuntu movie music";
        List<String> tokens = PerformersHelper.tokenizeSearchKeywords(currentQuery.toLowerCase());
        
        Map<SearchResult, String> normalized = new HashMap<>();
        Map<SearchResult, Integer> levenshtein = new HashMap<>();
        
        long startTime = System.nanoTime();
        
        // This was the bottleneck that caused ANR
        for (SearchResult r : searchResults) {
            String n = PerformersHelper.searchResultAsNormalizedString(r).toLowerCase();
            normalized.put(r, n);
            levenshtein.put(r, PerformersHelper.levenshteinDistance(n, currentQuery));
        }
        
        // Sort by relevance (also part of the ANR scenario)
        searchResults.sort((a, b) -> {
            int m1 = PerformersHelper.countMatchedTokens(normalized.get(a), tokens);
            int m2 = PerformersHelper.countMatchedTokens(normalized.get(b), tokens);
            if (m1 != m2) return Integer.compare(m2, m1);
            return Integer.compare(levenshtein.get(a), levenshtein.get(b));
        });
        
        long totalTime = System.nanoTime() - startTime;
        double totalTimeMs = totalTime / 1_000_000.0;
        
        System.out.println("Total processing time: " + String.format("%.2f", totalTimeMs) + " ms");
        
        // ANR threshold is 5 seconds on Android
        double anrThresholdMs = 5000;
        if (totalTimeMs > anrThresholdMs) {
            throw new RuntimeException("ANR regression detected! Processing took " + 
                                     String.format("%.2f", totalTimeMs) + 
                                     " ms, exceeds threshold of " + anrThresholdMs + " ms");
        }
        
        // Should complete in well under 1 second with optimization
        if (totalTimeMs > 1000) {
            System.out.println("WARNING: Performance slower than expected (" + 
                             String.format("%.2f", totalTimeMs) + " ms)");
        } else {
            System.out.println("PASS: Performance excellent, well under ANR threshold");
        }
        
        // Verify search functionality is working
        int topResultMatches = PerformersHelper.countMatchedTokens(
            normalized.get(searchResults.get(0)), tokens);
        
        if (topResultMatches == 0) {
            throw new RuntimeException("Search ranking is not working - no matches found");
        }
        
        System.out.println("Top result has " + topResultMatches + " keyword matches");
        System.out.println("ANR regression test PASSED!");
    }
    
    /**
     * Mock SearchResult for testing that implements FileSearchResult
     */
    private static class MockSearchResult implements FileSearchResult {
        private final String displayName;
        private final String filename;
        
        public MockSearchResult(String displayName, String filename) {
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
        public com.frostwire.licenses.License getLicense() {
            return null;
        }
        
        @Override
        public String getThumbnailUrl() {
            return null;
        }
    }
}