/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.res.Configuration;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.test.core.app.ApplicationProvider;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

/** The view helpers of ApolloUtils: orientation, the long-press hint and post-layout work. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class ApolloUtilsViewsTest {

  @Test
  public void landscapeIsReportedFromTheConfiguration() {
    Application app = ApplicationProvider.getApplicationContext();
    app.getResources().getConfiguration().orientation = Configuration.ORIENTATION_PORTRAIT;
    assertFalse(ApolloUtils.isLandscape(app));

    app.getResources().getConfiguration().orientation = Configuration.ORIENTATION_LANDSCAPE;
    assertTrue(ApolloUtils.isLandscape(app));
  }

  @Test
  public void aViewsDescriptionIsShownAsAToastNearIt() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    View view = new View(activity);
    view.setContentDescription("Play");
    activity.setContentView(view);

    ApolloUtils.showCheatSheet(view);

    assertEquals("Play", ShadowToast.getTextOfLatestToast());
    assertEquals(Toast.LENGTH_SHORT, ShadowToast.getLatestToast().getDuration());
  }

  @Test
  @Config(sdk = 28)
  public void beforeAndroid11TheToastIsPlacedAboveOrBelowTheView() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    View view = new View(activity);
    view.setContentDescription("Next");
    activity.setContentView(view);

    ApolloUtils.showCheatSheet(view);

    assertEquals("Next", ShadowToast.getTextOfLatestToast());
    assertEquals(
        "a view at the top of the screen gets its hint below it",
        Gravity.TOP | Gravity.CENTER_HORIZONTAL,
        ShadowToast.getLatestToast().getGravity());
  }

  @Test
  public void workAfterLayoutRunsOnceTheViewIsLaidOut() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    FrameLayout view = new FrameLayout(activity);
    activity.setContentView(view);
    AtomicInteger runs = new AtomicInteger();

    ApolloUtils.doAfterLayout(view, runs::incrementAndGet);
    view.getViewTreeObserver().dispatchOnGlobalLayout();
    view.getViewTreeObserver().dispatchOnGlobalLayout();

    assertEquals("and only once", 1, runs.get());
  }
}
