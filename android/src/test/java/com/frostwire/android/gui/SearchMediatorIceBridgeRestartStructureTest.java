/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

public class SearchMediatorIceBridgeRestartStructureTest {

  @Test
  public void performSearchRestartsIceBridgeIfDown() throws Exception {
    String mediator =
        readProjectFile("src/main/java/com/frostwire/android/gui/SearchMediator.java")
            .replaceAll("\\s+", "");
    String engine =
        readProjectFile("src/main/java/com/frostwire/android/gui/services/Engine.java")
            .replaceAll("\\s+", "");

    assertTrue(
        "search must wait for IceBridge before starting engines",
        mediator.contains("ensureDistributedSearchReady(15_000)"));
    assertTrue(
        "Engine must restart IceBridge when a search finds it down",
        engine.contains("IceBridgedownatsearchtime—restarting"));
    assertTrue(engine.contains("ensureRelayStack(false,latch::countDown)"));
  }

  private static String readProjectFile(String relativePath) throws Exception {
    File file = new File(relativePath);
    if (!file.isFile()) {
      file = new File("android", relativePath);
    }
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
  }
}
