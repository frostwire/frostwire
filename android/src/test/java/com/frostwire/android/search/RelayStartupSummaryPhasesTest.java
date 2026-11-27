/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Every startup phase and state has its own line in the settings summary. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class RelayStartupSummaryPhasesTest {

  private final Application context = ApplicationProvider.getApplicationContext();

  private static RelayStartupTracker.Snapshot starting(RelayStartupTracker.Phase phase) {
    return new RelayStartupTracker.Snapshot(RelayStartupTracker.State.STARTING, phase, 5, "");
  }

  @Test
  public void aMissingStatusMeansThereIsNoService() {
    assertEquals(
        context.getString(R.string.distributed_stack_no_service),
        RelayStartupSummary.render(context, null));
  }

  @Test
  public void eachStartingPhaseNamesWhatTheStackIsDoing() {
    assertTrue(
        RelayStartupSummary.render(context, starting(RelayStartupTracker.Phase.QUEUED))
            .contains(context.getString(R.string.waiting)));
    assertTrue(
        RelayStartupSummary.render(context, starting(RelayStartupTracker.Phase.IDENTITY))
            .contains(context.getString(R.string.distributed_identity_header)));
    assertTrue(
        RelayStartupSummary.render(context, starting(RelayStartupTracker.Phase.KARMA))
            .contains(context.getString(R.string.distributed_identity_karma)));
    assertTrue(
        RelayStartupSummary.render(context, starting(RelayStartupTracker.Phase.SERVER))
            .contains(context.getString(R.string.distributed_icebridge_header)));
    assertTrue(
        RelayStartupSummary.render(context, starting(RelayStartupTracker.Phase.TRANSPORT))
            .contains(context.getString(R.string.distributed_stack_status_title)));
    assertTrue(
        RelayStartupSummary.render(context, starting(RelayStartupTracker.Phase.DISCOVERY))
            .contains(context.getString(R.string.distributed_peers_header)));
  }

  @Test
  public void aDrainingStackReportsTheTimeoutAndWaiting() {
    String text =
        RelayStartupSummary.render(
            context,
            new RelayStartupTracker.Snapshot(
                RelayStartupTracker.State.DRAINING, RelayStartupTracker.Phase.SERVER, 0, ""));
    assertEquals(
        context.getString(R.string.error_connection_timed_out)
            + "\n"
            + context.getString(R.string.waiting),
        text);
  }

  @Test
  public void aFailedStackReportsTheFailureAndHowToStartItAgain() {
    String text =
        RelayStartupSummary.render(
            context,
            new RelayStartupTracker.Snapshot(
                RelayStartupTracker.State.FAILED,
                RelayStartupTracker.Phase.TRANSPORT,
                0,
                "SocketException"));
    assertTrue(text.contains("SocketException"));
    assertTrue(text.contains(context.getString(R.string.distributed_stack_status_title)));
    assertTrue(text.endsWith(context.getString(R.string.distributed_icebridge_start)));
  }

  @Test
  public void aRunningStackIsReportedAsNotStarting() {
    String text =
        RelayStartupSummary.render(
            context,
            new RelayStartupTracker.Snapshot(
                RelayStartupTracker.State.RUNNING, RelayStartupTracker.Phase.DISCOVERY, 0, ""));
    assertEquals(context.getString(R.string.distributed_stack_not_running), text);
  }
}
