/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import static com.frostwire.bittorrent.LocalEngine.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.jlibtorrent.TorrentInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * frostwire/frostwire#1304: the engine keeps each torrent's network mode, loads it when it starts
 * and writes it when it changes and when it stops.
 */
@Timeout(120)
class BTEngineNetworkPolicyTest {

  @TempDir Path dir;
  private LocalEngine local;

  @AfterEach
  void stopEngine() {
    if (local != null) {
      local.close();
    }
  }

  @Test
  void aTorrentWithoutAChoiceIsMixed() throws Exception {
    local = LocalEngine.start(dir);
    TorrentInfo ti = album("plain");

    assertEquals(
        TorrentNetworkMode.MIXED, local.engine.getNetworkMode(local.engine.canonicalInfoHash(ti)));
    assertFalse(local.engine.networkPolicyFile().exists(), "nothing to write for the default");
  }

  @Test
  void theChoiceIsWrittenInTheBackgroundAndSurvivesARestart() throws Exception {
    local = LocalEngine.start(dir);
    String hash = local.engine.canonicalInfoHash(album("kept"));

    assertTrue(local.engine.setNetworkMode(hash, TorrentNetworkMode.I2P_ONLY));
    assertEquals(TorrentNetworkMode.I2P_ONLY, local.engine.getNetworkMode(hash));
    await(() -> local.engine.networkPolicyFile().isFile(), "the policy file was written");

    local.restart();

    assertEquals(TorrentNetworkMode.I2P_ONLY, local.engine.getNetworkMode(hash));
  }

  @Test
  void aChoiceMadeJustBeforeShuttingDownIsStillWritten() throws Exception {
    local = LocalEngine.start(dir);
    String hash = local.engine.canonicalInfoHash(album("late"));

    local.engine.setNetworkMode(hash, TorrentNetworkMode.CLEARNET_ONLY);
    local.restart();

    assertEquals(TorrentNetworkMode.CLEARNET_ONLY, local.engine.getNetworkMode(hash));
  }

  @Test
  void aCorruptPolicyFileDoesNotKeepTheEngineFromStarting() throws Exception {
    local = LocalEngine.create(dir);
    Files.write(
        local.engine.networkPolicyFile().toPath(), new byte[] {(byte) 0xff, 0, '=', '\n', '#'});

    local.engine.start();

    assertTrue(local.engine.isRunning());
    assertEquals(
        TorrentNetworkMode.MIXED,
        local.engine.getNetworkMode("0123456789abcdef0123456789abcdef01234567"));
  }

  @Test
  void invalidHashesAndAnEngineThatNeverStartedAreRefused() throws Exception {
    local = LocalEngine.create(dir);
    assertFalse(
        local.engine.setNetworkMode(
            "0123456789abcdef0123456789abcdef01234567", TorrentNetworkMode.I2P_ONLY),
        "no store before start");
    local.engine.start();
    assertFalse(local.engine.setNetworkMode("nope", TorrentNetworkMode.I2P_ONLY));
    assertFalse(local.engine.setNetworkMode(null, TorrentNetworkMode.I2P_ONLY));
  }

  @Test
  void theCanonicalHashOfAHybridTorrentIsItsV1HashAndSharesTheStoresKey() throws Exception {
    local = LocalEngine.create(dir);
    TorrentInfo hybrid = album("hybrid");

    assertEquals(
        TorrentNetworkPolicyStore.canonicalKey(
            hybrid.infoHashV1().toString(), hybrid.infoHashV2().toString()),
        local.engine.canonicalInfoHash(hybrid));
    assertEquals(hybrid.infoHashV1().toHex(), local.engine.canonicalInfoHash(hybrid));
  }

  private TorrentInfo album(String name) throws Exception {
    Path folder = dir.resolve("content").resolve(name);
    LocalEngine.content(folder.resolve("one.mp3"), 64 * 1024, name.hashCode());
    return LocalEngine.torrent(folder.toFile(), null);
  }
}
