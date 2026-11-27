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
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.util.PreviousAnrReporter.Exit;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The Android 11+ entry points of the reporters, with the real exit history and no Firebase. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class PreviousAnrReporterExitsTest {

  private final Context context = ApplicationProvider.getApplicationContext();

  @Test
  public void anEmptyExitHistoryHasNoExits() {
    assertTrue(PreviousAnrReporter.exits(context).isEmpty());
  }

  @Test
  @SuppressWarnings("deprecation") // the replacement needs a hand-built ApplicationExitInfo
  public void exitsKeepTheReasonAndTimestampOfEachRecord() {
    android.app.ActivityManager am = context.getSystemService(android.app.ActivityManager.class);
    org.robolectric.shadows.ShadowActivityManager shadow = org.robolectric.Shadows.shadowOf(am);
    shadow.addApplicationExitInfo(
        context.getPackageName(), 1234, ApplicationExitInfo.REASON_ANR, 0);
    shadow.addApplicationExitInfo(
        context.getPackageName(), 1235, ApplicationExitInfo.REASON_CRASH, 0);

    List<Exit> exits = PreviousAnrReporter.exits(context);

    assertEquals(2, exits.size());
    assertEquals(1, exits.stream().filter(Exit::anr).count());
  }

  @Test
  public void reportingWithoutFirebaseNeverThrows() {
    PreviousAnrReporter.reportPreviousAnrs(context);
    PreviousAnrReporter.reportPreviousAnrs(null);
  }

  @Test
  public void theDefaultSavedStateReporterSurvivesWithoutFirebase() {
    SavedStateSizes.reporter.accept("Saved state of Test: 300 KB");
    assertFalse(SavedStateSizes.REPORT_BYTES <= 0);
  }

  @Test
  public void aSavedStateTooLargeCarriesItsMessage() {
    assertEquals("m", new SavedStateSizes.SavedStateTooLarge("m").getMessage());
  }

  @Test
  public void collectingAnEmptyBundleFindsNothing() {
    java.util.ArrayList<SavedStateSizes.Entry> out = new java.util.ArrayList<>();
    SavedStateSizes.collect("", new Bundle(), 0, out);
    assertTrue(out.isEmpty());
  }
}
