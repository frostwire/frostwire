/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A JDK class whose static initializer fails once only ever shows up later as {@code
 * NoClassDefFoundError: Could not initialize class X}; the probe initializes the known ones first
 * and keeps the first failure (frostwire/frostwire#1587).
 */
class JdkClassProbeTest {

  @BeforeEach
  @AfterEach
  void forgetFailures() {
    JdkWarmUp.clearFirstTextWarmUpFailure();
  }

  @Test
  void theFirstFailureIsKeptWithItsCauseChainAndTheClassItBroke() {
    List<String> tried = new ArrayList<>();
    int failures =
        JdkClassProbe.install(
            List.of("a.First", "b.Second", "c.Third"),
            name -> {
              tried.add(name);
              if (name.equals("b.Second")) {
                throw new ExceptionInInitializerError(
                    new UnsatisfiedLinkError("Can't find dependent libraries"));
              }
              if (name.equals("c.Third")) {
                throw new IllegalStateException("a later failure");
              }
            });

    assertEquals(2, failures);
    assertEquals(List.of("a.First", "b.Second", "c.Third"), tried, "every class is tried");
    String kept = JdkWarmUp.firstTextWarmUpFailure();
    assertNotNull(kept);
    assertTrue(kept.contains("JDK class b.Second failed to initialize"), kept);
    assertTrue(kept.contains("UnsatisfiedLinkError"), kept);
    assertTrue(!kept.contains("a later failure"), "only the first failure is kept: " + kept);
  }

  @Test
  void aClassThisJdkDoesNotHaveIsSkippedWithoutAFailure() {
    int failures =
        JdkClassProbe.install(
            List.of("sun.nio.fs.NoSuchOsClass"),
            name -> {
              throw new ClassNotFoundException(name);
            });

    assertEquals(0, failures);
    assertNull(JdkWarmUp.firstTextWarmUpFailure());
  }

  @Test
  void theRealClassesInitializeOnThisJdkAndNeverThrow() {
    assertDoesNotThrow((org.junit.jupiter.api.function.Executable) JdkClassProbe::install);
    assertNull(JdkWarmUp.firstTextWarmUpFailure());
  }

  @Test
  void mainRunsTheProbeRightAfterTheTextGuard() throws java.io.IOException {
    String main =
        java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/limegroup/gnutella/gui/Main.java"))
            .replaceAll("\\s+", "");
    int guard = main.indexOf("TextShapingGuard.install();");
    int probe = main.indexOf("JdkClassProbe.install();");
    assertTrue(guard > 0 && probe > guard, "the probe follows the text guard in main");
  }
}
