/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.adapters.menu;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

/**
 * Whether a source peer can be endorsed depends on the torrent's v1 info hash, which is a
 * synchronous libtorrent call ({@code BTDownload.getV1InfoHash}), and on the karma chain, which the
 * chain writer locks while it writes. Neither may run on the main thread: not when the transfer
 * menu is built on a long press, and not when the menu item is tapped (frostwire/frostwire#1413).
 */
public class EndorseSourcePeerOffMainStructureTest {

  @Test
  public void transferMenuDoesNotRunTheFullEndorsementCheck() throws Exception {
    String adapter =
        code("src/main/java/com/frostwire/android/gui/adapters/TransferListAdapter.java");

    assertFalse(
        "the menu is built on the main thread",
        adapter.contains("EndorseSourcePeerMenuAction.canEndorse("));
  }

  @Test
  public void tappingTheItemChecksAndEndorsesOffTheMainThread() throws Exception {
    String action =
        code(
            "src/main/java/com/frostwire/android/gui/adapters/menu/"
                + "EndorseSourcePeerMenuAction.java");
    String onClick = action.substring(action.indexOf("publicvoidonClick(Contextcontext){"));

    assertFalse("onClick runs on the main thread", onClick.contains("if(!canEndorse(download)"));
    assertTrue(
        "the check runs on a background handler",
        onClick.contains("OffMainQuery") && onClick.contains("canEndorse(download)"));
    assertFalse("uses the app's handler threads, not a raw thread", action.contains("newThread("));
    assertTrue(action.contains("SystemUtils.postToHandler("));
  }

  /** The source with comments and whitespace removed. */
  private static String code(String relativePath) throws Exception {
    File file = new File(relativePath);
    if (!file.isFile()) {
      file = new File("android", relativePath);
    }
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
        .replaceAll("(?s)/\\*.*?\\*/", "")
        .replaceAll("(?m)//[^\\n]*", "")
        .replaceAll("\\s+", "");
  }
}
