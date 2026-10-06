/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import static org.junit.Assert.*;

import android.app.Application;
import com.frostwire.android.R;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class RelayStartupSummaryTest {
  @Test
  public void stoppedStackWithSavedIdentityIsNotMisrepresentedAsStarting() {
    Application context = RuntimeEnvironment.getApplication();
    var status =
        new RelayStartupTracker.Snapshot(
            RelayStartupTracker.State.STOPPED, RelayStartupTracker.Phase.IDENTITY, 0, "");
    assertEquals(
        context.getString(R.string.distributed_stack_not_running),
        RelayStartupSummary.render(context, status));
  }

  @Test
  public void failureShowsStageAndLiveRetryCountdownInsteadOfPermanentStarting() {
    Application context = RuntimeEnvironment.getApplication();
    AtomicLong now = new AtomicLong();
    RelayStartupTracker tracker = new RelayStartupTracker(now::get);
    long attempt = tracker.begin(false);
    tracker.phase(attempt, RelayStartupTracker.Phase.SERVER);
    tracker.failed(attempt, new java.net.BindException());
    tracker.finish(attempt, false);
    String initial = RelayStartupSummary.render(context, tracker.snapshot());
    assertTrue(initial.contains("BindException"));
    assertTrue(initial.contains(context.getString(R.string.distributed_icebridge_header)));
    assertTrue(initial.contains(context.getString(R.string.distributed_stack_retry_countdown, 5L)));
    now.set(2000);
    assertTrue(
        RelayStartupSummary.render(context, tracker.snapshot())
            .contains(context.getString(R.string.distributed_stack_retry_countdown, 3L)));
    assertFalse(initial.contains(context.getString(R.string.distributed_stack_starting)));
  }

  @Test
  public void actualStartupReportsPhaseAndRemainingDeadline() {
    Application context = RuntimeEnvironment.getApplication();
    RelayStartupTracker tracker = new RelayStartupTracker(() -> 0);
    long attempt = tracker.begin(false);
    tracker.phase(attempt, RelayStartupTracker.Phase.INDEX);
    String text = RelayStartupSummary.render(context, tracker.snapshot());
    assertTrue(text.contains(context.getString(R.string.distributed_stack_starting)));
    assertTrue(text.contains(context.getString(R.string.distributed_identity_shared_count)));
    assertTrue(text.contains("01:30"));
  }
}
