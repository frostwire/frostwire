/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * frostwire/frostwire#1390: a TransactionTooLargeException when leaving the app carries no app
 * frame. SavedStateSizes reports which screen and key made the saved state large.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SavedStateSizesTest {

  private final AtomicReference<String> reported = new AtomicReference<>();
  private Consumer<String> original;

  @Before
  public void captureReports() {
    original = SavedStateSizes.reporter;
    SavedStateSizes.reporter = reported::set;
    SavedStateSizes.resetReported();
  }

  @After
  public void restore() {
    SavedStateSizes.reporter = original;
  }

  @Test
  public void aSmallStateIsNotReported() {
    Bundle state = new Bundle();
    state.putString("query", "frostwire");

    SavedStateSizes.check("SearchFragment", state);

    assertNull(reported.get());
  }

  @Test
  public void aLargeStateNamesItsLargestNestedKeyFirst() {
    Bundle fragments = new Bundle();
    fragments.putByteArray("results", new byte[300 * 1024]);
    fragments.putString("query", "frostwire");
    Bundle state = new Bundle();
    state.putBundle("android:support:fragments", fragments);
    state.putByteArray("thumbnail", new byte[20 * 1024]);

    SavedStateSizes.check("MainActivity", state);

    String message = reported.get();
    assertTrue(message, message.startsWith("Saved state of MainActivity: 32"));
    assertTrue(
        message, message.contains("— android:support:fragments/results 300 KB, thumbnail 20 KB"));
  }

  @Test
  public void eachOwnerIsReportedOncePerProcess() {
    Bundle state = new Bundle();
    state.putByteArray("big", new byte[250 * 1024]);
    SavedStateSizes.check("TransfersFragment", state);
    reported.set(null);

    SavedStateSizes.check("TransfersFragment", state);

    assertNull("leaving the app again reported the same screen again", reported.get());
  }

  @Test
  public void aRestoredValueThatCannotBeReadDoesNotBreakTheSave() {
    // A fragment's restored state, still parcelled, holding a value whose class can't be loaded
    // (a custom Parcelable read with the wrong class loader): reading it throws.
    Bundle restored = new Bundle();
    restored.putParcelable("adapter", new Unreadable());
    restored.putByteArray("results", new byte[300 * 1024]);
    Parcel parcel = Parcel.obtain();
    parcel.writeBundle(restored);
    parcel.setDataPosition(0);
    Bundle parcelled = parcel.readBundle(getClass().getClassLoader());
    parcel.recycle();
    Bundle state = new Bundle();
    state.putBundle("android:support:fragments", parcelled);

    SavedStateSizes.check("MainActivity", state);

    // The system's own save copies the parcelled bytes; it must still work after the check.
    assertTrue(SavedStateSizes.sizeOf(state) > 300 * 1024);
  }

  @Test
  public void checkingLeavesTheStateUnchanged() {
    Bundle state = new Bundle();
    state.putByteArray("big", new byte[250 * 1024]);
    state.putInt("position", 7);

    SavedStateSizes.check("TransfersFragment", state);

    assertEquals(2, state.size());
    assertEquals(7, state.getInt("position"));
    assertEquals(250 * 1024, state.getByteArray("big").length);
  }

  /** Writes fine; reading it back fails, like a Parcelable whose class can't be loaded. */
  public static final class Unreadable implements Parcelable {
    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeInt(1);
    }

    public static final Creator<Unreadable> CREATOR =
        new Creator<Unreadable>() {
          @Override
          public Unreadable createFromParcel(Parcel source) {
            throw new BadParcelableException("ClassNotFoundException when unmarshalling");
          }

          @Override
          public Unreadable[] newArray(int size) {
            return new Unreadable[size];
          }
        };
  }
}
