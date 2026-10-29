/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.util;

import android.os.Bundle;
import android.os.Parcel;
import com.frostwire.util.Logger;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Finds which saved state is too large (frostwire/frostwire#1390).
 *
 * <p>Leaving the app sends the activity's saved state to the system in one binder transaction;
 * above about 1 MB it fails with TransactionTooLargeException, which crashes the app with no app
 * frame on the stack. When a bundle is over {@link #REPORT_BYTES}, this records a Crashlytics
 * non-fatal naming its largest keys, descending into nested bundles, so the next events say which
 * screen and key to fix.
 */
public final class SavedStateSizes {

  private static final Logger LOG = Logger.getLogger(SavedStateSizes.class);

  /** Well under the ~1 MB binder limit, which the whole activity state shares. */
  static final int REPORT_BYTES = 200 * 1024;

  private static final int MAX_DEPTH = 3;
  private static final int TOP_KEYS = 5;

  static Consumer<String> reporter =
      message -> {
        LOG.warn(message);
        try {
          FirebaseCrashlytics.getInstance().recordException(new SavedStateTooLarge(message));
        } catch (RuntimeException e) {
          LOG.warn("SavedStateSizes: could not record the report", e);
        }
      };

  private SavedStateSizes() {}

  /** Reports owner's saved state if it is over the threshold. Must not modify outState. */
  public static void check(String owner, Bundle outState) {
    if (outState == null) {
      return;
    }
    int total = sizeOf(outState);
    if (total < REPORT_BYTES) {
      return;
    }
    List<Entry> entries = new ArrayList<>();
    collect("", outState, 0, entries);
    Collections.sort(entries, (a, b) -> Integer.compare(b.bytes, a.bytes));
    StringBuilder message =
        new StringBuilder("Saved state of ").append(owner).append(": ").append(kb(total));
    for (int i = 0; i < Math.min(TOP_KEYS, entries.size()); i++) {
      message.append(i == 0 ? " — " : ", ").append(entries.get(i).path);
      message.append(" ").append(kb(entries.get(i).bytes));
    }
    reporter.accept(message.toString());
  }

  /** The leaves (non-bundle values, or bundles at MAX_DEPTH) of bundle, with their sizes. */
  static void collect(String prefix, Bundle bundle, int depth, List<Entry> out) {
    for (String key : new ArrayList<>(bundle.keySet())) {
      Object value = bundle.get(key);
      String path = prefix.isEmpty() ? key : prefix + "/" + key;
      if (value instanceof Bundle && depth < MAX_DEPTH) {
        collect(path, (Bundle) value, depth + 1, out);
      } else {
        Bundle single = new Bundle(bundle);
        single.keySet().retainAll(Collections.singleton(key));
        out.add(new Entry(path, sizeOf(single)));
      }
    }
  }

  static int sizeOf(Bundle bundle) {
    Parcel parcel = Parcel.obtain();
    try {
      parcel.writeBundle(bundle);
      return parcel.dataSize();
    } finally {
      parcel.recycle();
    }
  }

  private static String kb(int bytes) {
    return (bytes / 1024) + " KB";
  }

  static final class Entry {
    final String path;
    final int bytes;

    Entry(String path, int bytes) {
      this.path = path;
      this.bytes = bytes;
    }
  }

  /** Recorded as a Crashlytics non-fatal; never thrown. */
  static final class SavedStateTooLarge extends Exception {
    SavedStateTooLarge(String message) {
      super(message);
    }
  }
}
