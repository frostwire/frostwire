/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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

package com.frostwire.search;

/**
 * Performance regression test for PerformersHelper.
 * This test ensures that the sanitize method optimization doesn't break functionality
 * and provides reasonable performance for large result sets.
 */
public class PerformersHelperPerformanceTest {
    
    public static void main(String[] args) {
        System.out.println("Testing PerformersHelper.searchResultAsNormalizedString performance...");
        
        // Create test search results with problematic strings that would cause ANR
        String[] testStrings = {
            "some.torrent.file.name.test",
            "www.example.com/file/name_with-special;chars",
            "file\\with\\backslashes&symbols*@#$%",
            "very.long.string.with.many.dots.and.www.prefix.com.and.net.extension.torrent",
            "String_with_underscores-and-dashes;semicolons&ampersands",
            "ÀÁfile#with|brackets[]and{curly}braces^caret'quotes",
            "Complex(file)name[with].torrent{multiple}special&chars*@^",
            "normalStringWithoutSpecialCharacters"
        };
        
        MockSearchResult[] searchResults = new MockSearchResult[testStrings.length];
        for (int i = 0; i < testStrings.length; i++) {
            searchResults[i] = new MockSearchResult(testStrings[i]);
        }
        
        // Performance test - this should complete quickly (under 1 second for many iterations)
        int iterations = 10000;
        long startTime = System.nanoTime();
        
        for (int i = 0; i < iterations; i++) {
            for (MockSearchResult sr : searchResults) {
                String normalized = PerformersHelper.searchResultAsNormalizedString(sr);
                // Basic validation - should not be null or empty for non-empty inputs
                if (sr.getDisplayName().length() > 0 && (normalized == null || normalized.trim().isEmpty())) {
                    throw new RuntimeException("Normalization failed for: " + sr.getDisplayName());
                }
            }
        }
        
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        double durationMs = duration / 1_000_000.0;
        
        System.out.println("Processed " + (iterations * searchResults.length) + " normalizations in " + 
                          String.format("%.2f", durationMs) + " ms");
        System.out.println("Average time per normalization: " + 
                          String.format("%.4f", durationMs / (iterations * searchResults.length)) + " ms");
        
        // Test should complete in reasonable time (less than 2 seconds for this test size)
        if (durationMs > 2000) {
            throw new RuntimeException("Performance regression detected! Test took " + 
                                     String.format("%.2f", durationMs) + " ms, expected < 2000 ms");
        }
        
        // Test correctness with some examples
        System.out.println("\nCorrectness validation:");
        for (int i = 0; i < Math.min(3, searchResults.length); i++) {
            String original = searchResults[i].getDisplayName();
            String normalized = PerformersHelper.searchResultAsNormalizedString(searchResults[i]);
            System.out.println("\"" + original + "\" -> \"" + normalized + "\"");
        }
        
        System.out.println("\nPerformance test passed!");
    }
    
    /**
     * Mock SearchResult for testing purposes
     */
    private static class MockSearchResult implements SearchResult {
        private final String displayName;
        
        public MockSearchResult(String displayName) {
            this.displayName = displayName;
        }
        
        @Override
        public String getDisplayName() {
            return displayName;
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
    }
}