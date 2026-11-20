/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.gui.theme.ThemeMediator.ThemeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1576: when the user's theme cannot load, FrostWire starts with the default
 * theme and, once the main window is up, tells the user once with a plain text notice that names
 * the theme and where the logs are.
 */
class ThemeFallbackNoticeTest {

  @BeforeEach
  @AfterEach
  void forgetFallback() {
    ThemeStartup.resetFallback();
  }

  private static void load(ThemeEnum chosen, ThemeEnum failing) {
    ThemeStartup.load(
        chosen,
        (theme, step) -> {
          if (theme == failing) {
            throw new IllegalStateException("boom");
          }
        },
        t -> {});
  }

  @Test
  void nothingIsRecordedWhenTheThemeLoads() {
    load(ThemeEnum.DARK_FLAT_LAF, null);

    assertNull(ThemeStartup.fallbackFrom());
  }

  @Test
  void theFallbackRecordsTheFailedThemeAndKeepsTheFirst() {
    load(ThemeEnum.DARK_FLAT_LAF, ThemeEnum.DARK_FLAT_LAF);
    load(ThemeEnum.LIGHT_FLAT_LAF, ThemeEnum.LIGHT_FLAT_LAF);

    assertEquals(ThemeEnum.DARK_FLAT_LAF.name(), ThemeStartup.fallbackFrom());
  }

  @Test
  void noNoticeWhenNoFallbackHappened() {
    ThemeFallbackNotice notice = new ThemeFallbackNotice(ThemeStartup::fallbackFrom, () -> "/x");

    assertNull(notice.take());
  }

  @Test
  void theNoticeIsGivenOnceWithTheThemeAndTheLogLocationAsPlainText() {
    load(ThemeEnum.LIGHT_FLAT_LAF, ThemeEnum.LIGHT_FLAT_LAF);
    ThemeFallbackNotice notice =
        new ThemeFallbackNotice(ThemeStartup::fallbackFrom, () -> "/home/u/.frostwire5");

    String message = notice.take();

    assertNotNull(message);
    assertTrue(message.contains("LIGHT_FLAT_LAF"), message);
    assertTrue(message.contains("/home/u/.frostwire5"), message);
    assertFalse(message.contains("<"), "the notice is plain text: " + message);
    assertFalse(message.contains("{"), "no placeholder may be left: " + message);
    assertNull(notice.take(), "the notice is shown at most once per launch");
  }
}
