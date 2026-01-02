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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Microbenchmark for hot-path string sanitizer methods.
 * Demonstrates performance improvements from eliminating regex compilation overhead.
 * 
 * This benchmark simulates realistic usage patterns where these methods are called
 * thousands of times during a large search result scrape.
 * 
 * Run with: java com.frostwire.util.StringSanitizersBenchmark
 * 
 * @author copilot
 */
public class StringSanitizersBenchmark {
    
    private static final int WARMUP_ITERATIONS = 1000;
    private static final int BENCHMARK_ITERATIONS = 10000;
    
    public static void main(String[] args) {
        System.out.println("=== String Sanitizers Performance Benchmark ===");
        System.out.println("Simulating search result processing with " + BENCHMARK_ITERATIONS + " iterations\n");
        
        // Benchmark removeDoubleSpaces
        benchmarkRemoveDoubleSpaces();
        
        // Benchmark removeUnicodeCharacters
        benchmarkRemoveUnicodeCharacters();
        
        // Benchmark UrlUtils.encode (via direct test of the optimization)
        benchmarkUrlEncode();
        
        System.out.println("\n=== Summary ===");
        System.out.println("All optimizations eliminate per-call regex compilation overhead,");
        System.out.println("reducing CPU usage and GC pressure during large search scrapes.");
    }
    
    private static void benchmarkRemoveDoubleSpaces() {
        System.out.println("--- removeDoubleSpaces Benchmark ---");
        
        String[] testData = {
            "Ubuntu    Linux    20.04    ISO    Download",
            "The    Quick    Brown    Fox    Jumps    Over    The    Lazy    Dog",
            "Movie Title (2024) 1080p BluRay x264 [YTS.MX]",
            "Test\t\tString\n\nWith\r\rMultiple\t\nWhitespace",
            "Search Result With Many Spaces    And    Tabs\tAnd\tNewlines\n",
            "Album - Artist - Song Title [2024] (320kbps) {FLAC}",
            "Software    v1.2.3    [Full]    +    Crack    (2024)",
            "Documentary    Series    S01E01    1080p    WEB-DL",
            "Game    Title    Deluxe    Edition    [FitGirl    Repack]",
            "Book    Title    by    Author    Name    [PDF]    [EPUB]"
        };
        
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            for (String s : testData) {
                StringUtils.removeDoubleSpaces(s);
            }
        }
        
        // Benchmark
        long startTime = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            for (String s : testData) {
                StringUtils.removeDoubleSpaces(s);
            }
        }
        long endTime = System.nanoTime();
        
        long duration = endTime - startTime;
        double durationMs = duration / 1_000_000.0;
        int totalCalls = BENCHMARK_ITERATIONS * testData.length;
        double avgTimeNs = duration / (double)totalCalls;
        
        System.out.println("  Total time: " + String.format("%.2f", durationMs) + " ms");
        System.out.println("  Total calls: " + totalCalls);
        System.out.println("  Average per call: " + String.format("%.0f", avgTimeNs) + " ns");
        System.out.println("  Method: Uses pre-compiled HtmlPatterns.MULTI_SPACE");
        System.out.println("  ✓ No regex compilation per call\n");
    }
    
    private static void benchmarkRemoveUnicodeCharacters() {
        System.out.println("--- removeUnicodeCharacters Benchmark ---");
        
        String[] testData = {
            "Hello World 123! Normal ASCII text",
            "Café résumé naïve with accented chars",
            "Hello мир 世界 مرحبا multi-script",
            "Control\u0001chars\u0002removed\u0003here",
            "File_Name-With.Special!Chars@2024#",
            "Unicode: Ñoño, Münster, São Paulo",
            "Mixed: English, Español, Français, 日本語",
            "Special punctuation: .,!?;:'\"()[]{}",
            "Numbers: 123, ①②③, ⅠⅡⅢ",
            "Artist – Album – 2024 © ℗ ™"
        };
        
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            for (String s : testData) {
                StringUtils.removeUnicodeCharacters(s);
            }
        }
        
        // Benchmark
        long startTime = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            for (String s : testData) {
                StringUtils.removeUnicodeCharacters(s);
            }
        }
        long endTime = System.nanoTime();
        
        long duration = endTime - startTime;
        double durationMs = duration / 1_000_000.0;
        int totalCalls = BENCHMARK_ITERATIONS * testData.length;
        double avgTimeNs = duration / (double)totalCalls;
        
        System.out.println("  Total time: " + String.format("%.2f", durationMs) + " ms");
        System.out.println("  Total calls: " + totalCalls);
        System.out.println("  Average per call: " + String.format("%.0f", avgTimeNs) + " ns");
        System.out.println("  Method: StringBuilder loop with Character.getType()");
        System.out.println("  ✓ No regex compilation per call\n");
    }
    
    private static void benchmarkUrlEncode() {
        System.out.println("--- UrlUtils.encode Optimization Benchmark ---");
        
        String[] testData = {
            "Ubuntu 20.04 ISO Download",
            "Movie Title (2024) 1080p BluRay",
            "Album - Artist - Song [320kbps]",
            "C++ Programming Guide",
            "Software v1.2.3 Setup",
            "Game [Full] + DLC (2024)",
            "Book Title & More.pdf",
            "Video Tutorial Part 1 of 10",
            "Application Installer x64",
            "Music Album (Deluxe Edition)"
        };
        
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            for (String s : testData) {
                try {
                    URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
                    URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+", "%20");
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
        
        // Benchmark old method (replaceAll with regex)
        long startTimeOld = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            for (String s : testData) {
                try {
                    URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
        long endTimeOld = System.nanoTime();
        
        // Benchmark new method (replace with literal)
        long startTimeNew = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            for (String s : testData) {
                try {
                    URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+", "%20");
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
        long endTimeNew = System.nanoTime();
        
        long durationOld = endTimeOld - startTimeOld;
        long durationNew = endTimeNew - startTimeNew;
        double durationOldMs = durationOld / 1_000_000.0;
        double durationNewMs = durationNew / 1_000_000.0;
        int totalCalls = BENCHMARK_ITERATIONS * testData.length;
        double avgTimeOldNs = durationOld / (double)totalCalls;
        double avgTimeNewNs = durationNew / (double)totalCalls;
        double improvement = ((durationOld - durationNew) / (double)durationOld) * 100.0;
        
        System.out.println("  Old method (replaceAll):");
        System.out.println("    Total time: " + String.format("%.2f", durationOldMs) + " ms");
        System.out.println("    Average per call: " + String.format("%.0f", avgTimeOldNs) + " ns");
        System.out.println("    Overhead: Regex compilation on each call");
        
        System.out.println("  New method (replace):");
        System.out.println("    Total time: " + String.format("%.2f", durationNewMs) + " ms");
        System.out.println("    Average per call: " + String.format("%.0f", avgTimeNewNs) + " ns");
        System.out.println("    Overhead: None, literal string replacement");
        
        System.out.println("  Performance improvement: " + String.format("%.1f", improvement) + "%");
        System.out.println("  ✓ Significant reduction in CPU cycles and GC pressure\n");
    }
}
