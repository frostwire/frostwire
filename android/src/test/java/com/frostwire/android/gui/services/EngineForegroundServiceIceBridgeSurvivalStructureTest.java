/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.services;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

/**
 * Android reaps EngineForegroundService while the process stays alive. IceBridge
 * must not die with the FGS, or distributed search silently sends nothing until
 * a force-stop. startServices must restart a missing relay stack instead of
 * aborting as "already started".
 */
public class EngineForegroundServiceIceBridgeSurvivalStructureTest {

  @Test
  public void fgsDestroyDoesNotTearDownIceBridge() throws Exception {
    String source =
        readProjectFile(
            "src/main/java/com/frostwire/android/gui/services/EngineForegroundService.java");
    String compact = source.replaceAll("\\s+", "");

    int onDestroy = compact.indexOf("publicvoidonDestroy()");
    int nextMethod = compact.indexOf("privatevoidpreloadIdentityFromDisk()", onDestroy);
    assertTrue(onDestroy >= 0 && nextMethod > onDestroy);
    String onDestroyBody = compact.substring(onDestroy, nextMethod);
    assertFalse(
        "onDestroy must not stop IceBridge when the FGS is reaped",
        onDestroyBody.contains("stopRelayStack()"));

    assertTrue(
        "startServices must restart IceBridge if the engine is up but the stack is gone",
        compact.contains("engineupbutIceBridgedown,restartingrelaystack"));
    assertTrue(
        "explicit stopServices still closes IceBridge",
        compact.contains("BTEngine.getInstance().pause();stopRelayStack();"));
  }

  private static String readProjectFile(String relativePath) throws Exception {
    File file = new File(relativePath);
    if (!file.isFile()) {
      file = new File("android", relativePath);
    }
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
  }
}
