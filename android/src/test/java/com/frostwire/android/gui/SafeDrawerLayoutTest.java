/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.gui.views.SafeDrawerLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * SafeDrawerLayout keeps the app alive when DrawerLayout's measure throws, and must report it to
 * Crashlytics instead of hiding it (frostwire/frostwire#1405). It lives outside gui.views, whose
 * tests the unit test task skips, and runs with a plain Application so it doesn't boot the app.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class SafeDrawerLayoutTest {
  private final List<RuntimeException> reported = new ArrayList<>();
  private Consumer<RuntimeException> original;

  @Before
  public void setUp() {
    original = SafeDrawerLayout.reporterForTesting();
    SafeDrawerLayout.setReporterForTesting(reported::add);
    SafeDrawerLayout.resetReported();
  }

  @After
  public void tearDown() {
    SafeDrawerLayout.setReporterForTesting(original);
    SafeDrawerLayout.resetReported();
  }

  @Test
  public void aFailedMeasureIsReportedOnceAndStillSetsTheMeasuredSize() {
    SafeDrawerLayout drawer = new SafeDrawerLayout(ApplicationProvider.getApplicationContext());
    // DrawerLayout throws IllegalArgumentException unless both specs are EXACTLY.
    int width = View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.AT_MOST);
    int height = View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.AT_MOST);

    drawer.measure(width, height);
    drawer.measure(width, height);

    assertEquals("only the first failure in a process is reported", 1, reported.size());
    assertTrue(reported.get(0) instanceof IllegalArgumentException);
    assertEquals(300, drawer.getMeasuredWidth());
    assertEquals(500, drawer.getMeasuredHeight());
  }

  @Test
  public void aGoodMeasureReportsNothing() {
    SafeDrawerLayout drawer = new SafeDrawerLayout(ApplicationProvider.getApplicationContext());
    drawer.measure(
        View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY));
    assertTrue(reported.isEmpty());
  }
}
