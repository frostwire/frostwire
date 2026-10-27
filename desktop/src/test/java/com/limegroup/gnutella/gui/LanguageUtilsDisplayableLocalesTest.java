/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The setup wizard's language picker filtered every language name through Font.canDisplayUpTo on
 * the EDT. On Windows a name in a script the font lacks walks the fallback fonts, loading them from
 * disk on a cold JVM. The check must run once per font, off the EDT, and be reused.
 */
class LanguageUtilsDisplayableLocalesTest {

  @Test
  void pickerFontIsCheckedOnceAndReusedOnTheEdt() throws Exception {
    LanguageUtils.preloadLocales();
    // What preloadLocales() schedules in the background on Windows and Linux.
    Locale[] preloaded = LanguageUtils.displayableLocales(LanguageUtils.LANGUAGE_PICKER_FONT);
    assertTrue(preloaded.length > 0, "English is always offered");

    RecordingFont sameFont = new RecordingFont(LanguageUtils.LANGUAGE_PICKER_FONT);
    AtomicReference<Locale[]> onEdt = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> onEdt.set(LanguageUtils.displayableLocales(sameFont)));

    assertEquals(0, sameFont.checks, "the EDT must reuse the preloaded check");
    assertArrayEquals(preloaded, onEdt.get());
  }

  private static final class RecordingFont extends Font {
    int checks;

    RecordingFont(Font font) {
      super(font);
    }

    @Override
    public int canDisplayUpTo(String str) {
      checks++;
      return super.canDisplayUpTo(str);
    }
  }
}
