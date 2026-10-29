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
import android.os.Bundle;
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
  public void checkingLeavesTheStateUnchanged() {
    Bundle state = new Bundle();
    state.putByteArray("big", new byte[250 * 1024]);
    state.putInt("position", 7);

    SavedStateSizes.check("TransfersFragment", state);

    assertEquals(2, state.size());
    assertEquals(7, state.getInt("position"));
    assertEquals(250 * 1024, state.getByteArray("big").length);
  }
}
