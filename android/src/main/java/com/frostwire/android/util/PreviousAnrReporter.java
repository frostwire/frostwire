/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.util;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.annotation.RequiresApi;
import com.frostwire.util.Logger;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tells Crashlytics what kind of ANR ended a previous run. Crashlytics reports most of FrostWire's
 * ANRs as "root cause unknown", and the thread-name breadcrumb that used to point at the culprit is
 * gone (#1375). Android 11+ keeps a record of why the process ended; its description says whether
 * input dispatching, a service or a broadcast timed out. At startup, off the main thread, each ANR
 * not reported before is written as private Crashlytics custom keys and a log line, which travel
 * with the next report (frostwire/frostwire#1376). Nothing leaves the device otherwise.
 */
public final class PreviousAnrReporter {

  private static final Logger LOG = Logger.getLogger(PreviousAnrReporter.class);

  static final String PREFS = "previous_anr_reporter";
  static final String LAST_REPORTED = "last_reported_anr_ms";

  /** How many of the most recent exits Android is asked for. */
  static final int EXITS_READ = 5;

  /** Where the ANRs go: Crashlytics in the app, a recorder in tests. */
  interface Sink {
    void setCustomKey(String key, String value);

    void log(String message);
  }

  /** One recorded process exit. */
  record Exit(boolean anr, long timestamp, String description, int importance) {}

  private PreviousAnrReporter() {}

  /** Reports the ANRs since the last run; Android 11+ only, off the main thread. */
  public static void reportPreviousAnrs(final Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || context == null) {
      return;
    }
    try {
      report(context, exits(context), crashlyticsSink());
    } catch (Throwable t) {
      LOG.warn("reportPreviousAnrs: could not read the process exit reasons", t);
    }
  }

  @RequiresApi(Build.VERSION_CODES.R)
  static List<Exit> exits(final Context context) {
    final ActivityManager activityManager = context.getSystemService(ActivityManager.class);
    final List<Exit> exits = new ArrayList<>();
    if (activityManager == null) {
      return exits;
    }
    for (ApplicationExitInfo info :
        activityManager.getHistoricalProcessExitReasons(context.getPackageName(), 0, EXITS_READ)) {
      exits.add(
          new Exit(
              info.getReason() == ApplicationExitInfo.REASON_ANR,
              info.getTimestamp(),
              info.getDescription(),
              info.getImportance()));
    }
    return exits;
  }

  /** Writes the ANRs newer than the last one reported to {@code sink}, oldest first. */
  static void report(final Context context, final List<Exit> exits, final Sink sink) {
    final SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    final long lastReported = prefs.getLong(LAST_REPORTED, 0);
    final List<Exit> anrs = newAnrs(exits, lastReported);
    for (Exit anr : anrs) {
      final String description = anr.description() != null ? anr.description() : "";
      sink.setCustomKey("previous_anr_description", description);
      sink.setCustomKey("previous_anr_importance", String.valueOf(anr.importance()));
      sink.setCustomKey("previous_anr_time_ms", String.valueOf(anr.timestamp()));
      sink.log(
          "Previous run ended in an ANR at "
              + anr.timestamp()
              + " (importance "
              + anr.importance()
              + "): "
              + description);
    }
    if (!anrs.isEmpty()) {
      prefs.edit().putLong(LAST_REPORTED, anrs.get(anrs.size() - 1).timestamp()).apply();
    }
  }

  /** The ANRs among {@code exits} newer than {@code lastReported}, oldest first. */
  static List<Exit> newAnrs(final List<Exit> exits, final long lastReported) {
    final List<Exit> anrs = new ArrayList<>();
    for (Exit exit : exits) {
      if (exit.anr() && exit.timestamp() > lastReported) {
        anrs.add(exit);
      }
    }
    anrs.sort(Comparator.comparingLong(Exit::timestamp));
    return anrs;
  }

  private static Sink crashlyticsSink() {
    final FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
    return new Sink() {
      @Override
      public void setCustomKey(String key, String value) {
        crashlytics.setCustomKey(key, value);
      }

      @Override
      public void log(String message) {
        crashlytics.log(message);
      }
    };
  }
}
