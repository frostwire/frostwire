/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The guard's decisions, with the HBShaper probe replaced (frostwire/frostwire#1587). */
class TextShapingGuardTest {
  private String savedProperty;

  @BeforeEach
  void forgetEverything() {
    savedProperty = System.getProperty(TextShapingGuard.FFM_PROPERTY);
    System.clearProperty(TextShapingGuard.FFM_PROPERTY);
    JdkWarmUp.clearFirstTextWarmUpFailure();
  }

  @AfterEach
  void restore() {
    if (savedProperty == null) {
      System.clearProperty(TextShapingGuard.FFM_PROPERTY);
    } else {
      System.setProperty(TextShapingGuard.FFM_PROPERTY, savedProperty);
    }
    JdkWarmUp.clearFirstTextWarmUpFailure();
  }

  @Test
  void aFailingShaperSwitchesTheJdkToItsJniShaperAndRecordsTheCause() {
    boolean works =
        TextShapingGuard.install(
            () -> {
              throw new ExceptionInInitializerError(
                  new java.util.NoSuchElementException("Symbol not found: malloc"));
            });

    assertFalse(works);
    assertEquals("false", System.getProperty(TextShapingGuard.FFM_PROPERTY));
    String recorded = JdkWarmUp.firstTextWarmUpFailure();
    assertNotNull(recorded, "the first failure reaches the crash and stall reports");
    assertTrue(recorded.contains("ExceptionInInitializerError"), recorded);
    assertTrue(recorded.contains("Symbol not found: malloc"), recorded);
  }

  @Test
  void aWorkingShaperLeavesTheJdkAlone() {
    assertTrue(TextShapingGuard.install(() -> {}));
    assertNull(System.getProperty(TextShapingGuard.FFM_PROPERTY));
    assertNull(JdkWarmUp.firstTextWarmUpFailure());
  }

  @Test
  void aJdkWithoutTheFfmShaperIsLeftAlone() {
    assertTrue(
        TextShapingGuard.install(
            () -> {
              throw new ClassNotFoundException("sun.font.HBShaper");
            }));
    assertNull(System.getProperty(TextShapingGuard.FFM_PROPERTY));
    assertNull(JdkWarmUp.firstTextWarmUpFailure());
  }

  @Test
  void anExplicitChoiceOfTheUserIsRespectedAndTheProbeIsNotRun() {
    System.setProperty(TextShapingGuard.FFM_PROPERTY, "true");
    boolean[] probed = {false};

    assertTrue(TextShapingGuard.install(() -> probed[0] = true));

    assertFalse(probed[0]);
    assertEquals("true", System.getProperty(TextShapingGuard.FFM_PROPERTY));
  }

  @Test
  void missingFontLibrariesAreNotAShaperFailureButTheirCauseIsKept() {
    assertTrue(
        TextShapingGuard.install(
            () -> {
              throw new TextShapingGuard.NativeLibrariesUnavailable(
                  new UnsatisfiedLinkError("fontmanager.dll: Can't find dependent libraries"));
            }));

    assertNull(System.getProperty(TextShapingGuard.FFM_PROPERTY), "JNI shaping needs them too");
    assertTrue(JdkWarmUp.firstTextWarmUpFailure().contains("Can't find dependent libraries"));
  }

  @Test
  void theRealHbShaperInitializesOnThisJdk() {
    // On a healthy JDK the real probe passes and the property stays unset.
    assertTrue(TextShapingGuard.install());
    assertNull(System.getProperty(TextShapingGuard.FFM_PROPERTY));
  }
}
