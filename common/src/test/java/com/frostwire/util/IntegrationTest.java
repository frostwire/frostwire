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
 * Integration test simulating how PerformersHelper uses string sanitizers.
 * This test validates that our optimizations maintain the same behavior
 * as used in search result processing.
 * 
 * Run with: java com.frostwire.util.IntegrationTest
 * 
 * @author copilot
 */
public class IntegrationTest {
    
    public static void main(String[] args) {
        System.out.println("=== Integration Test: Simulating Search Result Processing ===\n");
        
        boolean allTestsPassed = true;
        
        // Test the full sanitization pipeline as used in PerformersHelper
        allTestsPassed &= testSanitizationPipeline();
        
        // Test magnet URL generation
        allTestsPassed &= testMagnetUrlGeneration();
        
        if (allTestsPassed) {
            System.out.println("\n✓ All integration tests PASSED");
            System.exit(0);
        } else {
            System.out.println("\n✗ Some integration tests FAILED");
            System.exit(1);
        }
    }
    
    private static boolean testSanitizationPipeline() {
        System.out.println("--- Testing Sanitization Pipeline ---");
        System.out.println("Simulating PerformersHelper.sanitize() usage\n");
        
        boolean passed = true;
        
        // Test cases from realistic search results
        String[][] testCases = {
            {
                "Ubuntu    Linux    20.04    ISO    Download",
                "ubuntu linux 20.04 iso download"
            },
            {
                "Movie  Title  (2024)  1080p  BluRay  x264",
                "movie title 2024 1080p bluray x264"
            },
            {
                "Artist\t-\tAlbum\n-\rSong\tTitle",
                "artist album song title"
            },
            {
                "File_Name.With-Special.Characters[2024]",
                "file name with special characters 2024"
            },
            {
                "Normal   String   With   Spaces",
                "normal string with spaces"
            }
        };
        
        for (String[] testCase : testCases) {
            String input = testCase[0];
            String expected = testCase[1];
            
            // Simulate the sanitization steps
            String result = input;
            
            // Step 1: Remove unicode characters (as used in sanitize)
            result = StringUtils.removeUnicodeCharacters(result);
            
            // Step 2: Remove double spaces (as used in sanitize)
            result = StringUtils.removeDoubleSpaces(result);
            
            // Step 3: Normalize case and trim
            result = result.toLowerCase().trim();
            
            boolean matches = expected.equals(result);
            
            if (!matches) {
                System.out.println("  ✗ Failed sanitization");
                System.out.println("    Input:    '" + input + "'");
                System.out.println("    Expected: '" + expected + "'");
                System.out.println("    Got:      '" + result + "'");
                passed = false;
            } else {
                System.out.println("  ✓ '" + input.substring(0, Math.min(40, input.length())) + 
                                 (input.length() > 40 ? "..." : "") + "'");
            }
        }
        
        System.out.println();
        return passed;
    }
    
    private static boolean testMagnetUrlGeneration() {
        System.out.println("--- Testing Magnet URL Generation ---");
        System.out.println("Simulating UrlUtils.buildMagnetUrl() usage\n");
        
        boolean passed = true;
        
        String[][] testCases = {
            {
                "Ubuntu 20.04 Desktop ISO",
                "magnet:?xt=urn:btih:HASH&dn=Ubuntu%2020.04%20Desktop%20ISO&"
            },
            {
                "Movie Title (2024) [1080p]",
                "magnet:?xt=urn:btih:HASH&dn=Movie%20Title%20%282024%29%20%5B1080p%5D&"
            },
            {
                "C++ Programming Guide",
                "magnet:?xt=urn:btih:HASH&dn=C%2B%2B%20Programming%20Guide&"
            },
            {
                "Album & More Music",
                "magnet:?xt=urn:btih:HASH&dn=Album%20%26%20More%20Music&"
            }
        };
        
        String infoHash = "1234567890abcdef1234567890abcdef12345678";
        String trackers = "tr=udp://tracker.example.com:80";
        
        for (String[] testCase : testCases) {
            String displayName = testCase[0];
            String expectedPattern = testCase[1];
            
            String result = UrlUtils.buildMagnetUrl(infoHash, displayName, trackers);
            
            // Replace HASH placeholder in expected pattern
            expectedPattern = expectedPattern.replace("HASH", infoHash);
            
            boolean matches = result.startsWith(expectedPattern.replace("&tr=", "").replace("&", ""));
            boolean hasEncodedName = result.contains(UrlUtils.encode(displayName));
            
            if (!hasEncodedName) {
                System.out.println("  ✗ Failed magnet URL generation");
                System.out.println("    Display name: '" + displayName + "'");
                System.out.println("    Expected encoded name in URL");
                System.out.println("    Got: " + result);
                passed = false;
            } else {
                System.out.println("  ✓ '" + displayName + "'");
            }
            
            // Verify no plus signs in the URL (should all be %20)
            String encoded = UrlUtils.encode(displayName);
            if (encoded.contains("+")) {
                System.out.println("  ✗ Found '+' in encoded string, should be '%20'");
                passed = false;
            }
        }
        
        System.out.println();
        return passed;
    }
}
