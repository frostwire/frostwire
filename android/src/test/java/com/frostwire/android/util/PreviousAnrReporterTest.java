/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.util.PreviousAnrReporter.Exit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Crashlytics reports most ANRs as "root cause unknown"; Android's record of why the process ended
 * says which kind of ANR it was, and is now passed on at the next start (GitHub #1376).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class PreviousAnrReporterTest {

  private static final class Recorder implements PreviousAnrReporter.Sink {
    final Map<String, String> keys = new LinkedHashMap<>();
    final List<String> logs = new ArrayList<>();

    @Override
    public void setCustomKey(String key, String value) {
      keys.put(key, value);
    }

    @Override
    public void log(String message) {
      logs.add(message);
    }
  }

  private Context context;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    context
        .getSharedPreferences(PreviousAnrReporter.PREFS, Context.MODE_PRIVATE)
        .edit()
        .clear()
        .commit();
  }

  @Test
  public void onlyAnrsNewerThanTheLastReportedOneCountOldestFirst() {
    List<Exit> exits =
        List.of(
            new Exit(true, 300, "Input dispatching timed out", 100),
            new Exit(false, 250, "user requested", 400),
            new Exit(true, 200, "executing service", 125),
            new Exit(true, 100, "old", 100));

    List<Exit> anrs = PreviousAnrReporter.newAnrs(exits, 150);

    assertEquals(2, anrs.size());
    assertEquals(200, anrs.get(0).timestamp());
    assertEquals(300, anrs.get(1).timestamp());
  }

  @Test
  public void anAnrIsRecordedOnceWithItsKind() {
    List<Exit> exits =
        List.of(new Exit(true, 500, "Input dispatching timed out (Waiting...)", 100));
    Recorder first = new Recorder();
    PreviousAnrReporter.report(context, exits, first);

    assertEquals(
        "Input dispatching timed out (Waiting...)", first.keys.get("previous_anr_description"));
    assertEquals("100", first.keys.get("previous_anr_importance"));
    assertEquals(1, first.logs.size());
    assertTrue(first.logs.get(0).contains("Input dispatching timed out"));

    Recorder second = new Recorder();
    PreviousAnrReporter.report(context, exits, second);
    assertTrue("the next start doesn't report it again", second.logs.isEmpty());
  }

  @Test
  public void exitsThatWereNotAnrsRecordNothing() {
    Recorder recorder = new Recorder();
    PreviousAnrReporter.report(context, List.of(new Exit(false, 900, "crash", 100)), recorder);

    assertTrue(recorder.keys.isEmpty());
    assertTrue(recorder.logs.isEmpty());
  }
}
