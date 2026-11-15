/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import com.frostwire.gui.PerformanceMode;
import javax.swing.UIManager;

/**
 * Whether the UI animates: not in Lite (frostwire/frostwire#1555). Covers FlatLaf's animations and
 * smooth scrolling, the update button's blinking and the promo slideshow's rotation; the Pieces
 * hive has its own switch.
 */
public final class Animations {
  /** FlatLaf reads it each time it would animate (com.formdev.flatlaf.util.Animator). */
  static final String FLATLAF_ANIMATION = "flatlaf.animation";

  /** FlatLaf reads it on each mouse wheel event (FlatScrollPaneUI). */
  static final String SMOOTH_SCROLLING = "ScrollPane.smoothScrolling";

  private Animations() {}

  public static boolean enabled() {
    return !PerformanceMode.isLite();
  }

  /**
   * Turns FlatLaf's animations and smooth scrolling off in Lite, and gives them back to the look
   * and feel in Full. Call when the look and feel is installed and when the mode changes; both
   * settings are read live, so open windows follow.
   */
  public static void applyToLookAndFeel() {
    if (enabled()) {
      System.clearProperty(FLATLAF_ANIMATION);
      UIManager.put(SMOOTH_SCROLLING, null);
    } else {
      System.setProperty(FLATLAF_ANIMATION, "false");
      UIManager.put(SMOOTH_SCROLLING, Boolean.FALSE);
    }
  }
}
