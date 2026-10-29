/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.util;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import com.frostwire.util.Logger;

/**
 * Removes what WorkManager left behind (frostwire/frostwire#1388).
 *
 * <p>FrostWire no longer uses WorkManager. Older versions persisted work in WorkManager's database
 * and JobScheduler; WorkManager rescheduled it at every process start until JobScheduler refused
 * with "Apps may not schedule more than 100 distinct jobs", crash-looping the app in the
 * background. Its jobs stay registered after an update, using up the app's job slots, so they are
 * cancelled here, and the database is deleted.
 */
public final class StaleWorkManagerJobs {

  private static final Logger LOG = Logger.getLogger(StaleWorkManagerJobs.class);

  static final String WORK_MANAGER_JOB_SERVICE =
      "androidx.work.impl.background.systemjob.SystemJobService";
  static final String WORK_MANAGER_DATABASE = "androidx.work.workdb";

  private StaleWorkManagerJobs() {}

  /** Cancels WorkManager's jobs and deletes its database. Returns how many jobs were cancelled. */
  public static int remove(Context context) {
    int cancelled = 0;
    try {
      JobScheduler scheduler = context.getSystemService(JobScheduler.class);
      if (scheduler != null) {
        for (JobInfo job : scheduler.getAllPendingJobs()) {
          if (job.getService() != null
              && WORK_MANAGER_JOB_SERVICE.equals(job.getService().getClassName())) {
            scheduler.cancel(job.getId());
            cancelled++;
          }
        }
      }
      if (context.deleteDatabase(WORK_MANAGER_DATABASE) || cancelled > 0) {
        LOG.info("StaleWorkManagerJobs: cancelled " + cancelled + " jobs, removed old database");
      }
    } catch (Throwable t) {
      LOG.warn("StaleWorkManagerJobs: cleanup failed", t);
    }
    return cancelled;
  }
}
