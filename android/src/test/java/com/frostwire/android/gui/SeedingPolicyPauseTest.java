/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class SeedingPolicyPauseTest {

  @Test
  public void seedingPolicyDoesNotPersistAnExplicitPause() throws Exception {
    String manager =
        projectFile("src/main/java/com/frostwire/android/gui/transfers/TransferManager.java");
    String suspend = blockStartingAt(manager, "public void suspendSeedingTorrents()");
    assertTrue(suspend.contains("pauseForPolicy()"));
    assertFalse(suspend.contains("d.pause();"));

    String download =
        projectFile("../common/src/main/java/com/frostwire/bittorrent/BTDownload.java");
    String policyPause = blockStartingAt(download, "public void pauseForPolicy()");
    assertTrue(policyPause.contains("pause(false);"));
    assertFalse(policyPause.contains("WAS_PAUSED_EXTRA_KEY"));
  }

  @Test
  public void explicitStopSeedingStillPersistsPause() throws Exception {
    String manager =
        projectFile("src/main/java/com/frostwire/android/gui/transfers/TransferManager.java");
    String explicitStop = blockStartingAt(manager, "public void stopSeedingTorrents()");
    assertTrue(explicitStop.contains("d.pause();"));
  }

  @Test
  public void networkPolicyResumesOnlyPolicySuspendedSeeds() throws Exception {
    String receiver =
        projectFile(
            "src/main/java/com/frostwire/android/gui/services/EngineBroadcastReceiver.java");
    String connected = blockStartingAt(receiver, "private void handleConnectedNetwork");
    assertTrue(connected.contains("suspendSeedingTorrents();"));
    assertTrue(connected.contains("resumePolicySuspendedSeeding();"));
  }

  @Test
  public void dataAndVpnPoliciesDoNotBecomeExplicitPauses() throws Exception {
    String receiver =
        projectFile(
            "src/main/java/com/frostwire/android/gui/services/EngineBroadcastReceiver.java");
    String connected = blockStartingAt(receiver, "private void handleConnectedNetwork");
    assertTrue(connected.contains("suspendTorrentsForPolicy();"));
    assertFalse(connected.contains("pauseTorrents();"));

    String ui =
        projectFile("src/main/java/com/frostwire/android/gui/transfers/UIBittorrentDownload.java");
    String constructor = blockStartingAt(ui, "public UIBittorrentDownload(TransferManager manager");
    assertTrue(constructor.contains("pauseForPolicy();"));
    assertFalse(constructor.contains("dl.pause();"));
  }

  private static String blockStartingAt(String source, String marker) {
    int start = source.indexOf(marker);
    if (start < 0) {
      return "";
    }
    int openingBrace = source.indexOf('{', start);
    int depth = 0;
    for (int i = openingBrace; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}' && --depth == 0) {
        return source.substring(start, i);
      }
    }
    return source.substring(start);
  }

  private static String projectFile(String relative) throws IOException {
    Path root = Path.of(System.getProperty("user.dir"));
    Path file = root.resolve(relative);
    if (!Files.exists(file)) {
      file = root.resolve("android").resolve(relative);
    }
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }
}
