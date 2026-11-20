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

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Keeps content clear of the system bars, the display cutout and the keyboard now that every window
 * is edge-to-edge (the platform enforces it for apps targeting Android 15 and the opt-out is gone
 * at Android 16).
 *
 * <p>One place for every screen: {@link com.frostwire.android.gui.views.AbstractActivity} installs
 * it on the content of every activity. When a toolbar is given, the status bar inset goes to the
 * toolbar (taller, same background, so the colored bar reaches the top of the screen) instead of
 * leaving an empty strip above it; everything else gets the side, bottom and keyboard insets.
 */
public final class EdgeToEdgeInsets {

  private EdgeToEdgeInsets() {}

  /**
   * The inset the content has to leave free: system bars plus display cutout on every side, and on
   * the bottom whichever is taller of the navigation bar and the keyboard.
   */
  @NonNull
  public static Insets contentInsets(@NonNull WindowInsetsCompat windowInsets) {
    Insets bars =
        windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
    Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
    return Insets.of(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
  }

  /**
   * Pads {@code content} with the window insets, now and on every later change. Call once per view.
   *
   * @param content the root the insets are applied to
   * @param toolbar a toolbar inside {@code content} that takes the top inset, or null to give the
   *     top inset to {@code content}
   */
  public static void install(@NonNull View content, @Nullable View toolbar) {
    final int left = content.getPaddingLeft();
    final int top = content.getPaddingTop();
    final int right = content.getPaddingRight();
    final int bottom = content.getPaddingBottom();
    final int toolbarTop = toolbar != null ? toolbar.getPaddingTop() : 0;
    final int toolbarHeight = toolbarHeight(toolbar);

    ViewCompat.setOnApplyWindowInsetsListener(
        content,
        (view, windowInsets) -> {
          Insets insets = contentInsets(windowInsets);
          int contentTop = top;
          if (toolbar != null) {
            toolbar.setPadding(
                toolbar.getPaddingLeft(),
                toolbarTop + insets.top,
                toolbar.getPaddingRight(),
                toolbar.getPaddingBottom());
            ViewGroup.LayoutParams params = toolbar.getLayoutParams();
            if (params != null && toolbarHeight > 0) {
              params.height = toolbarHeight + insets.top;
              toolbar.setLayoutParams(params);
            }
          } else {
            contentTop = top + insets.top;
          }
          view.setPadding(
              left + insets.left, contentTop, right + insets.right, bottom + insets.bottom);
          // Consumed here: children must not apply the same insets a second time.
          return WindowInsetsCompat.CONSUMED;
        });
    ViewCompat.requestApplyInsets(content);
  }

  private static int toolbarHeight(@Nullable View toolbar) {
    if (toolbar == null || toolbar.getLayoutParams() == null) {
      return 0;
    }
    return toolbar.getLayoutParams().height;
  }
}
