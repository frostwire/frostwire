/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The Share dialog must target X (x.com), not the retired Twitter branding, and use an X icon. */
class ShareTorrentDialogXBrandingTest {

  @Test
  void shareDialogTargetsXNotTwitter() throws Exception {
    String source =
        readSource("desktop/src/main/java/com/frostwire/gui/bittorrent/ShareTorrentDialog.java");
    assertFalse(source.contains("Twitter it"), "the Share dialog must not say 'Twitter it'");
    assertFalse(source.contains("twitter.com"), "the Share dialog must not link to twitter.com");
    assertTrue(source.contains("x.com/intent/post"), "sharing must open the X intent endpoint");
  }

  @Test
  void xIconIsMappedAndPresent() throws Exception {
    String mapping =
        readSource("desktop/resources/org/limewire/gui/resources/icon_mapping.properties");
    assertTrue(mapping.contains("X=x"), "an X button icon must be mapped");
    assertTrue(
        Files.exists(locate("desktop/resources/org/limewire/gui/images/x.png")),
        "the X button icon image must exist");
  }

  private static String readSource(String moduleRelative) throws Exception {
    Path file = locate(moduleRelative);
    return Files.readString(file);
  }

  private static Path locate(String moduleRelative) {
    String stripped =
        moduleRelative.startsWith("desktop/")
            ? moduleRelative.substring("desktop/".length())
            : moduleRelative;
    Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    for (int depth = 0; depth < 6 && dir != null; depth++) {
      for (String candidate : new String[] {moduleRelative, stripped}) {
        Path file = dir.resolve(candidate);
        if (Files.exists(file)) {
          return file;
        }
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("source not found: " + moduleRelative);
  }
}
