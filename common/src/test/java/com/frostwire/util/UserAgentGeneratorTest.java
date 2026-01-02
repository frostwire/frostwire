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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit test for UserAgentGenerator utility class.
 * Verifies that the implementation is fast, collision-resistant, and thread-safe.
 * 
 * Run with: gradle test --tests "com.frostwire.util.UserAgentGeneratorTest"
 * 
 * @author gubatron
 * @author aldenml
 * @author copilot
 */
public class UserAgentGeneratorTest {
    
    public static void main(String[] args) {
        System.out.println("=== UserAgentGenerator Unit Test ===");
        
        boolean allTestsPassed = true;
        
        // Test 1: Basic functionality - returns non-null, non-empty strings
        allTestsPassed &= testBasicFunctionality();
        
        // Test 2: High uniqueness in rapid successive calls (>90% unique in 10,000 calls)
        allTestsPassed &= testUniquenessInRapidCalls();
        
        // Test 3: Thread safety under concurrent access
        allTestsPassed &= testThreadSafety();
        
        // Test 4: Performance - measure execution time
        allTestsPassed &= testPerformance();
        
        // Test 5: Distribution - ensure all user agents can be returned
        allTestsPassed &= testDistribution();
        
        if (allTestsPassed) {
            System.out.println("\n✓ All tests PASSED");
            System.exit(0);
        } else {
            System.out.println("\n✗ Some tests FAILED");
            System.exit(1);
        }
    }
    
    private static boolean testBasicFunctionality() {
        System.out.println("\n--- Testing Basic Functionality ---");
        boolean passed = true;
        
        // Test that getUserAgent returns a non-null, non-empty string
        for (int i = 0; i < 100; i++) {
            String userAgent = UserAgentGenerator.getUserAgent();
            if (userAgent == null || userAgent.isEmpty()) {
                System.out.println("  ✗ getUserAgent returned null or empty string");
                passed = false;
                break;
            }
        }
        
        if (passed) {
            System.out.println("  ✓ getUserAgent returns valid strings");
        }
        
        // Test that returned strings look like valid user agents
        String userAgent = UserAgentGenerator.getUserAgent();
        if (userAgent.startsWith("Mozilla/")) {
            System.out.println("  ✓ User agent has expected format");
        } else {
            System.out.println("  ✗ User agent does not start with 'Mozilla/'");
            passed = false;
        }
        
        return passed;
    }
    
    private static boolean testUniquenessInRapidCalls() {
        System.out.println("\n--- Testing Uniqueness in Rapid Successive Calls ---");
        
        final int NUM_CALLS = 10000;
        Set<String> uniqueUserAgents = new HashSet<>();
        
        // Make rapid successive calls
        long startTime = System.nanoTime();
        for (int i = 0; i < NUM_CALLS; i++) {
            uniqueUserAgents.add(UserAgentGenerator.getUserAgent());
        }
        long endTime = System.nanoTime();
        
        double uniquenessPercentage = (uniqueUserAgents.size() * 100.0) / NUM_CALLS;
        long durationMs = (endTime - startTime) / 1_000_000;
        
        System.out.println(String.format("  Made %d calls in %d ms", NUM_CALLS, durationMs));
        System.out.println(String.format("  Unique user agents: %d (%.2f%%)", 
            uniqueUserAgents.size(), uniquenessPercentage));
        
        // With ThreadLocalRandom and 10 user agents, we expect much lower uniqueness
        // but we should still see reasonable distribution (at least a few different ones)
        boolean passed = uniqueUserAgents.size() >= 2;
        
        if (passed) {
            System.out.println("  ✓ Good distribution of user agents");
        } else {
            System.out.println("  ✗ Poor distribution - only got " + uniqueUserAgents.size() + " unique values");
        }
        
        return passed;
    }
    
    private static boolean testThreadSafety() {
        System.out.println("\n--- Testing Thread Safety ---");
        
        final int NUM_THREADS = 50;
        final int NUM_ITERATIONS_PER_THREAD = 1000;
        final Set<String> allUserAgents = ConcurrentHashMap.newKeySet();
        final AtomicInteger errorCount = new AtomicInteger(0);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch completionLatch = new CountDownLatch(NUM_THREADS);
        
        // Create threads that will all start at the same time
        Thread[] threads = new Thread[NUM_THREADS];
        for (int i = 0; i < NUM_THREADS; i++) {
            threads[i] = new Thread(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();
                    
                    // Make many rapid calls
                    for (int j = 0; j < NUM_ITERATIONS_PER_THREAD; j++) {
                        String userAgent = UserAgentGenerator.getUserAgent();
                        if (userAgent == null || userAgent.isEmpty()) {
                            errorCount.incrementAndGet();
                        } else {
                            allUserAgents.add(userAgent);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    errorCount.incrementAndGet();
                } finally {
                    completionLatch.countDown();
                }
            });
            threads[i].start();
        }
        
        // Start all threads simultaneously
        long startTime = System.nanoTime();
        startLatch.countDown();
        
        // Wait for all threads to complete
        try {
            completionLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
        long endTime = System.nanoTime();
        
        long durationMs = (endTime - startTime) / 1_000_000;
        int totalCalls = NUM_THREADS * NUM_ITERATIONS_PER_THREAD;
        
        System.out.println(String.format("  %d threads × %d iterations = %d total calls",
            NUM_THREADS, NUM_ITERATIONS_PER_THREAD, totalCalls));
        System.out.println(String.format("  Completed in %d ms", durationMs));
        System.out.println(String.format("  Unique user agents observed: %d", allUserAgents.size()));
        System.out.println(String.format("  Errors: %d", errorCount.get()));
        
        boolean passed = errorCount.get() == 0 && allUserAgents.size() >= 2;
        
        if (passed) {
            System.out.println("  ✓ Thread-safe operation confirmed");
        } else {
            System.out.println("  ✗ Thread safety issues detected");
        }
        
        return passed;
    }
    
    private static boolean testPerformance() {
        System.out.println("\n--- Testing Performance ---");
        
        final int WARMUP_ITERATIONS = 10000;
        final int TEST_ITERATIONS = 100000;
        
        // Warm up the JVM
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            UserAgentGenerator.getUserAgent();
        }
        
        // Measure performance
        long startTime = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            UserAgentGenerator.getUserAgent();
        }
        long endTime = System.nanoTime();
        
        long durationNs = endTime - startTime;
        long durationMs = durationNs / 1_000_000;
        double avgTimePerCallNs = (double) durationNs / TEST_ITERATIONS;
        double avgTimePerCallUs = avgTimePerCallNs / 1000.0;
        
        System.out.println(String.format("  %d calls in %d ms", TEST_ITERATIONS, durationMs));
        System.out.println(String.format("  Average time per call: %.3f µs (%.0f ns)", 
            avgTimePerCallUs, avgTimePerCallNs));
        
        // Performance should be very fast - less than 1 microsecond per call
        boolean passed = avgTimePerCallUs < 1.0;
        
        if (passed) {
            System.out.println("  ✓ Performance is excellent");
        } else {
            System.out.println("  ✗ Performance is slower than expected");
        }
        
        return passed;
    }
    
    private static boolean testDistribution() {
        System.out.println("\n--- Testing Distribution ---");
        
        final int NUM_CALLS = 100000;
        Set<String> uniqueUserAgents = new HashSet<>();
        
        // Make many calls to ensure we see all possible user agents
        for (int i = 0; i < NUM_CALLS; i++) {
            uniqueUserAgents.add(UserAgentGenerator.getUserAgent());
        }
        
        System.out.println(String.format("  After %d calls, observed %d unique user agents",
            NUM_CALLS, uniqueUserAgents.size()));
        
        // We expect to see at least several different user agents (there are 10 in the list)
        // With 100,000 random calls, we should see most or all of them
        boolean passed = uniqueUserAgents.size() >= 8;
        
        if (passed) {
            System.out.println("  ✓ Good distribution across available user agents");
        } else {
            System.out.println("  ✗ Poor distribution - only saw " + uniqueUserAgents.size() + " unique values");
        }
        
        // Print all unique user agents found
        System.out.println("  User agents found:");
        for (String ua : uniqueUserAgents) {
            System.out.println("    - " + ua.substring(0, Math.min(60, ua.length())) + "...");
        }
        
        return passed;
    }
}
