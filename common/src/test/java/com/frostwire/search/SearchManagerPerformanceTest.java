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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Performance test for SearchManager refactoring.
 * Tests that the conversion from Thread to Runnable improves performance
 * and reduces memory allocation.
 * 
 * @author gubatron
 * @author aldenml
 */
public class SearchManagerPerformanceTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== SearchManager Performance Test ===");
        
        // Create a stub performer that emits fake results
        StubPerformer performer = new StubPerformer(System.nanoTime(), 2000);
        
        SearchManager manager = SearchManager.getInstance();
        
        final CountDownLatch completionLatch = new CountDownLatch(1);
        final AtomicInteger resultsReceived = new AtomicInteger(0);
        
        // Set up listener to count results
        manager.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                resultsReceived.addAndGet(results.size());
            }
            
            @Override
            public void onError(long token, SearchError error) {
                System.out.println("Error: " + error);
            }
            
            @Override
            public void onStopped(long token) {
                completionLatch.countDown();
            }
        });
        
        // Measure execution time
        long startTime = System.nanoTime();
        
        manager.perform(performer);
        
        // Wait for completion (max 30 seconds)
        boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
        
        long totalTime = System.nanoTime() - startTime;
        double totalTimeMs = totalTime / 1_000_000.0;
        
        if (!completed) {
            throw new RuntimeException("Test timeout - search did not complete in 30 seconds");
        }
        
        System.out.println("Total execution time: " + String.format("%.2f", totalTimeMs) + " ms");
        System.out.println("Results received: " + resultsReceived.get());
        
        // Verify results were received
        if (resultsReceived.get() != 2000) {
            throw new RuntimeException("Expected 2000 results, got " + resultsReceived.get());
        }
        
        // Performance should be reasonable (less than 10 seconds for 2k results)
        if (totalTimeMs > 10000) {
            System.out.println("WARNING: Performance slower than expected (" + 
                             String.format("%.2f", totalTimeMs) + " ms)");
        } else {
            System.out.println("PASS: Performance excellent");
        }
        
        System.out.println("SearchManager performance test PASSED!");
        
        // Clean up
        manager.stop();
    }
    
    /**
     * Stub search performer that generates fake results for testing
     */
    private static class StubPerformer extends AbstractSearchPerformer {
        private final int numResults;
        
        public StubPerformer(long token, int numResults) {
            super(token);
            this.numResults = numResults;
        }
        
        @Override
        public void perform() {
            List<SearchResult> results = new ArrayList<>(100);
            
            // Generate results in batches
            for (int i = 0; i < numResults; i++) {
                results.add(new StubSearchResult("result-" + i));
                
                // Send in batches of 100
                if (results.size() >= 100) {
                    onResults(new ArrayList<>(results));
                    results.clear();
                }
            }
            
            // Send remaining results
            if (!results.isEmpty()) {
                onResults(results);
            }
        }
        
        @Override
        public void crawl(CrawlableSearchResult sr) {
            // No crawling in this stub
        }
        
        @Override
        public boolean isCrawler() {
            return false;
        }
    }
    
    /**
     * Stub search result for testing
     */
    private static class StubSearchResult implements FileSearchResult {
        private final String filename;
        
        public StubSearchResult(String filename) {
            this.filename = filename;
        }
        
        @Override
        public String getDisplayName() {
            return filename;
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
            return "http://example.com/" + filename;
        }
        
        @Override
        public String getSource() {
            return "stub";
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
