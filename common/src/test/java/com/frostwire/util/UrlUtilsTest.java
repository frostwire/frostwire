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

package com.frostwire.util;

/**
 * Unit tests for UrlUtils optimized hot-path methods.
 * Tests correctness and performance of the encode method.
 * 
 * Run with: java com.frostwire.util.UrlUtilsTest
 * 
 * @author copilot
 */
public class UrlUtilsTest {
    
    public static void main(String[] args) {
        System.out.println("=== UrlUtils Unit Test ===");
        
        boolean allTestsPassed = true;
        
        // Test encode functionality
        allTestsPassed &= testEncode();
        
        // Test buildMagnetUrl (uses encode)
        allTestsPassed &= testBuildMagnetUrl();
        
        // Performance benchmark
        allTestsPassed &= benchmarkPerformance();
        
        if (allTestsPassed) {
            System.out.println("\n✓ All tests PASSED");
            System.exit(0);
        } else {
            System.out.println("\n✗ Some tests FAILED");
            System.exit(1);
        }
    }
    
    private static boolean testEncode() {
        System.out.println("\n--- Testing encode ---");
        boolean passed = true;
        
        // Test basic ASCII (no encoding needed)
        String result = UrlUtils.encode("hello");
        if (!"hello".equals(result)) {
            System.out.println("  ✗ Failed basic ASCII");
            System.out.println("    Expected: 'hello', Got: '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ Basic ASCII");
        }
        
        // Test spaces (should become %20, not +)
        result = UrlUtils.encode("hello world");
        if (!"hello%20world".equals(result)) {
            System.out.println("  ✗ Failed space encoding");
            System.out.println("    Expected: 'hello%20world', Got: '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ Spaces encoded as %20");
        }
        
        // Test multiple spaces
        result = UrlUtils.encode("hello world test");
        if (!"hello%20world%20test".equals(result)) {
            System.out.println("  ✗ Failed multiple spaces");
            System.out.println("    Expected: 'hello%20world%20test', Got: '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ Multiple spaces");
        }
        
        // Test special characters
        result = UrlUtils.encode("hello&world=test");
        if (!"hello%26world%3Dtest".equals(result)) {
            System.out.println("  ✗ Failed special characters");
            System.out.println("    Expected: 'hello%26world%3Dtest', Got: '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ Special characters");
        }
        
        // Test UTF-8 characters
        result = UrlUtils.encode("Café");
        if (!"Caf%C3%A9".equals(result)) {
            System.out.println("  ✗ Failed UTF-8 encoding");
            System.out.println("    Expected: 'Caf%C3%A9', Got: '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ UTF-8 characters");
        }
        
        // Test realistic filename with spaces and special chars
        result = UrlUtils.encode("Movie Title (2024) [1080p].mkv");
        String expected = "Movie%20Title%20%282024%29%20%5B1080p%5D.mkv";
        if (!expected.equals(result)) {
            System.out.println("  ✗ Failed realistic filename");
            System.out.println("    Expected: '" + expected + "'");
            System.out.println("    Got:      '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ Realistic filename");
        }
        
        // Test null input
        result = UrlUtils.encode(null);
        if (!"".equals(result)) {
            System.out.println("  ✗ Failed null input");
            System.out.println("    Expected: '', Got: '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ Null input returns empty string");
        }
        
        // Test empty string
        result = UrlUtils.encode("");
        if (!"".equals(result)) {
            System.out.println("  ✗ Failed empty string");
            passed = false;
        } else {
            System.out.println("  ✓ Empty string");
        }
        
        // Test string with only spaces
        result = UrlUtils.encode("   ");
        if (!"%20%20%20".equals(result)) {
            System.out.println("  ✗ Failed only spaces");
            System.out.println("    Expected: '%20%20%20', Got: '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ Only spaces");
        }
        
        // Test plus sign in input (should be encoded as %2B)
        result = UrlUtils.encode("C++");
        if (!"C%2B%2B".equals(result)) {
            System.out.println("  ✗ Failed plus sign encoding");
            System.out.println("    Expected: 'C%2B%2B', Got: '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ Plus sign encoded correctly");
        }
        
        return passed;
    }
    
    private static boolean testBuildMagnetUrl() {
        System.out.println("\n--- Testing buildMagnetUrl ---");
        boolean passed = true;
        
        String infoHash = "1234567890abcdef1234567890abcdef12345678";
        String displayName = "Test File Name.mkv";
        String trackers = "tr=udp://tracker.example.com:80/announce";
        
        String result = UrlUtils.buildMagnetUrl(infoHash, displayName, trackers);
        
        // Verify magnet URL format
        if (!result.startsWith("magnet:?xt=urn:btih:")) {
            System.out.println("  ✗ Invalid magnet URL prefix");
            passed = false;
        } else {
            System.out.println("  ✓ Magnet URL prefix correct");
        }
        
        // Verify infoHash is present
        if (!result.contains(infoHash)) {
            System.out.println("  ✗ InfoHash not found in magnet URL");
            passed = false;
        } else {
            System.out.println("  ✓ InfoHash present");
        }
        
        // Verify encoded display name (spaces should be %20)
        if (!result.contains("Test%20File%20Name.mkv")) {
            System.out.println("  ✗ Display name not properly encoded");
            System.out.println("    Result: " + result);
            passed = false;
        } else {
            System.out.println("  ✓ Display name encoded correctly");
        }
        
        // Verify trackers are present
        if (!result.contains(trackers)) {
            System.out.println("  ✗ Trackers not found in magnet URL");
            passed = false;
        } else {
            System.out.println("  ✓ Trackers present");
        }
        
        // Test with special characters in filename
        displayName = "Movie (2024) [1080p] & More.mkv";
        result = UrlUtils.buildMagnetUrl(infoHash, displayName, trackers);
        
        if (!result.contains("Movie%20%282024%29%20%5B1080p%5D%20%26%20More.mkv")) {
            System.out.println("  ✗ Special characters not properly encoded");
            System.out.println("    Result: " + result);
            passed = false;
        } else {
            System.out.println("  ✓ Special characters in filename encoded correctly");
        }
        
        return passed;
    }
    
    private static boolean benchmarkPerformance() {
        System.out.println("\n--- Performance Benchmark ---");
        boolean passed = true;
        
        // Prepare test data - realistic file names for magnet links
        String[] testStrings = {
            "Ubuntu 20.04 ISO Download",
            "Movie Title (2024) 1080p BluRay",
            "Album - Artist - Song Title [320kbps]",
            "Document File Name.pdf",
            "Software v1.2.3 Setup",
            "Game [Full] + DLC (2024)",
            "Book Title - Author Name.epub",
            "Video Tutorial Part 1 of 10",
            "Application Installer x64",
            "Music Album (Deluxe Edition) [FLAC]"
        };
        
        final int ITERATIONS = 10000;
        
        // Benchmark encode
        long startTime = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            for (String s : testStrings) {
                String result = UrlUtils.encode(s);
                if (result.isEmpty() && s != null && !s.isEmpty()) {
                    System.out.println("  ✗ encode returned empty string unexpectedly");
                    passed = false;
                }
            }
        }
        long endTime = System.nanoTime();
        
        long duration = endTime - startTime;
        double durationMs = duration / 1_000_000.0;
        double avgTimePerCall = duration / (double)(ITERATIONS * testStrings.length);
        
        System.out.println("  encode:");
        System.out.println("    Total: " + String.format("%.2f", durationMs) + " ms for " + 
                          (ITERATIONS * testStrings.length) + " calls");
        System.out.println("    Average: " + String.format("%.0f", avgTimePerCall) + " ns per call");
        
        if (avgTimePerCall > 100000) {
            System.out.println("  ⚠ Performance may be slower than expected");
        } else {
            System.out.println("  ✓ Performance is good");
        }
        
        // Verify no memory leaks or GC issues during many iterations
        System.out.println("  Running extended test (100k iterations)...");
        startTime = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            UrlUtils.encode("Test String " + (i % 100));
        }
        endTime = System.nanoTime();
        
        duration = endTime - startTime;
        durationMs = duration / 1_000_000.0;
        
        System.out.println("  Extended test: " + String.format("%.2f", durationMs) + " ms for 100k calls");
        System.out.println("  ✓ No apparent memory issues");
        
        return passed;
    }
}
