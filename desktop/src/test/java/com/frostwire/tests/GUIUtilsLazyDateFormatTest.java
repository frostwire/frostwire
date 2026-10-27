/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.limegroup.gnutella.gui.GUIUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.DateFormat;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * GUIUtils is first initialized on the EDT by the splash window's status bar; building its full
 * date format there ran SimpleDateFormat.<clinit> on the EDT (Icebase #e31af36a34fd). Only the date
 * table cells need it, so it is built on first use.
 */
class GUIUtilsLazyDateFormatTest {

  @Test
  void localeResetDoesNotBuildTheDateFormat() throws Exception {
    resetLocale();

    assertNull(fullDateTimeFormatField());
  }

  @Test
  void dateFormatIsBuiltOnFirstUseAndReused() throws Exception {
    resetLocale();

    DateFormat format = GUIUtils.getFullDateTimeFormat();

    assertNotNull(format);
    assertSame(format, GUIUtils.getFullDateTimeFormat());
  }

  /** GUIUtils' static initializer runs this, as does every language change. */
  private static void resetLocale() throws ReflectiveOperationException {
    Method reset = GUIUtils.class.getDeclaredMethod("resetLocale");
    reset.setAccessible(true);
    reset.invoke(null);
  }

  private static DateFormat fullDateTimeFormatField() throws ReflectiveOperationException {
    Field field =
        Arrays.stream(GUIUtils.class.getDeclaredFields())
            .filter(f -> Modifier.isStatic(f.getModifiers()) && f.getType() == DateFormat.class)
            .findFirst()
            .orElseThrow();
    field.setAccessible(true);
    return (DateFormat) field.get(null);
  }
}
