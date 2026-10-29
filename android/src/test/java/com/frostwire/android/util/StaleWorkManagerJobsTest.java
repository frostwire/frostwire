/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * frostwire/frostwire#1388: jobs that older versions scheduled for WorkManager kept the app at
 * JobScheduler's 100-job limit; WorkManager rescheduling them at process start crash-looped it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, application = Application.class)
public class StaleWorkManagerJobsTest {

  private final Context context = ApplicationProvider.getApplicationContext();
  private final JobScheduler scheduler = context.getSystemService(JobScheduler.class);

  @Test
  public void workManagerJobsAreCancelledAndOtherJobsKept() {
    schedule(1, StaleWorkManagerJobs.WORK_MANAGER_JOB_SERVICE);
    schedule(2, StaleWorkManagerJobs.WORK_MANAGER_JOB_SERVICE);
    schedule(
        3,
        "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService");

    assertEquals(2, StaleWorkManagerJobs.remove(context));

    List<JobInfo> left = scheduler.getAllPendingJobs();
    assertEquals(1, left.size());
    assertEquals(3, left.get(0).getId());
  }

  @Test
  public void workManagersDatabaseIsDeleted() {
    context.openOrCreateDatabase(StaleWorkManagerJobs.WORK_MANAGER_DATABASE, 0, null).close();
    assertTrue(
        Arrays.asList(context.databaseList()).contains(StaleWorkManagerJobs.WORK_MANAGER_DATABASE));

    StaleWorkManagerJobs.remove(context);

    assertFalse(
        Arrays.asList(context.databaseList()).contains(StaleWorkManagerJobs.WORK_MANAGER_DATABASE));
  }

  @Test
  public void nothingToRemoveIsFine() {
    assertEquals(0, StaleWorkManagerJobs.remove(context));
  }

  private void schedule(int id, String service) {
    scheduler.schedule(
        new JobInfo.Builder(id, new ComponentName(context.getPackageName(), service))
            .setMinimumLatency(60_000)
            .build());
  }
}
