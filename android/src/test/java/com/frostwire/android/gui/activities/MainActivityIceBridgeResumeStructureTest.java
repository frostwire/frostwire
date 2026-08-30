/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.activities;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

public class MainActivityIceBridgeResumeStructureTest {

  @Test
  public void resumeRestartsIceBridgeWithoutWaitingForSearch() throws Exception {
    String source =
        read("src/main/java/com/frostwire/android/gui/activities/MainActivity.java")
            .replaceAll("\\s+", "");
    assertTrue(source.contains("ensureDistributedSearchReady(15_000)"));
    assertTrue(source.contains("mainResume()"));
  }

  private static String read(String relativePath) throws Exception {
    File file = new File(relativePath);
    if (!file.isFile()) {
      file = new File("android", relativePath);
    }
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
  }
}
