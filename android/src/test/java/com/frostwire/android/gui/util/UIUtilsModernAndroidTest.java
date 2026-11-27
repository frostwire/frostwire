/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import com.frostwire.android.gui.activities.MainActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** UIUtils paths that only exist on Android 11 and newer (window metrics, insets controller). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class UIUtilsModernAndroidTest {

  @Test
  public void theScreenSizeInInchesComesFromTheWindowMetricsAndDensity() {
    Activity activity = Robolectric.buildActivity(Activity.class).create().get();
    android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
    android.graphics.Rect bounds =
        activity.getWindowManager().getCurrentWindowMetrics().getBounds();
    double expected =
        Math.sqrt(
            Math.pow(bounds.width() / metrics.xdpi, 2)
                + Math.pow(bounds.height() / metrics.ydpi, 2));
    assertTrue(expected > 0);
    assertEquals(expected, UIUtils.getScreenInches(activity), 1e-9);
  }

  @Test
  public void showingTheKeyboardWithoutAFocusedViewUsesTheInsetsController() {
    Activity activity =
        Robolectric.buildActivity(Activity.class).create().start().resume().visible().get();
    // no view has focus: the call must still complete
    UIUtils.forceShowKeyboard(activity);
    UIUtils.hideKeyboardFromActivity(activity);
  }

  @Test
  public void goingToTheMainActivityOverridesTheCloseTransition() {
    Activity activity = Robolectric.buildActivity(Activity.class).create().get();
    UIUtils.goToFrostWireMainActivity(activity);
    Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
    assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
    assertTrue(activity.isFinishing());
  }
}
