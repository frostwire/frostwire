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
            build.indexOf("applicationDefaultJvmArgs"), build.indexOf("// Remote management"));

    assertFalse(defaults.contains("jmxremote.port"));
    assertFalse(defaults.contains("agentlib:jdwp"));
    assertFalse(defaults.contains("-Ddebug=1"));
    assertFalse(defaults.contains("fw.running.from.ide"));
    assertTrue(defaults.contains("-Dfw.running.from.distribution=true"));
    assertTrue(build.contains("if (project.hasProperty('debug'))"));
    assertTrue(build.contains("tasks.named('run')"));
    assertTrue(build.contains("jvmArgs '-Ddebug=1'"));
  }

  @Test
  void releaseMetadataMatchesUnreleasedVersion() throws Exception {
    String build = Files.readString(DESKTOP.resolve("build.gradle"));
    String changelog = Files.readString(DESKTOP.resolve("changelog.txt"));
    String frostWireUtils =
        Files.readString(
            DESKTOP.resolve("src/main/java/com/limegroup/gnutella/util/FrostWireUtils.java"));
    String mcpConstants =
        Files.readString(
            DESKTOP.resolve("../common/src/main/java/com/frostwire/mcp/MCPConstants.java"));

    assertTrue(build.contains("version = '7.1.0'"));
    assertTrue(changelog.startsWith(" FrostWire 7.1.0 UNRELEASED"));
    assertTrue(frostWireUtils.contains("FROSTWIRE_VERSION = \"7.1.0\""));
    assertTrue(frostWireUtils.contains("BUILD_NUMBER = 332"));
    assertTrue(mcpConstants.contains("SERVER_VERSION = \"7.1.0\""));
  }

  @Test
  void productionBuildHidesDiagnosticLocalSearch() throws Exception {
    String buildConfig =
        Files.readString(DESKTOP.resolve("src/main/java/com/frostwire/BuildConfig.java"));
    String searchEngine =
        Files.readString(
            DESKTOP.resolve("src/main/java/com/limegroup/gnutella/gui/search/SearchEngine.java"));

    assertTrue(
        buildConfig.contains(
            "CommonUtils.isRunningFromGradle() || CommonUtils.isRunningFromIntelliJ()"));
    assertTrue(searchEngine.contains("BuildConfig.DEBUG ? ENGINES : PRODUCTION_ENGINES"));
    assertTrue(searchEngine.contains("if (!BuildConfig.DEBUG)"));
    assertTrue(searchEngine.contains("SearchEnginesSettings.LOCAL_SEARCH_ENABLED.setValue(false)"));
    assertTrue(searchEngine.contains("return BuildConfig.DEBUG && super.isEnabled()"));

    String settingsAdapter =
        Files.readString(
            DESKTOP.resolve(
                "src/main/java/com/frostwire/mcp/desktop/adapters/SettingsAdapter.java"));
    assertTrue(settingsAdapter.contains("if (BuildConfig.DEBUG)"));
  }

  @Test
  void macStartupUsesJavaDesktopUrlHandlerOnly() throws Exception {
    String initializer =
        Files.readString(
            DESKTOP.resolve("src/main/java/com/limegroup/gnutella/gui/Initializer.java"));
    String macEventHandler =
        Files.readString(
            DESKTOP.resolve("src/main/java/com/limegroup/gnutella/gui/MacEventHandler.java"));

    assertFalse(initializer.contains("GURLHandler"));
    assertTrue(initializer.contains("MacEventHandler.instance()"));
    assertTrue(macEventHandler.contains("setOpenURIHandler"));
    assertTrue(macEventHandler.contains("java.awt.desktop.OpenURIHandler"));
    assertTrue(macEventHandler.contains("GUIMediator.instance().openTorrentURI(uri, false)"));
    assertFalse(
        Files.exists(DESKTOP.resolve("src/main/java/com/limegroup/gnutella/gui/GURLHandler.java")));
    assertFalse(Files.exists(DESKTOP.resolve("lib/native-src/osx/GURLjnilib.c")));
    assertFalse(Files.exists(DESKTOP.resolve("lib/native/libGURL.dylib")));
    assertFalse(Files.readString(DESKTOP.resolve("lib/native-src/osx/build.sh")).contains("GURL"));
  }

  @Test
  void macNativeIconsAvoidAquaLookAndFeelReflection() throws Exception {
    String nativeFileIconController =
        Files.readString(
            DESKTOP.resolve(
                "src/main/java/com/limegroup/gnutella/gui/NativeFileIconController.java"));

    assertTrue(nativeFileIconController.contains("OSUtils.isWindows() || OSUtils.isMacOSX()"));
    assertTrue(nativeFileIconController.contains("return constructFSVView()"));
    assertTrue(nativeFileIconController.contains("VIEW.getSystemIcon(f)"));
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
