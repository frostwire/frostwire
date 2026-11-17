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

package com.frostwire.android.gui.fragments;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import com.frostwire.android.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The tapped piece's card was unreadable in the dark theme: its background drawable was a fixed
 * white while its text takes the theme's text color, which is light in the dark theme (white
 * letters on a white card). The card now takes the theme's card color; this checks, for the light
 * and the dark resources, that its real background and each of its texts are readable together
 * (frostwire/frostwire#1566).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class PieceDetailsPanelThemeTest {
  /** WCAG AA for normal text. */
  private static final double MIN_CONTRAST = 4.5;

  @Test
  public void theCardIsReadableInTheLightTheme() {
    assertCardIsReadable();
  }

  @Test
  @Config(qualifiers = "night")
  public void theCardIsReadableInTheDarkTheme() {
    assertCardIsReadable();
  }

  private static void assertCardIsReadable() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    activity.setTheme(R.style.Theme_FrostWire);
    FrameLayout parent = new FrameLayout(activity);
    activity.setContentView(parent);
    LayoutInflater.from(activity).inflate(R.layout.view_transfer_detail_piece_panel, parent);
    View card = parent.findViewById(R.id.view_transfer_detail_piece_panel);

    Drawable background = card.getBackground();
    assertNotNull("the card has no background", background);
    int fill = ((GradientDrawable) background.mutate()).getColor().getDefaultColor();

    for (int id :
        new int[] {
          R.id.view_transfer_detail_piece_panel_title,
          R.id.view_transfer_detail_piece_panel_details,
          R.id.view_transfer_detail_piece_panel_priority_label,
          R.id.view_transfer_detail_piece_panel_priority
        }) {
      TextView text = parent.findViewById(id);
      int color = text.getCurrentTextColor();
      double contrast = ColorUtils.calculateContrast(color, fill);
      assertTrue(
          String.format(
              "text %#x on the card's background %#x has contrast %.2f, needs %.1f",
              color, fill, contrast, MIN_CONTRAST),
          contrast >= MIN_CONTRAST);
    }
  }
}
