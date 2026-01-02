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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * TaskThrottle provides thread-safe task throttling functionality to prevent tasks from being
 * executed too frequently. It maintains a lock-free map of task names to their last submission
 * timestamps and ensures minimum intervals between task executions.
 * 
 * <p>This implementation uses {@link ConcurrentHashMap} for lock-free concurrent access,
 * eliminating the global monitor bottleneck present in synchronized collections like {@link java.util.Hashtable}.
 * 
 * <p><b>Thread Safety:</b> All methods are thread-safe and can be called from multiple threads
 * concurrently without external synchronization.
 * 
 * <p><b>Memory Management:</b> The class automatically recycles old task entries that haven't
 * been accessed for {@link #TASK_RECYCLE_INTERVAL_IN_MS} to prevent unbounded memory growth.
 * 
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * // Throttle a search operation to run at most once per second
 * if (TaskThrottle.isReadyToSubmitTask("search-operation", 1000)) {
 *     // Execute the search
 *     performSearch();
 * }
 * 
 * // Check when a task was last submitted
 * long lastSubmission = TaskThrottle.getLastSubmissionTimestamp("search-operation");
 * if (lastSubmission != -1) {
 *     System.out.println("Last run: " + (System.currentTimeMillis() - lastSubmission) + "ms ago");
 * }
 * }</pre>
 * 
 * @author Angel Leon (@gubatron)
 */
public final class TaskThrottle {
    /**
     * Debug flag to enable detailed profiling logs. When false, only essential recycling
     * information is logged at INFO level. Set to true during development to track task
     * submission patterns and hit counts.
     */
    private final static boolean PROFILING_ENABLED = false;
    
    /**
     * Logger instance for this class.
     */
    private final static Logger LOG = Logger.getLogger(TaskThrottle.class);
    
    /**
     * Thread-safe map storing task names to their last submission timestamps.
     * Uses ConcurrentHashMap for lock-free reads and atomic updates without global monitor contention.
     */
    private final static ConcurrentHashMap<String, Long> asyncTaskSubmissionTimestampMap = new ConcurrentHashMap<>();
    
    /**
     * Thread-safe map storing task names to their hit counts. Only used when {@link #PROFILING_ENABLED} is true.
     * Uses ConcurrentHashMap with LongAdder values for high-performance concurrent increments.
     */
    private final static ConcurrentHashMap<String, LongAdder> tasksHitsMap = new ConcurrentHashMap<>();
    
    /**
     * Interval in milliseconds between recycling attempts. The recycling process removes
     * stale task entries to prevent unbounded memory growth. Default is 30 seconds.
     */
    private final static long RECYCLE_SUBMISSION_TIME_MAP_INTERVAL = 30 * 1000;
    
    /**
     * Age threshold in milliseconds for recycling task entries. Tasks not accessed for longer
     * than this duration will be removed during the recycling process. Default is 10 seconds.
     */
    private final static long TASK_RECYCLE_INTERVAL_IN_MS = 10000;
    
    /**
     * Timestamp of the last recycling operation. Uses AtomicLong for thread-safe updates
     * without requiring synchronization.
     */
    private static final AtomicLong lastRecycleTimestamp = new AtomicLong(-1);

    /**
     * Checks if a task is ready to be submitted based on the minimum interval requirement.
     * If the task is ready, atomically updates its submission timestamp.
     * 
     * <p>This method is the primary entry point for throttling task submissions. It checks
     * whether sufficient time has passed since the task was last submitted, and if so,
     * records the current timestamp and returns true. Otherwise, it returns false to
     * indicate the task should be throttled.
     * 
     * <p><b>Thread Safety:</b> This method is thread-safe and uses atomic operations to
     * ensure correct behavior under concurrent access from multiple threads.
     * 
     * @param taskName the unique identifier for the task being throttled
     * @param minIntervalInMillis the minimum time interval in milliseconds that must pass
     *                            between successive submissions of this task
     * @return {@code true} if the task can be submitted now (and its timestamp has been updated),
     *         {@code false} if the task should be throttled (insufficient time has passed)
     */
    public static boolean isReadyToSubmitTask(final String taskName, final long minIntervalInMillis) {
        tryRecycling();
        final long now = System.currentTimeMillis();
        
        // Use computeIfAbsent for atomic first-time insertion
        Long existingTimestamp = asyncTaskSubmissionTimestampMap.get(taskName);
        
        if (existingTimestamp == null) {
            // First submission - attempt atomic insertion
            Long previous = asyncTaskSubmissionTimestampMap.putIfAbsent(taskName, now);
            if (previous == null) {
                // We successfully inserted - this is the first submission
                if (PROFILING_ENABLED) {
                    LOG.info("isReadyToSubmitTask(): " + taskName + " can be submitted for the first time");
                }
                profileHit(taskName);
                return true;
            }
            // Another thread beat us to it, use their timestamp
            existingTimestamp = previous;
        }
        
        // Check if enough time has passed
        long delta = now - existingTimestamp;
        if (delta >= minIntervalInMillis) {
            // Enough time has passed - update timestamp atomically
            asyncTaskSubmissionTimestampMap.put(taskName, now);
            if (PROFILING_ENABLED) {
                LOG.info("isReadyToSubmitTask(): " + taskName + " can be submitted again, satisfactory delta:" + delta + " ms");
            }
            profileHit(taskName);
            return true;
        }
        
        if (PROFILING_ENABLED) {
            LOG.info("isReadyToSubmitTask(): " + taskName + " too soon, sent only " + delta + " ms ago, min interval required: " + minIntervalInMillis + " ms", true);
        }
        return false;
    }

    /**
     * Retrieves the last submission timestamp for a given task.
     * 
     * <p>This method can be used to check when a task was last submitted without affecting
     * its throttling state.
     * 
     * @param taskName the unique identifier for the task
     * @return the timestamp in milliseconds when the task was last submitted,
     *         or {@code -1} if the task has never been submitted
     */
    public static long getLastSubmissionTimestamp(final String taskName) {
        Long timestamp = asyncTaskSubmissionTimestampMap.get(taskName);
        return timestamp != null ? timestamp : -1L;
    }

    /**
     * Records a successful task submission for profiling purposes.
     * Only active when {@link #PROFILING_ENABLED} is true.
     * 
     * <p>Uses {@link LongAdder} for high-performance concurrent increments without contention.
     * 
     * @param taskName the name of the task to record a hit for
     */
    private static void profileHit(final String taskName) {
        if (!PROFILING_ENABLED) {
            return;
        }
        // computeIfAbsent is thread-safe and will only create one LongAdder per task
        tasksHitsMap.computeIfAbsent(taskName, k -> new LongAdder()).increment();
    }

    /**
     * Dumps profiling statistics for all tracked tasks to the log.
     * Only active when {@link #PROFILING_ENABLED} is true.
     * 
     * <p>The output includes hit counts for each task, sorted by frequency.
     */
    private static void dumpTaskProfile() {
        if (!PROFILING_ENABLED) {
            return;
        }
        LOG.info("dumpTaskProfile(): ==============================================================");
        
        // Create a list of task names with their hit counts
        List<Entry<String, Long>> taskHitsList = new ArrayList<>();
        tasksHitsMap.forEach((taskName, adder) -> {
            taskHitsList.add(new java.util.AbstractMap.SimpleEntry<>(taskName, adder.sum()));
        });
        
        // Sort by hit count
        Collections.sort(taskHitsList, (o1, o2) -> Long.compare(o1.getValue(), o2.getValue()));
        
        for (Entry<String, Long> entry : taskHitsList) {
            LOG.info("dumpTaskProfile(): " + entry.getKey() + " -> " + entry.getValue() + " hits");
        }
        LOG.info("dumpTaskProfile(): ==============================================================");
    }

    /**
     * Attempts to recycle (remove) stale task entries from the submission timestamp map.
     * 
     * <p>This method is called periodically from {@link #isReadyToSubmitTask} to prevent
     * unbounded memory growth. It removes entries that haven't been accessed for longer
     * than {@link #TASK_RECYCLE_INTERVAL_IN_MS}.
     * 
     * <p><b>Concurrency:</b> Uses lock-free iteration with {@link ConcurrentHashMap#forEach}
     * to avoid copying the key set and blocking other threads. Multiple threads may attempt
     * recycling concurrently, but only one will succeed per interval due to the atomic
     * timestamp check.
     * 
     * <p><b>Performance:</b> This implementation eliminates the need for synchronized blocks
     * and HashSet copies, reducing allocations and contention.
     */
    private static void tryRecycling() {
        if (asyncTaskSubmissionTimestampMap.isEmpty()) {
            return;
        }
        
        final long now = System.currentTimeMillis();
        final long lastRecycle = lastRecycleTimestamp.get();
        
        // Check if it's time to recycle using atomic compare-and-set to ensure only one thread performs recycling
        if (now - lastRecycle > RECYCLE_SUBMISSION_TIME_MAP_INTERVAL) {
            // Try to atomically claim the recycling task
            if (!lastRecycleTimestamp.compareAndSet(lastRecycle, now)) {
                // Another thread is already recycling or just finished
                return;
            }
            
            // Dump profiling info if enabled (gated behind PROFILING_ENABLED)
            dumpTaskProfile();
            
            // Collect keys to recycle using lock-free iteration
            ArrayList<String> keysToRecycle = new ArrayList<>();
            final int[] numKeysBeforeRecycle = {0}; // Use array to allow modification in lambda
            
            // Use forEach for lock-free iteration - no need to copy keySet()
            asyncTaskSubmissionTimestampMap.forEach((taskName, submissionTimestamp) -> {
                numKeysBeforeRecycle[0]++;
                if (submissionTimestamp != null) {
                    long delta = now - submissionTimestamp;
                    if (delta > TASK_RECYCLE_INTERVAL_IN_MS) {
                        // Only log when PROFILING_ENABLED is true to reduce log noise in production
                        if (PROFILING_ENABLED) {
                            LongAdder hitCounter = tasksHitsMap.get(taskName);
                            long hits = hitCounter != null ? hitCounter.sum() : 0;
                            LOG.info("Recycling " + taskName + " with " + hits + " hits, last used " + delta + " ms ago");
                        }
                        keysToRecycle.add(taskName);
                    }
                }
            });
            
            int numKeysToRecycle = keysToRecycle.size();
            if (numKeysToRecycle > 0) {
                double recycleRatio = numKeysBeforeRecycle[0] > 0 ? 
                    100.0 * numKeysToRecycle / numKeysBeforeRecycle[0] : 0.0;
                    
                // Always log recycling summary (not gated by PROFILING_ENABLED)
                LOG.info("Recycling " + numKeysToRecycle + " tasks out of " + 
                         numKeysBeforeRecycle[0] + " total tasks (freed " + 
                         String.format("%.1f", recycleRatio) + "%)");
                
                // Remove stale entries - ConcurrentHashMap.remove() is thread-safe
                for (String task : keysToRecycle) {
                    asyncTaskSubmissionTimestampMap.remove(task);
                    // Also clean up profiling data if enabled
                    if (PROFILING_ENABLED) {
                        tasksHitsMap.remove(task);
                    }
                }
            }
        }
    }
}
