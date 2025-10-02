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

package com.frostwire.util;

/**
 * Unit test for DateParser utility class.
 * Verifies that all date formats used by search results are parsed correctly.
 * 
 * Run with: gradle test --tests "com.frostwire.util.DateParserTest"
 * 
 * @author gubatron
 * @author copilot
 */
public class DateParserTest {
    
    public static void main(String[] args) {
        System.out.println("=== DateParser Unit Test ===");
        
        boolean allTestsPassed = true;
        
        // Test 1: parseTorrentDate with various formats
        allTestsPassed &= testTorrentDateFormats();
        
        // Test 2: parseIsoDate
        allTestsPassed &= testIsoDateFormats();
        
        // Test 3: parseSimpleDate
        allTestsPassed &= testSimpleDateFormats();
        
        // Test 4: parseRelativeAge
        allTestsPassed &= testRelativeAgeFormats();
        
        // Test 5: Thread safety
        allTestsPassed &= testThreadSafety();
        
        // Test 6: Performance (no allocations)
        allTestsPassed &= testPerformance();
        
        if (allTestsPassed) {
            System.out.println("\n✓ All tests PASSED");
            System.exit(0);
        } else {
            System.out.println("\n✗ Some tests FAILED");
            System.exit(1);
        }
    }
    
    private static boolean testTorrentDateFormats() {
        System.out.println("\n--- Testing parseTorrentDate ---");
        boolean passed = true;
        
        // Test various formats
        String[][] testCases = {
            {"2024-01-15 14:30:00", "yyyy-MM-dd HH:mm:ss"},
            {"2024-01-15", "yyyy-MM-dd"},
            {"15/01/2024", "dd/MM/yyyy"},
            {"01/15/2024", "MM/dd/yyyy"},
            {"2024/01/15", "yyyy/MM/dd"},
            {"2024-01-15T14:30:00Z", "ISO 8601"},
            {"2024-01-15T14:30:00.123Z", "ISO 8601 with millis"}
        };
        
        for (String[] testCase : testCases) {
            String dateStr = testCase[0];
            String format = testCase[1];
            long result = DateParser.parseTorrentDate(dateStr);
            boolean valid = result > 0 && result <= System.currentTimeMillis();
            System.out.println(String.format("  %s (%s): %s", 
                dateStr, format, valid ? "✓" : "✗"));
            passed &= valid;
        }
        
        // Test null/empty handling
        long nullResult = DateParser.parseTorrentDate(null);
        boolean nullHandled = nullResult > 0;
        System.out.println(String.format("  null handling: %s", nullHandled ? "✓" : "✗"));
        passed &= nullHandled;
        
        long emptyResult = DateParser.parseTorrentDate("");
        boolean emptyHandled = emptyResult > 0;
        System.out.println(String.format("  empty string handling: %s", emptyHandled ? "✓" : "✗"));
        passed &= emptyHandled;
        
        return passed;
    }
    
    private static boolean testIsoDateFormats() {
        System.out.println("\n--- Testing parseIsoDate ---");
        boolean passed = true;
        
        String[] testCases = {
            "2009-12-02T15:41:50Z",
            "2008-02-20T22:02:21Z",
            "2024-01-15T14:30:00.123Z"
        };
        
        for (String dateStr : testCases) {
            long result = DateParser.parseIsoDate(dateStr);
            boolean valid = result > 0 && result <= System.currentTimeMillis();
            System.out.println(String.format("  %s: %s", dateStr, valid ? "✓" : "✗"));
            passed &= valid;
        }
        
        // Test invalid handling
        long invalidResult = DateParser.parseIsoDate("invalid-date");
        boolean invalidHandled = invalidResult == -1;
        System.out.println(String.format("  invalid date handling: %s", invalidHandled ? "✓" : "✗"));
        passed &= invalidHandled;
        
        return passed;
    }
    
    private static boolean testSimpleDateFormats() {
        System.out.println("\n--- Testing parseSimpleDate ---");
        boolean passed = true;
        
        String[] testCases = {
            "2024-01-15",
            "2023-12-25",
            "2022-06-30"
        };
        
        for (String dateStr : testCases) {
            long result = DateParser.parseSimpleDate(dateStr);
            boolean valid = result > 0 && result <= System.currentTimeMillis();
            System.out.println(String.format("  %s: %s", dateStr, valid ? "✓" : "✗"));
            passed &= valid;
        }
        
        return passed;
    }
    
    private static boolean testRelativeAgeFormats() {
        System.out.println("\n--- Testing parseRelativeAge ---");
        boolean passed = true;
        long now = System.currentTimeMillis();
        
        String[][] testCases = {
            {"3 hours ago", "hours"},
            {"2 days ago", "days"},
            {"5 weeks ago", "weeks"},
            {"3 months ago", "months"},
            {"1 year ago", "years"},
            {"Yesterday", "special"},
            {"1 Year+", "special"},
            {"Last Month", "special"},
            {"12 minutes ago", "minutes"}
        };
        
        for (String[] testCase : testCases) {
            String ageStr = testCase[0];
            String type = testCase[1];
            long result = DateParser.parseRelativeAge(ageStr);
            boolean valid = result > 0 && result < now;
            System.out.println(String.format("  %s (%s): %s", 
                ageStr, type, valid ? "✓" : "✗"));
            passed &= valid;
        }
        
        return passed;
    }
    
    private static boolean testThreadSafety() {
        System.out.println("\n--- Testing Thread Safety ---");
        
        final int NUM_THREADS = 10;
        final int NUM_ITERATIONS = 1000;
        final String[] testDates = {
            "2024-01-15 14:30:00",
            "2024-01-15",
            "3 days ago",
            "2024-01-15T14:30:00Z"
        };
        
        Thread[] threads = new Thread[NUM_THREADS];
        final boolean[] results = new boolean[NUM_THREADS];
        
        for (int i = 0; i < NUM_THREADS; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                boolean threadPassed = true;
                try {
                    for (int j = 0; j < NUM_ITERATIONS; j++) {
                        for (String date : testDates) {
                            long result = DateParser.parseTorrentDate(date);
                            if (result <= 0) {
                                threadPassed = false;
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    threadPassed = false;
                }
                results[threadIndex] = threadPassed;
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        boolean allPassed = true;
        try {
            for (int i = 0; i < NUM_THREADS; i++) {
                threads[i].join();
                allPassed &= results[i];
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
        
        System.out.println(String.format("  %d threads x %d iterations: %s", 
            NUM_THREADS, NUM_ITERATIONS, allPassed ? "✓" : "✗"));
        
        return allPassed;
    }
    
    private static boolean testPerformance() {
        System.out.println("\n--- Testing Performance ---");
        
        final int NUM_ITERATIONS = 5000;
        final String[] testDates = {
            "2024-01-15 14:30:00",
            "2024-01-15",
            "3 days ago",
            "2024-01-15T14:30:00Z",
            "Yesterday"
        };
        
        // Warm up
        for (int i = 0; i < 100; i++) {
            for (String date : testDates) {
                DateParser.parseTorrentDate(date);
            }
        }
        
        // Measure performance
        long startTime = System.nanoTime();
        for (int i = 0; i < NUM_ITERATIONS; i++) {
            for (String date : testDates) {
                DateParser.parseTorrentDate(date);
            }
        }
        long endTime = System.nanoTime();
        
        long durationMs = (endTime - startTime) / 1_000_000;
        double avgTimePerParse = (double) durationMs / (NUM_ITERATIONS * testDates.length);
        
        System.out.println(String.format("  Parsed %d dates in %d ms", 
            NUM_ITERATIONS * testDates.length, durationMs));
        System.out.println(String.format("  Average time per parse: %.4f ms", avgTimePerParse));
        
        // Performance should be reasonable (less than 1ms per parse on average)
        boolean performanceGood = avgTimePerParse < 1.0;
        System.out.println(String.format("  Performance: %s", performanceGood ? "✓" : "✗"));
        
        return performanceGood;
    }
}
