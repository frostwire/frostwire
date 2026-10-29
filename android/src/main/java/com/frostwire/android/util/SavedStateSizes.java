/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.util;

import android.os.Bundle;
import android.os.Parcel;
import androidx.annotation.VisibleForTesting;
import com.frostwire.util.Logger;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

  @VisibleForTesting
  static Consumer<String> reporter =
      message -> {
        LOG.warn(message);
        try {
          FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
          crashlytics.setCustomKey("savedStateReport", message);
          crashlytics.recordException(new SavedStateTooLarge(message));
        } catch (RuntimeException e) {
          LOG.warn("SavedStateSizes: could not record the report", e);
        }
      };

  /** Owners already reported in this process: leaving the app repeatedly reports once. */
  private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

  private SavedStateSizes() {}

  /**
   * Reports owner's saved state if it is over the threshold, once per owner per process. It only
   * reads copies of outState, and it never throws: it runs inside the system's save.
   */
  public static void check(String owner, Bundle outState) {
    if (outState == null || REPORTED.contains(owner)) {
      return;
    }
    try {
      // A copy: the system copies a parcelled bundle's raw bytes without unparcelling it, and
      // reading the copy can't unparcel (and so can't fail on) anything in outState.
      Bundle state = new Bundle(outState);
      int total = sizeOf(state);
      if (total < REPORT_BYTES || !REPORTED.add(owner)) {
        return;
      }
      List<Entry> entries = new ArrayList<>();
      collect("", state, 0, entries);
      Collections.sort(entries, (a, b) -> Integer.compare(b.bytes, a.bytes));
      StringBuilder message =
          new StringBuilder("Saved state of ").append(owner).append(": ").append(kb(total));
      for (int i = 0; i < Math.min(TOP_KEYS, entries.size()); i++) {
        message.append(i == 0 ? " — " : ", ").append(entries.get(i).path);
        message.append(" ").append(kb(entries.get(i).bytes));
      }
      reporter.accept(message.toString());
    } catch (RuntimeException e) {
      // e.g. BadParcelableException reading a restored value with the wrong class loader. Allow
      // a later save of this owner to be measured.
      REPORTED.remove(owner);
      LOG.warn("SavedStateSizes: could not measure the saved state of " + owner, e);
    }
  }

  /**
   * The leaves (non-bundle values, or bundles at MAX_DEPTH) of bundle, which must be a copy the
   * caller owns, with their sizes.
   */
  static void collect(String prefix, Bundle bundle, int depth, List<Entry> out) {
    for (String key : new ArrayList<>(bundle.keySet())) {
      // On API 33+ a value still waiting to be read is shared with the original bundle and gets
      // read there too; that's harmless (the system then writes the read object).
      Object value = bundle.get(key);
      String path = prefix.isEmpty() ? key : prefix + "/" + key;
      if (value instanceof Bundle && depth < MAX_DEPTH) {
        collect(path, new Bundle((Bundle) value), depth + 1, out);
      } else {
        out.add(new Entry(path, sizeOfValue(value)));
      }
    }
  }

  /** One value alone, so a large state is marshalled about once, not once per key. */
  private static int sizeOfValue(Object value) {
    Parcel parcel = Parcel.obtain();
    try {
      parcel.writeValue(value);
      return parcel.dataSize();
    } finally {
      parcel.recycle();
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

  @VisibleForTesting
  static void resetReported() {
    REPORTED.clear();
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
