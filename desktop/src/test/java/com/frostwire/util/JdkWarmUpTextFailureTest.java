/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The first text warm-up failure is kept for crash and StrictMode reports: a NoClassDefFoundError
 * for sun.font.HBShaper is only explained by the initializer failure before it
 * (frostwire/frostwire#1587).
 */
class JdkWarmUpTextFailureTest {

  @BeforeEach
  @AfterEach
  void clear() {
    JdkWarmUp.clearFirstTextWarmUpFailure();
  }

  @Test
  void aFailingTextWarmUpStaysBestEffortAndKeepsTheFailureChain() {
    UnsatisfiedLinkError link = new UnsatisfiedLinkError("no harfbuzz in java.library.path");
    ExceptionInInitializerError first = new ExceptionInInitializerError(link);

    JdkWarmUp.warmUpText(
        () -> {
          throw first;
        });

    String failure = JdkWarmUp.firstTextWarmUpFailure();
    assertTrue(failure.startsWith("java.lang.ExceptionInInitializerError"), failure);
    assertTrue(
        failure.contains(" <- java.lang.UnsatisfiedLinkError: no harfbuzz in java.library.path"),
        failure);
  }

  @Test
  void onlyTheFirstFailureIsKept() {
    JdkWarmUp.warmUpText(
        () -> {
          throw new UnsatisfiedLinkError("first");
        });
    JdkWarmUp.warmUpText(
        () -> {
          throw new NoClassDefFoundError("sun/font/HBShaper");
        });

    assertEquals("java.lang.UnsatisfiedLinkError: first", JdkWarmUp.firstTextWarmUpFailure());
  }

  @Test
  void aSuccessfulTextWarmUpRecordsNothing() {
    JdkWarmUp.warmUpText(() -> {});

    assertNull(JdkWarmUp.firstTextWarmUpFailure());
  }

  @Test
  void theDescriptionIsBounded() {
    Throwable chain = new RuntimeException("x".repeat(5_000));
    for (int i = 0; i < 20; i++) {
      chain = new RuntimeException("cause " + i + " " + "y".repeat(300), chain);
    }

    String text = JdkWarmUp.describeFailure(chain, "/home/nobody", "nobody");

    assertTrue(text.length() <= 800, "length " + text.length());
    assertTrue(text.endsWith("..."), text);

    String single = JdkWarmUp.describeFailure(new RuntimeException("z".repeat(5_000)), null, null);
    assertEquals("java.lang.RuntimeException: ".length() + 200, single.length());
    assertTrue(single.endsWith("..."), single);

    Throwable cyclic = new RuntimeException("loop");
    cyclic.initCause(new IllegalStateException("inner", cyclic));
    assertTrue(JdkWarmUp.describeFailure(cyclic, null, null).length() <= 800);
  }

  @Test
  void theHomeDirectoryAndUserNameAreRemoved() {
    UnsatisfiedLinkError link =
        new UnsatisfiedLinkError(
            "C:\\Users\\jdoe123\\AppData\\Local\\Temp\\harfbuzz.dll: can't load; jdoe123 at"
                + " C:/Users/jdoe123/x");

    String text = JdkWarmUp.describeFailure(link, "C:\\Users\\jdoe123", "jdoe123");

    assertFalse(text.contains("jdoe123"), text);
    assertTrue(text.contains("<home>\\AppData\\Local\\Temp\\harfbuzz.dll"), text);
    assertTrue(text.contains("<home>/x"), text);
    assertTrue(text.contains("<user> at"), text);
  }

  @Test
  void theDescriptionIsPrintableAsciiBecauseTheServerRejectsAnythingElse() {
    String described =
        JdkWarmUp.describeFailure(
            new UnsatisfiedLinkError("No se puede encontrar el m\u00f3dulo\n\u65e5\u672c\t"),
            "C:\\Users\\nobody",
            "nobody");
    assertTrue(described.matches("[\\x20-\\x7e]+"), described);
    assertTrue(described.startsWith("java.lang.UnsatisfiedLinkError: No se puede encontrar el m"));
  }
}
