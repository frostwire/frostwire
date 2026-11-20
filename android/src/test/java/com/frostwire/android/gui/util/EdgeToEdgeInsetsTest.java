/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.util;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Targeting Android 15+ makes every window edge-to-edge: without these insets the toolbar sits
 * under the status bar and the last list rows and the mini player under the navigation bar.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class EdgeToEdgeInsetsTest {

  private static WindowInsetsCompat insets(Insets systemBars, Insets cutout, Insets ime) {
    return new WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.systemBars(), systemBars)
        .setInsets(WindowInsetsCompat.Type.displayCutout(), cutout)
        .setInsets(WindowInsetsCompat.Type.ime(), ime)
        .build();
  }

  private static void dispatch(View view, WindowInsetsCompat insets) {
    ViewCompat.dispatchApplyWindowInsets(view, insets);
  }

  @Test
  public void contentInsetsUseTheLargerOfNavigationBarAndKeyboardAtTheBottom() {
    Insets result =
        EdgeToEdgeInsets.contentInsets(
            insets(Insets.of(0, 24, 0, 48), Insets.NONE, Insets.of(0, 0, 0, 300)));
    assertEquals(Insets.of(0, 24, 0, 300), result);

    result =
        EdgeToEdgeInsets.contentInsets(insets(Insets.of(0, 24, 0, 48), Insets.NONE, Insets.NONE));
    assertEquals(Insets.of(0, 24, 0, 48), result);
  }

  @Test
  public void contentInsetsIncludeTheDisplayCutout() {
    Insets result =
        EdgeToEdgeInsets.contentInsets(
            insets(Insets.of(0, 24, 0, 0), Insets.of(80, 80, 0, 0), Insets.NONE));
    assertEquals(Insets.of(80, 80, 0, 0), result);
  }

  @Test
  public void contentWithoutToolbarIsPaddedOnEverySideKeepingItsOwnPadding() {
    Context context = ApplicationProvider.getApplicationContext();
    FrameLayout content = new FrameLayout(context);
    content.setPadding(4, 5, 6, 7);

    EdgeToEdgeInsets.install(content, null);
    dispatch(content, insets(Insets.of(10, 24, 12, 48), Insets.NONE, Insets.NONE));

    assertEquals(14, content.getPaddingLeft());
    assertEquals(29, content.getPaddingTop());
    assertEquals(18, content.getPaddingRight());
    assertEquals(55, content.getPaddingBottom());
  }

  @Test
  public void toolbarTakesTheStatusBarInsetAndGrowsByIt() {
    Context context = ApplicationProvider.getApplicationContext();
    FrameLayout content = new FrameLayout(context);
    View toolbar = new View(context);
    toolbar.setLayoutParams(
        new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 56));
    toolbar.setPadding(0, 2, 0, 0);
    content.addView(toolbar);

    EdgeToEdgeInsets.install(content, toolbar);
    dispatch(content, insets(Insets.of(0, 24, 0, 48), Insets.NONE, Insets.NONE));

    assertEquals("the toolbar's colored bar reaches the top", 26, toolbar.getPaddingTop());
    assertEquals(80, toolbar.getLayoutParams().height);
    assertEquals("no empty strip above the toolbar", 0, content.getPaddingTop());
    assertEquals(48, content.getPaddingBottom());
  }

  @Test
  public void insetsAreReappliedNotAccumulatedWhenTheyChange() {
    Context context = ApplicationProvider.getApplicationContext();
    FrameLayout content = new FrameLayout(context);

    EdgeToEdgeInsets.install(content, null);
    dispatch(content, insets(Insets.of(0, 24, 0, 48), Insets.NONE, Insets.NONE));
    dispatch(content, insets(Insets.of(0, 24, 0, 48), Insets.NONE, Insets.of(0, 0, 0, 400)));
    assertEquals(400, content.getPaddingBottom());
    dispatch(content, insets(Insets.of(0, 24, 0, 48), Insets.NONE, Insets.NONE));
    assertEquals("the keyboard going away gives the space back", 48, content.getPaddingBottom());
  }
}
