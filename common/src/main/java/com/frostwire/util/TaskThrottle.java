/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

public final class TaskThrottle {
    private final static boolean PROFILING_ENABLED = false;
    private final static Logger LOG = Logger.getLogger(TaskThrottle.class);
    private final static Hashtable<String, Long> asyncTaskSubmissionTimestampMap = new Hashtable<>();
    private final static Hashtable<String, Integer> tasksHitsMap = new Hashtable<>(); //used if profiling enabled
    private final static long RECYCLE_SUBMISSION_TIME_MAP_INTERVAL = 30 * 1000; //recycle every 1 minute
    private final static long TASK_RECYCLE_INTERVAL_IN_MS = 10000;
    private static long lastRecycleTimestamp = -1;
    private final static Object recycleLock = new Object();

    /**
     * Checks if it's not too early to submit this task again. Updates the Map<TaskName -> TimestampLastSubmitted> when it's ready, assuming the task will be launched right after checking if(ready) async(theTask)
     *
     * @param taskName
     * @param minIntervalInMillis
     * @return
     */
    public static boolean isReadyToSubmitTask(final String taskName, final long minIntervalInMillis) {
        tryRecycling();
        final long now = System.currentTimeMillis();
        if (!asyncTaskSubmissionTimestampMap.containsKey(taskName)) {
            if (PROFILING_ENABLED) {
                LOG.info("isReadyToSubmitTask(): " + taskName + " can be submitted for the first time");
            }
            synchronized(recycleLock) {
                asyncTaskSubmissionTimestampMap.put(taskName, now);
            }
            profileHit(taskName);
            return true;
        }
        long delta = now - asyncTaskSubmissionTimestampMap.get(taskName);
        if (delta >= minIntervalInMillis) {
            if (PROFILING_ENABLED) {
                LOG.info("isReadyToSubmitTask(): " + taskName + " can be submitted again, satisfactory delta:" + delta + " ms");
            }
            asyncTaskSubmissionTimestampMap.put(taskName, now);
            profileHit(taskName);
            return true;
        }
        if (PROFILING_ENABLED) {
            LOG.info("isReadyToSubmitTask(): " + taskName + " too soon, sent only " + delta + " ms ago, min interval required: " + minIntervalInMillis + " ms", true);
        }
        return false;
    }

    public static long getLastSubmissionTimestamp(final String taskName) {
        if (!asyncTaskSubmissionTimestampMap.containsKey(taskName)) {
            return (long) -1;
        }
        return asyncTaskSubmissionTimestampMap.get(taskName);
    }

    private static void profileHit(final String taskName) {
        if (!PROFILING_ENABLED) {
            return;
        }
        if (tasksHitsMap.containsKey(taskName)) {
            tasksHitsMap.put(taskName, 1 + tasksHitsMap.get(taskName));
        } else {
            tasksHitsMap.put(taskName, 1);
        }

    }

    private static void dumpTaskProfile() {
        if (!PROFILING_ENABLED) {
            return;
        }
        LOG.info("dumpTaskProfile(): ==============================================================");
        Set<Entry<String, Integer>> taskHitsMapEntries = tasksHitsMap.entrySet();
        List<Entry<String, Integer>> taskHitsList = new ArrayList<>(taskHitsMapEntries);
        Collections.sort(taskHitsList, (o1, o2) -> Integer.compare(o1.getValue(), o2.getValue()));
        for (Entry<String, Integer> entry : taskHitsList) {
            LOG.info("dumpTaskProfile(): " + entry.getKey() + " -> " + entry.getValue() + " hits");
        }
        LOG.info("dumpTaskProfile(): ==============================================================");
    }

    private static void tryRecycling() {
        if (asyncTaskSubmissionTimestampMap.size() == 0) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now - lastRecycleTimestamp > RECYCLE_SUBMISSION_TIME_MAP_INTERVAL) {
            dumpTaskProfile();
            ArrayList<String> keysToRecycle = new ArrayList<>();
            int numKeysBeforeRecycle = 0;

            Set<String> taskNames;
            synchronized (recycleLock) {
                // Android implementations of Hashtable.keySet()
                // may or may not return a synchronized set, and in any event
                // we don't want to share the set instance in different threads
                // Therefore we ensure a copy after seeing 5 crashed users appear among 40k installations.
                taskNames = new HashSet<>(asyncTaskSubmissionTimestampMap.keySet());
            }
            for (String taskName : taskNames) {
                numKeysBeforeRecycle++;
                Long submissionTimestamp = asyncTaskSubmissionTimestampMap.get(taskName);
                if (submissionTimestamp == null) {
                    continue;
                }
                long delta = now - submissionTimestamp;
                if (delta > TASK_RECYCLE_INTERVAL_IN_MS) {
                    if (PROFILING_ENABLED) {
                        LOG.info("Recycling " + taskName + " with " + tasksHitsMap.get(taskName) + " hits, last used " + delta + " ms ago");
                    } else {
                        LOG.info("Recycling " + taskName + ", last used " + delta + " ms ago");
                    }
                    keysToRecycle.add(taskName);
                }
            }
            lastRecycleTimestamp = now;
            int numKeysToRecycle = keysToRecycle.size();
            double recycleRatio = 100 * numKeysToRecycle / numKeysBeforeRecycle;
            if (numKeysToRecycle > 0) {
                LOG.info("Recycling " + numKeysToRecycle + " tasks out of " + numKeysBeforeRecycle + " total tasks (freed  " + recycleRatio + "%)");
            }

            synchronized (recycleLock) {
                for (String task : keysToRecycle) {
                    try {
                        asyncTaskSubmissionTimestampMap.remove(task);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }
}
