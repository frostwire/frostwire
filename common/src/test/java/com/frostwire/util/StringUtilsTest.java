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

package com.frostwire.util;

/**
 * Unit tests for StringUtils optimized hot-path methods.
 * Tests correctness and performance of removeDoubleSpaces and removeUnicodeCharacters.
 * 
 * Run with: java com.frostwire.util.StringUtilsTest
 * 
 * @author copilot
 */
public class StringUtilsTest {
    
    public static void main(String[] args) {
        System.out.println("=== StringUtils Unit Test ===");
        
        boolean allTestsPassed = true;
        
        // Test removeDoubleSpaces
        allTestsPassed &= testRemoveDoubleSpaces();
        
        // Test removeUnicodeCharacters
        allTestsPassed &= testRemoveUnicodeCharacters();
        
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
    
    private static boolean testRemoveDoubleSpaces() {
        System.out.println("\n--- Testing removeDoubleSpaces ---");
        boolean passed = true;
        
        // Test basic double space removal
        String result = StringUtils.removeDoubleSpaces("hello  world");
        if (!"hello world".equals(result)) {
            System.out.println("  ✗ Failed basic double space removal");
            System.out.println("    Expected: 'hello world', Got: '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ Basic double space removal");
        }
        
        // Test multiple consecutive spaces
        result = StringUtils.removeDoubleSpaces("hello     world");
        if (!"hello world".equals(result)) {
            System.out.println("  ✗ Failed multiple consecutive spaces");
            System.out.println("    Expected: 'hello world', Got: '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ Multiple consecutive spaces");
        }
        
        // Test tabs and newlines (all whitespace)
        result = StringUtils.removeDoubleSpaces("hello\t\n  world");
        if (!"hello world".equals(result)) {
            System.out.println("  ✗ Failed tabs and newlines");
            System.out.println("    Expected: 'hello world', Got: '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ Tabs and newlines");
        }
        
        // Test leading/trailing whitespace
        result = StringUtils.removeDoubleSpaces("  hello world  ");
        if (!" hello world ".equals(result)) {
            System.out.println("  ✗ Failed leading/trailing whitespace");
            System.out.println("    Expected: ' hello world ', Got: '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ Leading/trailing whitespace preserved");
        }
        
        // Test null input
        result = StringUtils.removeDoubleSpaces(null);
        if (result != null) {
            System.out.println("  ✗ Failed null input");
            passed = false;
        } else {
            System.out.println("  ✓ Null input");
        }
        
        // Test empty string
        result = StringUtils.removeDoubleSpaces("");
        if (!"".equals(result)) {
            System.out.println("  ✗ Failed empty string");
            passed = false;
        } else {
            System.out.println("  ✓ Empty string");
        }
        
        // Test single space (no change)
        result = StringUtils.removeDoubleSpaces("hello world");
        if (!"hello world".equals(result)) {
            System.out.println("  ✗ Failed single space");
            passed = false;
        } else {
            System.out.println("  ✓ Single space unchanged");
        }
        
        return passed;
    }
    
    private static boolean testRemoveUnicodeCharacters() {
        System.out.println("\n--- Testing removeUnicodeCharacters ---");
        boolean passed = true;
        
        // Test basic ASCII (should remain unchanged)
        String result = StringUtils.removeUnicodeCharacters("Hello World 123!");
        if (!"Hello World 123!".equals(result)) {
            System.out.println("  ✗ Failed basic ASCII");
            System.out.println("    Expected: 'Hello World 123!', Got: '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ Basic ASCII preserved");
        }
        
        // Test control characters (should be removed)
        result = StringUtils.removeUnicodeCharacters("Hello\u0001World\u0002");
        if (!"HelloWorld".equals(result)) {
            System.out.println("  ✗ Failed control character removal");
            System.out.println("    Expected: 'HelloWorld', Got: '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ Control characters removed");
        }
        
        // Test emoji and special symbols (should be removed)
        result = StringUtils.removeUnicodeCharacters("Hello 😀 World ™");
        // Both emoji and ™ (trademark symbol) should be removed as they are symbols, not letters/numbers/punctuation/separators
        String expected = "Hello  World ";
        if (!expected.equals(result)) {
            System.out.println("  ✗ Failed emoji/symbol filtering");
            System.out.println("    Expected: '" + expected + "', Got: '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ Emoji/symbols filtered correctly");
        }
        
        // Test accented characters (letters should be preserved)
        result = StringUtils.removeUnicodeCharacters("Café résumé naïve");
        if (!"Café résumé naïve".equals(result)) {
            System.out.println("  ✗ Failed accented characters");
            System.out.println("    Expected: 'Café résumé naïve', Got: '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ Accented characters preserved");
        }
        
        // Test various Unicode scripts (letters from different languages)
        result = StringUtils.removeUnicodeCharacters("Hello мир 世界 مرحبا");
        if (!"Hello мир 世界 مرحبا".equals(result)) {
            System.out.println("  ✗ Failed Unicode scripts");
            System.out.println("    Expected: 'Hello мир 世界 مرحبا', Got: '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ Unicode scripts preserved");
        }
        
        // Test punctuation (should be preserved)
        result = StringUtils.removeUnicodeCharacters("Hello, World! How are you? (Fine)");
        if (!"Hello, World! How are you? (Fine)".equals(result)) {
            System.out.println("  ✗ Failed punctuation preservation");
            System.out.println("    Expected: 'Hello, World! How are you? (Fine)', Got: '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ Punctuation preserved");
        }
        
        // Test numbers
        result = StringUtils.removeUnicodeCharacters("Test123 ①②③");
        // ①②③ are numbers and should be preserved
        if (!"Test123 ①②③".equals(result)) {
            System.out.println("  ✗ Failed number preservation");
            System.out.println("    Expected: 'Test123 ①②③', Got: '" + result + "'");
            passed = false;
        } else {
            System.out.println("  ✓ Numbers preserved");
        }
        
        // Test empty string
        result = StringUtils.removeUnicodeCharacters("");
        if (!"".equals(result)) {
            System.out.println("  ✗ Failed empty string");
            passed = false;
        } else {
            System.out.println("  ✓ Empty string");
        }
        
        // Test null check (method may throw exception, which is acceptable)
        try {
            result = StringUtils.removeUnicodeCharacters(null);
            if (result == null) {
                System.out.println("  ✓ Null input handled");
            } else if ("".equals(result)) {
                System.out.println("  ✓ Null input returns empty string");
            } else {
                System.out.println("  ✗ Unexpected null handling");
                passed = false;
            }
        } catch (NullPointerException e) {
            System.out.println("  ⚠ Null input throws NPE (acceptable if documented)");
        }
        
        return passed;
    }
    
    private static boolean benchmarkPerformance() {
        System.out.println("\n--- Performance Benchmark ---");
        boolean passed = true;
        
        // Prepare test data - realistic samples from search results
        String[] testStrings = {
            "Ubuntu    Linux    20.04    ISO    Download",
            "The    Quick    Brown    Fox    Jumps    Over    The    Lazy    Dog",
            "Movie Title (2024) 1080p BluRay x264 [YTS.MX]",
            "Test\t\tString\n\nWith\r\rMultiple\t\nWhitespace",
            "Short",
            "                                          ",  // lots of spaces
            "A B C D E F G H I J K L M N O P Q R S T U V W X Y Z",
            "Search Result With Many Spaces    And    Tabs\tAnd\tNewlines\n",
            "Album - Artist - Song Title [2024] (320kbps) {FLAC}",
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor"
        };
        
        final int ITERATIONS = 10000;
        
        // Benchmark removeDoubleSpaces
        long startTime = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            for (String s : testStrings) {
                String result = StringUtils.removeDoubleSpaces(s);
                if (result == null && s != null) {
                    System.out.println("  ✗ removeDoubleSpaces returned null unexpectedly");
                    passed = false;
                }
            }
        }
        long endTime = System.nanoTime();
        
        long duration = endTime - startTime;
        double durationMs = duration / 1_000_000.0;
        double avgTimePerCall = duration / (double)(ITERATIONS * testStrings.length);
        
        System.out.println("  removeDoubleSpaces:");
        System.out.println("    Total: " + String.format("%.2f", durationMs) + " ms for " + 
                          (ITERATIONS * testStrings.length) + " calls");
        System.out.println("    Average: " + String.format("%.0f", avgTimePerCall) + " ns per call");
        
        // Benchmark removeUnicodeCharacters
        String[] unicodeTestStrings = {
            "Hello World 123!",
            "Café résumé naïve",
            "Hello мир 世界 مرحبا",
            "Test\u0001\u0002\u0003String",
            "Movie (2024) [1080p]",
            "Artist - Album - Song Title",
            "Normal ASCII text without special characters",
            "Mixed: English, Español, Français, 日本語",
            "File_Name-With.Special!Chars@2024#",
            "Lorem ipsum dolor sit amet consectetur adipiscing elit"
        };
        
        startTime = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            for (String s : unicodeTestStrings) {
                String result = StringUtils.removeUnicodeCharacters(s);
                if (result == null && s != null) {
                    System.out.println("  ✗ removeUnicodeCharacters returned null unexpectedly");
                    passed = false;
                }
            }
        }
        endTime = System.nanoTime();
        
        duration = endTime - startTime;
        durationMs = duration / 1_000_000.0;
        avgTimePerCall = duration / (double)(ITERATIONS * unicodeTestStrings.length);
        
        System.out.println("  removeUnicodeCharacters:");
        System.out.println("    Total: " + String.format("%.2f", durationMs) + " ms for " + 
                          (ITERATIONS * unicodeTestStrings.length) + " calls");
        System.out.println("    Average: " + String.format("%.0f", avgTimePerCall) + " ns per call");
        
        // Performance should be reasonable - less than 10µs per call for typical strings
        if (avgTimePerCall > 10000) {
            System.out.println("  ⚠ Performance may be slower than expected");
        } else {
            System.out.println("  ✓ Performance is good");
        }
        
        return passed;
    }
}
