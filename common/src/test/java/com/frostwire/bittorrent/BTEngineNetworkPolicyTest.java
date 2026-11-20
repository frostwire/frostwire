/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import static com.frostwire.bittorrent.LocalEngine.await;
import static com.frostwire.bittorrent.LocalEngine.torrentFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.jlibtorrent.TorrentBuilder;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

  private static final String SEED = "http://127.0.0.1:9/seed/";

  @Test
  void onlyAnI2pOnlyTorrentIsAddedWithTheI2pFlag() throws Exception {
    local = LocalEngine.start(dir);
    String hash = local.engine.canonicalInfoHash(album("flags"));
    var paused = com.frostwire.jlibtorrent.TorrentFlags.PAUSED;

    assertEquals(paused, local.engine.networkAddFlags(hash, paused), "mixed: untouched");
    local.engine.setNetworkMode(hash, TorrentNetworkMode.CLEARNET_ONLY);
    assertEquals(paused, local.engine.networkAddFlags(hash, paused), "clearnet only: untouched");
    local.engine.setNetworkMode(hash, TorrentNetworkMode.I2P_ONLY);
    var flags = local.engine.networkAddFlags(hash, paused);

    assertTrue(flags.and_(TorrentNetworkFlags.i2pTorrent()).nonZero());
    assertTrue(flags.and_(paused).nonZero(), "the caller's flags are kept");
    assertFalse(paused.and_(TorrentNetworkFlags.i2pTorrent()).nonZero(), "the base is not mutated");
  }

  @Test
  void anI2pOnlyTorrentIsAddedAsAnI2pTorrentWithoutWebSeeds() throws Exception {
    local = LocalEngine.start(dir);
    TorrentInfo ti = albumWithWebSeed("i2ponly");
    local.engine.setNetworkMode(local.engine.canonicalInfoHash(ti), TorrentNetworkMode.I2P_ONLY);

    local.engine.download(ti, dir.resolve("content").toFile(), null, null);

    TorrentHandle th = LocalEngine.Events.next(local.events.added, "i2ponly").getTorrentHandle();
    await(() -> TorrentNetworkFlags.isI2pTorrent(th), "the torrent is flagged as I2P");
    await(() -> th.urlSeeds().isEmpty(), "its web seeds are gone");
  }

  @Test
  void aMixedTorrentIsExactlyWhatItWasBeforeTheChoiceExisted() throws Exception {
    local = LocalEngine.start(dir);
    TorrentInfo ti = albumWithWebSeed("mixed");

    local.engine.download(ti, dir.resolve("content").toFile(), null, null);

    TorrentHandle th = LocalEngine.Events.next(local.events.added, "mixed").getTorrentHandle();
    assertFalse(TorrentNetworkFlags.isI2pTorrent(th));
    assertEquals(List.of(SEED), th.urlSeeds());
    local.engine.applyNetworkPolicy(th);
    assertFalse(TorrentNetworkFlags.isI2pTorrent(th), "applying the default changes nothing");
    assertEquals(List.of(SEED), th.urlSeeds());
  }

  @Test
  void aClearnetOnlyTorrentIsNotFlaggedAndKeepsItsWebSeeds() throws Exception {
    local = LocalEngine.start(dir);
    TorrentInfo ti = albumWithWebSeed("clearnet");
    local.engine.setNetworkMode(
        local.engine.canonicalInfoHash(ti), TorrentNetworkMode.CLEARNET_ONLY);

    local.engine.download(ti, dir.resolve("content").toFile(), null, null);

    TorrentHandle th = LocalEngine.Events.next(local.events.added, "clearnet").getTorrentHandle();
    assertFalse(TorrentNetworkFlags.isI2pTorrent(th));
    assertEquals(List.of(SEED), th.urlSeeds());
  }

  @Test
  void changingTheModeOfARunningTorrentFlipsTheFlagBothWays() throws Exception {
    local = LocalEngine.start(dir);
    TorrentInfo ti = albumWithWebSeed("flip");
    String hash = local.engine.canonicalInfoHash(ti);
    local.engine.download(ti, dir.resolve("content").toFile(), null, null);
    TorrentHandle th = LocalEngine.Events.next(local.events.added, "flip").getTorrentHandle();
    assertFalse(TorrentNetworkFlags.isI2pTorrent(th));

    local.engine.setNetworkMode(hash, TorrentNetworkMode.I2P_ONLY);
    await(() -> TorrentNetworkFlags.isI2pTorrent(th), "flagged after choosing I2P only");
    await(() -> th.urlSeeds().isEmpty(), "web seeds removed after choosing I2P only");

    local.engine.setNetworkMode(hash, TorrentNetworkMode.MIXED);
    await(() -> !TorrentNetworkFlags.isI2pTorrent(th), "flag cleared after choosing mixed");
  }

  @Test
  void aTorrentWithAnI2pTrackerKeepsItsFlagWhenTheModeGoesBackToMixed() throws Exception {
    local = LocalEngine.start(dir);
    Path folder = dir.resolve("content").resolve("native");
    LocalEngine.content(folder.resolve("one.mp3"), 64 * 1024, 7);
    TorrentInfo ti =
        TorrentInfo.bdecode(
            new TorrentBuilder()
                .path(folder.toFile())
                .addTracker("http://tracker.example.i2p/announce")
                .generate()
                .entry()
                .bencode());

    local.engine.download(ti, dir.resolve("content").toFile(), null, null);

    TorrentHandle th = LocalEngine.Events.next(local.events.added, "native").getTorrentHandle();
    String hash = local.engine.canonicalInfoHash(ti);
    local.engine.setNetworkMode(hash, TorrentNetworkMode.I2P_ONLY);
    await(() -> TorrentNetworkFlags.isI2pTorrent(th), "flagged after choosing I2P only");

    local.engine.setNetworkMode(hash, TorrentNetworkMode.MIXED);
    local.engine.applyNetworkPolicy(th);
    assertTrue(TorrentNetworkFlags.isI2pTorrent(th), "an .i2p tracker keeps libtorrent's flag");
  }

  @Test
  void aRestoredTorrentGetsItsChoiceBeforeAnyoneHearsOfIt() throws Exception {
    local = LocalEngine.start(dir);
    TorrentInfo ti = albumWithWebSeed("restored");
    File torrent = torrentFile(ti, dir.resolve("in/restored.torrent"));
    String hash = ti.infoHashV1().toHex();
    local.engine.download(torrent, dir.resolve("save").toFile(), null);
    LocalEngine.Events.next(local.events.added, "restored");
    await(() -> local.engine.resumeTorrentFile(hash).isFile(), "the session copy of the torrent");
    await(() -> local.engine.readSavePath(hash) != null, "the resume data was written");

    local.restart(
        () -> Files.writeString(local.engine.networkPolicyFile().toPath(), hash + "=I2P_ONLY\n"));
    local.engine.restoreDownloads();

    TorrentHandle th = LocalEngine.Events.next(local.events.added, "restored").getTorrentHandle();
    assertTrue(TorrentNetworkFlags.isI2pTorrent(th), "flagged by the time it was announced");
    await(() -> th.urlSeeds().isEmpty(), "web seeds removed");
  }

  @Test
  void modesChangedFromManyThreadsWhileTorrentsAreAddedSettleOnTheFinalChoice() throws Exception {
    local = LocalEngine.start(dir);
    int count = 6;
    List<TorrentInfo> torrents = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      torrents.add(albumWithWebSeed("race" + i));
    }
    ExecutorService pool = Executors.newFixedThreadPool(4);
    List<Future<?>> work = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      TorrentInfo ti = torrents.get(i);
      String hash = local.engine.canonicalInfoHash(ti);
      boolean finalI2p = i % 2 == 0;
      work.add(
          pool.submit(
              () -> {
                for (int n = 0; n < 20; n++) {
                  local.engine.setNetworkMode(
                      hash, n % 2 == 0 ? TorrentNetworkMode.I2P_ONLY : TorrentNetworkMode.MIXED);
                }
                local.engine.setNetworkMode(
                    hash, finalI2p ? TorrentNetworkMode.I2P_ONLY : TorrentNetworkMode.MIXED);
              }));
      work.add(
          pool.submit(
              () -> local.engine.download(ti, dir.resolve("content").toFile(), null, null)));
    }
    for (Future<?> f : work) {
      f.get(60, TimeUnit.SECONDS);
    }
    pool.shutdownNow();

    String[] names = new String[count];
    for (int i = 0; i < count; i++) {
      names[i] = "race" + i;
    }
    Map<String, BTDownload> added = LocalEngine.Events.nextEach(local.events.added, names);
    for (int i = 0; i < count; i++) {
      boolean i2p = i % 2 == 0;
      TorrentHandle th = added.get("race" + i).getTorrentHandle();
      await(
          () -> TorrentNetworkFlags.isI2pTorrent(th) == i2p,
          "torrent " + th.infoHash() + " settled on I2P only = " + i2p);
    }
  }

  private TorrentInfo albumWithWebSeed(String name) throws Exception {
    TorrentInfo ti = album(name);
    ti.addUrlSeed(SEED);
    return ti;
  }

  private TorrentInfo album(String name) throws Exception {
    Path folder = dir.resolve("content").resolve(name);
    LocalEngine.content(folder.resolve("one.mp3"), 64 * 1024, name.hashCode());
    return LocalEngine.torrent(folder.toFile(), null);
  }
}
