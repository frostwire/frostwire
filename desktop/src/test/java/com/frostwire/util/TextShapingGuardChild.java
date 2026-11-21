/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;

/**
 * The program {@link TextShapingGuardEndToEndTest} runs in a child JVM whose {@code HBShaper} has
 * been made to fail: it lays out Latin and Hebrew text, with or without the guard first.
 */
public final class TextShapingGuardChild {
  private TextShapingGuardChild() {}

  public static void main(String[] args) {
    System.setProperty("java.awt.headless", "true");
    String mode = args.length > 0 ? args[0] : "guard";
    Font font = new Font("Dialog", Font.PLAIN, 12);
    FontRenderContext frc = new FontRenderContext(null, true, true);
    if (mode.equals("guard")) {
      System.out.println("GUARD " + TextShapingGuard.install());
    } else if (mode.equals("late")) {
      layout("latin-first", font, frc, "hello");
      System.out.println("GUARD " + TextShapingGuard.install());
    }
    layout("hebrew", font, frc, "שלום");
    layout("hebrew-again", font, frc, "שלום");
  }

  private static void layout(String step, Font font, FontRenderContext frc, String text) {
    try {
      new TextLayout(text, font, frc).getAdvance();
      System.out.println("LAYOUT_OK " + step);
    } catch (Throwable t) {
      System.out.println("LAYOUT_FAILED " + step + " " + t);
    }
  }
}
