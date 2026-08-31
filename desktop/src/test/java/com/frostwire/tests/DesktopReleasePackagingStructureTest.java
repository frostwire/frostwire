/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DesktopReleasePackagingStructureTest {

  private static final Path DESKTOP = Path.of(System.getProperty("user.dir"));

  @Test
  void releaseJvmDefaultsDoNotExposeDebugListeners() throws Exception {
    String build = Files.readString(DESKTOP.resolve("build.gradle"));
    String defaults =
        build.substring(
            build.indexOf("applicationDefaultJvmArgs"), build.indexOf("// Add IDE flag"));

    assertFalse(defaults.contains("jmxremote.port"));
    assertFalse(defaults.contains("agentlib:jdwp"));
    assertTrue(build.contains("if (project.hasProperty('debug'))"));
  }

  @Test
  void distributionPackagesIceBridgeBesideTheApplicationJar() throws Exception {
    String build = Files.readString(DESKTOP.resolve("build.gradle"));
    String initializer =
        Files.readString(
            DESKTOP.resolve("src/main/java/com/limegroup/gnutella/gui/Initializer.java"));

    assertTrue(build.contains("from(icebridgeJar)"));
    assertTrue(build.contains("into 'lib'"));
    assertTrue(initializer.contains("new File(applicationJar.getParentFile(), \"icebridge.jar\")"));
  }
}
