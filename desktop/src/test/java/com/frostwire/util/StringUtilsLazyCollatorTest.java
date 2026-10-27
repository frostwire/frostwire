/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.limewire.util.StringUtils;

/**
 * ResourceManager's static initializer sets the string-matching locale on the EDT at startup.
 * Building a Collator there decoded the JDK's Unicode decomposition tables on the EDT (Icebase
 * #98b39cbe9c84); only table sorting needs one, so it is built on the first comparison.
 */
class StringUtilsLazyCollatorTest {

  @AfterEach
  void restoreDefaultLocale() {
    StringUtils.setLocale(Locale.getDefault());
  }

  @Test
  void settingTheLocaleDoesNotBuildACollator() throws Exception {
    StringUtils.setLocale(Locale.ENGLISH);

    assertNull(collator(), "setLocale built a Collator");
  }

  @Test
  void firstComparisonBuildsAFullPrimaryCollatorForTheLocale() throws Exception {
    StringUtils.setLocale(Locale.ENGLISH);

    // Primary strength ignores case and accents.
    assertEquals(0, StringUtils.compareFullPrimary("resume", "Résumé"));
    assertTrue(StringUtils.compareFullPrimary("apple", "banana") < 0);
    assertNotNull(collator());
  }

  @Test
  void changingTheLocaleDropsTheCollatorBuiltForThePreviousOne() throws Exception {
    StringUtils.setLocale(Locale.ENGLISH);
    StringUtils.compareFullPrimary("a", "b");

    StringUtils.setLocale(Locale.GERMAN);

    assertNull(collator());
  }

  private static Collator collator() throws ReflectiveOperationException {
    Field field =
        Arrays.stream(StringUtils.class.getDeclaredFields())
            .filter(f -> Modifier.isStatic(f.getModifiers()) && f.getType() == Collator.class)
            .findFirst()
            .orElseThrow();
    field.setAccessible(true);
    return (Collator) field.get(null);
  }
}
