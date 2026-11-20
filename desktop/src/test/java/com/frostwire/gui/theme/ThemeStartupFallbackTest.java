/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.gui.theme.ThemeMediator.ThemeEnum;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1576: a theme that failed at startup ended the launch with a RuntimeException
 * whose report said nothing about what failed. The failure now names the theme and the step, keeps
 * the cause, and a FlatLaf theme that cannot load starts the default theme instead.
 */
class ThemeStartupFallbackTest {

  private final List<ThemeEnum> applied = new ArrayList<>();
  private final List<Throwable> reported = new ArrayList<>();

  private ThemeStartup.Applier failing(ThemeEnum failingTheme, Exception failure) {
    return (theme, step) -> {
      step.accept("first step");
      if (theme == failingTheme) {
        step.accept("FlatLaf setup");
        throw failure;
      }
      applied.add(theme);
    };
  }

  @Test
  void aFlatLafThemeThatFailsFallsBackToTheDefaultAndKeepsTheFailure() {
    IllegalStateException cause = new IllegalStateException("flatlaf dll");

    ThemeStartup.load(
        ThemeEnum.DARK_FLAT_LAF, failing(ThemeEnum.DARK_FLAT_LAF, cause), reported::add);

    assertEquals(List.of(ThemeEnum.DEFAULT), applied);
    assertEquals(1, reported.size(), "the failure must still reach the crash report");
    assertSame(cause, reported.get(0), "the report needs the real failure, not a wrapper");
  }

  @Test
  void aLinkageErrorFallsBackToo() {
    UnsatisfiedLinkError cause = new UnsatisfiedLinkError("flatlaf-windows-x86_64.dll");
    ThemeStartup.Applier applier =
        (theme, step) -> {
          if (theme == ThemeEnum.LIGHT_FLAT_LAF) {
            throw cause;
          }
          applied.add(theme);
        };

    ThemeStartup.load(ThemeEnum.LIGHT_FLAT_LAF, applier, reported::add);

    assertEquals(List.of(ThemeEnum.DEFAULT), applied);
    assertSame(cause, reported.get(0));
  }

  @Test
  void whenTheDefaultThemeFailsTheCauseAndTheStepAreInTheException() {
    IllegalStateException cause = new IllegalStateException("nimbus");

    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                ThemeStartup.load(
                    ThemeEnum.DEFAULT, failing(ThemeEnum.DEFAULT, cause), reported::add));

    assertSame(cause, thrown.getCause());
    assertTrue(thrown.getMessage().contains("DEFAULT"));
    assertTrue(thrown.getMessage().contains("FlatLaf setup"));
    assertTrue(applied.isEmpty());
  }

  @Test
  void whenTheFallbackFailsTooTheExceptionNamesBothAndKeepsTheCause() {
    IllegalStateException cause = new IllegalStateException("broken ui");
    ThemeStartup.Applier applier =
        (theme, step) -> {
          step.accept("step of " + theme);
          throw cause;
        };

    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> ThemeStartup.load(ThemeEnum.DARK_FLAT_LAF, applier, reported::add));

    assertSame(cause, thrown.getCause());
    assertTrue(thrown.getMessage().contains("DARK_FLAT_LAF"));
    assertTrue(thrown.getMessage().contains("step of DEFAULT"));
    assertNotNull(reported.get(0), "the first failure was reported before the fallback");
  }

  @Test
  void damagedFilesAreNotHiddenByAFallback() {
    ClassFormatError damaged = new ClassFormatError("bad class");

    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                ThemeStartup.load(
                    ThemeEnum.DARK_FLAT_LAF,
                    (theme, step) -> {
                      throw damaged;
                    },
                    reported::add));

    assertSame(damaged, thrown.getCause());
    assertTrue(applied.isEmpty());
  }
}
