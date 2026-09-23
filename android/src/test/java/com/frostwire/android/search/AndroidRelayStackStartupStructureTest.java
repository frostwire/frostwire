/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.search;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

public class AndroidRelayStackStartupStructureTest {

  @Test
  public void verifiedDiscoveryAndMeshWarmupDoNotBlockSearchWiring() throws Exception {
    String source =
        read("src/main/java/com/frostwire/android/search/AndroidRelayStack.java")
            .replaceAll("\\s+", "");

    assertTrue(source.contains("pds.start();"));
    assertTrue(source.contains("da.start();"));
    assertTrue(source.contains("prs.start();"));
    assertTrue(source.contains(".searchTransport(tr);"));
    assertFalse(source.contains("pds.tick();"));
    assertFalse(source.contains("da.tick(btEngine);"));
    assertFalse(source.contains("prs.sync();"));
  }

  private static String read(String relativePath) throws Exception {
    File file = new File(relativePath);
    if (!file.isFile()) {
      file = new File("android", relativePath);
    }
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
  }
}
