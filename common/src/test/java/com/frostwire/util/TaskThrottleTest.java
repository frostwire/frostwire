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

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for TaskThrottle to verify correct behavior and performance
 * under concurrent access patterns.
 */
public class TaskThrottleTest {

    @Test
    public void testBasicThrottling() throws InterruptedException {
        String taskName = "test-basic-task";
        long minInterval = 1000; // 1 second
        
        // First submission should succeed
        assertTrue(TaskThrottle.isReadyToSubmitTask(taskName, minInterval));
        
        // Immediate resubmission should fail
        assertFalse(TaskThrottle.isReadyToSubmitTask(taskName, minInterval));
        
        // Wait for the interval to pass
        Thread.sleep(minInterval + 100);
        
        // Should be able to submit again
        assertTrue(TaskThrottle.isReadyToSubmitTask(taskName, minInterval));
    }

    @Test
    public void testGetLastSubmissionTimestamp() {
        String taskName = "test-timestamp-task";
        long minInterval = 500;
        
        // Before submission, should return -1
        assertEquals(-1, TaskThrottle.getLastSubmissionTimestamp(taskName));
        
        // After submission, should have a valid timestamp
        long beforeSubmit = System.currentTimeMillis();
        assertTrue(TaskThrottle.isReadyToSubmitTask(taskName, minInterval));
        long afterSubmit = System.currentTimeMillis();
        
        long timestamp = TaskThrottle.getLastSubmissionTimestamp(taskName);
        assertTrue(timestamp >= beforeSubmit && timestamp <= afterSubmit);
    }

    @Test
    public void testConcurrentAccess() throws InterruptedException {
        int numThreads = 8;
        int operationsPerThread = 100;
        long minInterval = 10; // Short interval for faster testing
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        String taskName = "concurrent-task-" + (threadId % 4); // Use 4 different task names
                        
                        if (TaskThrottle.isReadyToSubmitTask(taskName, minInterval)) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                        
                        // Small random sleep to create varied access patterns
                        if (j % 10 == 0) {
                            Thread.sleep(minInterval + 5);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        // Start all threads at once
        startLatch.countDown();
        
        // Wait for completion
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Test timed out");
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        
        // Verify that we had both successes and failures (throttling is working)
        int totalOperations = numThreads * operationsPerThread;
        assertEquals(totalOperations, successCount.get() + failureCount.get());
        assertTrue(successCount.get() > 0, "Should have some successful submissions");
        assertTrue(failureCount.get() > 0, "Should have some throttled submissions");
        
        System.out.println("Concurrent test results: " + successCount.get() + " successes, " + 
                          failureCount.get() + " throttled out of " + totalOperations + " total");
    }

    @Test
    public void testHighContentionScenario() throws InterruptedException {
        int numThreads = 16;
        int operationsPerThread = 50;
        String sharedTaskName = "high-contention-task"; // All threads compete for same task
        long minInterval = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicLong totalWaitTime = new AtomicLong(0);
        
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        long startTime = System.nanoTime();
                        TaskThrottle.isReadyToSubmitTask(sharedTaskName, minInterval);
                        long endTime = System.nanoTime();
                        
                        totalWaitTime.addAndGet(endTime - startTime);
                        
                        // Small sleep to allow other threads to compete
                        Thread.sleep(5);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Test timed out");
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        
        long avgWaitTimeNanos = totalWaitTime.get() / (numThreads * operationsPerThread);
        System.out.println("Average wait time per call: " + avgWaitTimeNanos + " ns (" + 
                          (avgWaitTimeNanos / 1_000_000.0) + " ms)");
        
        // In a well-optimized implementation, average wait time should be reasonably low
        // This serves as a baseline for comparison after optimization
    }

    @Test
    public void testMultipleTaskNames() throws InterruptedException {
        int numTasks = 20;
        long minInterval = 500;
        
        // Submit many different tasks
        for (int i = 0; i < numTasks; i++) {
            String taskName = "multi-task-" + i;
            assertTrue(TaskThrottle.isReadyToSubmitTask(taskName, minInterval));
        }
        
        // All should be throttled on immediate retry
        for (int i = 0; i < numTasks; i++) {
            String taskName = "multi-task-" + i;
            assertFalse(TaskThrottle.isReadyToSubmitTask(taskName, minInterval));
        }
        
        // Wait for interval and verify recycling might occur
        Thread.sleep(minInterval + 100);
        
        // Should be able to submit again
        for (int i = 0; i < numTasks; i++) {
            String taskName = "multi-task-" + i;
            assertTrue(TaskThrottle.isReadyToSubmitTask(taskName, minInterval));
        }
    }

    @Test
    public void testRecyclingBehavior() throws InterruptedException {
        // Submit a task
        String taskName = "recycle-test-task";
        assertTrue(TaskThrottle.isReadyToSubmitTask(taskName, 100));
        
        // Wait longer than TASK_RECYCLE_INTERVAL_IN_MS (10 seconds in the code)
        // but for test purposes, we'll just verify basic behavior
        long timestamp1 = TaskThrottle.getLastSubmissionTimestamp(taskName);
        assertTrue(timestamp1 > 0);
        
        Thread.sleep(200);
        
        // Submit again
        assertTrue(TaskThrottle.isReadyToSubmitTask(taskName, 100));
        long timestamp2 = TaskThrottle.getLastSubmissionTimestamp(taskName);
        assertTrue(timestamp2 > timestamp1);
    }
}
