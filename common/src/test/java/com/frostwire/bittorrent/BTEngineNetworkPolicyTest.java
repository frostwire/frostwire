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

import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.TorrentBuilder;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.swig.settings_pack;
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
  void theReasonToHoldAnI2pOnlyTorrentBackNamesWhatIsMissing() {
    assertTrue(BTEngine.blockReason(false, null).contains("not enabled"));
    assertTrue(
        BTEngine.blockReason(false, "boom").contains("not enabled"), "not enabled comes first");
    assertTrue(BTEngine.blockReason(true, "connection refused").contains("connection refused"));
    assertEquals(null, BTEngine.blockReason(true, null));
  }

  @Test
  void anI2pOnlyTorrentWithoutI2pNeverConnectsToAClearnetPeer() throws Exception {
    // The control: the same transfer, mixed, finishes from the clearnet seeder.
    TorrentInfo ti = album("closed");
    File seedData = dir.resolve("content").toFile();
    local = LocalEngine.start(dir);
    com.frostwire.jlibtorrent.SessionManager seeder = LocalEngine.seed(ti, seedData);
    try {
      var peer = LocalEngine.endpoint(seeder);
      String hash = local.engine.canonicalInfoHash(ti);
      local.engine.setNetworkMode(hash, TorrentNetworkMode.I2P_ONLY);

      local.engine.download(ti, dir.resolve("save").toFile(), null, java.util.List.of(peer));

      BTDownload dl = LocalEngine.Events.next(local.events.added, "closed");
      TorrentHandle th = dl.getTorrentHandle();
      await(() -> local.engine.getNetworkBlockReason(hash) != null, "held back");
      assertTrue(local.engine.getNetworkBlockReason(hash).contains("not enabled"));
      await(() -> LocalEngine.isPaused(dl), "paused");

      dl.resume(); // the user presses resume
      local.engine.download(
          ti, dir.resolve("save").toFile(), null, java.util.List.of(peer)); // and adds it again
      Thread.sleep(3000);

      assertTrue(LocalEngine.isPaused(dl), "still paused");
      assertEquals(0, th.status(true).numPeers(), "no peer, clearnet or otherwise");
      assertFalse(th.status(true).isSeeding() || th.status(true).isFinished());

      // Choosing mixed releases it and it finishes from the seeder.
      local.engine.setNetworkMode(hash, TorrentNetworkMode.MIXED);
      await(() -> local.engine.getNetworkBlockReason(hash) == null, "released");
      // (the peer given at add time was dropped while the torrent was I2P only: hand it over again)
      th.swig().connect_peer(peer.swig());
      await(() -> th.status(true).isSeeding(), "downloaded once allowed");
    } finally {
      seeder.stop();
    }
  }

  @Test
  void aTorrentTheUserPausedStaysPausedWhenTheHoldEnds() throws Exception {
    local = LocalEngine.start(dir);
    TorrentInfo ti = album("userpaused");
    String hash = local.engine.canonicalInfoHash(ti);
    local.engine.setNetworkMode(hash, TorrentNetworkMode.I2P_ONLY);
    local.engine.download(ti, dir.resolve("content").toFile(), null, null);
    BTDownload dl = LocalEngine.Events.next(local.events.added, "userpaused");
    await(() -> local.engine.getNetworkBlockReason(hash) != null, "held back");
    dl.pause(); // the user's own pause

    local.engine.setNetworkMode(hash, TorrentNetworkMode.MIXED);
    await(() -> local.engine.getNetworkBlockReason(hash) == null, "released");
    Thread.sleep(1500);

    assertTrue(LocalEngine.isPaused(dl), "the user's pause is respected");
  }

  @Test
  void aMixedTorrentIsNeverHeldBackWhateverI2pIsDoing() throws Exception {
    TorrentInfo ti = album("open");
    File seedData = dir.resolve("content").toFile();
    local = LocalEngine.start(dir);
    com.frostwire.jlibtorrent.SessionManager seeder = LocalEngine.seed(ti, seedData);
    try {
      var peer = LocalEngine.endpoint(seeder);

      local.engine.download(ti, dir.resolve("save").toFile(), null, java.util.List.of(peer));

      BTDownload dl = LocalEngine.Events.next(local.events.added, "open");
      assertEquals(null, local.engine.getNetworkBlockReason(local.engine.canonicalInfoHash(ti)));
      await(() -> dl.getTorrentHandle().status(true).isSeeding(), "downloaded from the seeder");
    } finally {
      seeder.stop();
    }
  }

  @Test
  void aBridgeThatFailsHoldsAnI2pOnlyTorrentBackAndANewBridgeGetsAnotherChance() throws Exception {
    local = LocalEngine.start(dir);
    TorrentInfo ti = album("bridge");
    String hash = local.engine.canonicalInfoHash(ti);
    // I2P configured, but nothing listens on the SAM port.
    local.engine.applySettings(
        new SettingsPack()
            .setString(settings_pack.string_types.i2p_hostname.swigValue(), "127.0.0.1")
            .setInteger(settings_pack.int_types.i2p_port.swigValue(), 1));
    local.engine.setNetworkMode(hash, TorrentNetworkMode.I2P_ONLY);

    local.engine.download(ti, dir.resolve("content").toFile(), null, null);

    LocalEngine.Events.next(local.events.added, "bridge");
    await(
        () -> {
          String reason = local.engine.getNetworkBlockReason(hash);
          return reason != null && reason.contains("not usable");
        },
        "held back because the SAM bridge failed");
  }

  private static final int ALLOW_MIXED = settings_pack.bool_types.allow_i2p_mixed.swigValue();

  private boolean sessionAllowsMixed() {
    return local.engine.settings().getBoolean(ALLOW_MIXED);
  }

  private void userSetsMixed(boolean on) {
    local.engine.applySettings(new SettingsPack().setBoolean(ALLOW_MIXED, on));
  }

  @Test
  void anI2pOnlyTorrentForcesMixedModeOffAndTheUsersChoiceComesBack() throws Exception {
    local = LocalEngine.start(dir);
    String hash = local.engine.canonicalInfoHash(album("rule"));
    userSetsMixed(true);
    assertTrue(sessionAllowsMixed(), "no I2P only torrent: the user's choice stands");

    local.engine.setNetworkMode(hash, TorrentNetworkMode.I2P_ONLY);
    await(() -> !sessionAllowsMixed(), "mixed mode forced off");

    local.engine.setNetworkMode(hash, TorrentNetworkMode.MIXED);
    await(this::sessionAllowsMixed, "the user's mixed mode is back");
  }

  @Test
  void whileAnI2pOnlyTorrentExistsTheUsersOptionsChangeIsRememberedButNotApplied()
      throws Exception {
    local = LocalEngine.start(dir);
    String hash = local.engine.canonicalInfoHash(album("remembered"));
    local.engine.setNetworkMode(hash, TorrentNetworkMode.I2P_ONLY);

    userSetsMixed(true);
    assertFalse(sessionAllowsMixed(), "forced off while I2P only exists");

    local.engine.setNetworkMode(hash, TorrentNetworkMode.CLEARNET_ONLY);
    await(this::sessionAllowsMixed, "the choice made meanwhile applies once none is left");
    userSetsMixed(false);
    assertFalse(sessionAllowsMixed());
  }

  @Test
  void removingTheLastI2pOnlyTorrentGivesTheUsersMixedModeBack() throws Exception {
    local = LocalEngine.start(dir);
    TorrentInfo ti = album("gone");
    String hash = local.engine.canonicalInfoHash(ti);
    userSetsMixed(true);
    local.engine.setNetworkMode(hash, TorrentNetworkMode.I2P_ONLY);
    await(() -> !sessionAllowsMixed(), "forced off");

    local.engine.markRemoved(hash);

    await(this::sessionAllowsMixed, "back after the torrent was removed");
    assertEquals(TorrentNetworkMode.MIXED, local.engine.getNetworkMode(hash));
  }

  @Test
  void aSettingsChangeThatLeavesMixedModeAloneDoesNotTouchIt() throws Exception {
    local = LocalEngine.start(dir);
    String hash = local.engine.canonicalInfoHash(album("other"));
    userSetsMixed(true);
    local.engine.setNetworkMode(hash, TorrentNetworkMode.I2P_ONLY);
    await(() -> !sessionAllowsMixed(), "forced off");

    local.engine.applySettings(new SettingsPack().connectionsLimit(123));

    assertFalse(sessionAllowsMixed());
    assertEquals(123, local.engine.settings().connectionsLimit());
  }

  @Test
  void withoutAnyI2pOnlyTorrentTheSessionKeepsTheUsersMixedModeExactly() throws Exception {
    local = LocalEngine.start(dir);
    userSetsMixed(true);
    local.engine.setNetworkMode(
        local.engine.canonicalInfoHash(album("clear")), TorrentNetworkMode.CLEARNET_ONLY);
    local.engine.setNetworkMode(
        local.engine.canonicalInfoHash(album("mix")), TorrentNetworkMode.MIXED);
    local.engine.setNetworkMode(
        local.engine.canonicalInfoHash(album("mix2")), TorrentNetworkMode.MIXED);
    assertTrue(sessionAllowsMixed());
    userSetsMixed(false);
    assertFalse(sessionAllowsMixed());
  }

  @Test
  void theSessionStartsWithMixedModeOffWhenAnI2pOnlyTorrentWasSaved() throws Exception {
    local = LocalEngine.create(dir);
    local.ctx.i2pEnabled = true;
    local.ctx.i2pHostname = "127.0.0.1";
    local.ctx.i2pPort = 1; // nothing listens here: the SAM bridge is unreachable
    local.ctx.i2pAllowMixed = true;
    Files.writeString(
        local.engine.networkPolicyFile().toPath(),
        "0123456789abcdef0123456789abcdef01234567=I2P_ONLY\n");

    local.engine.start();

    assertFalse(sessionAllowsMixed());
    local.engine.setNetworkMode(
        "0123456789abcdef0123456789abcdef01234567", TorrentNetworkMode.MIXED);
    await(this::sessionAllowsMixed, "the user's choice (mixed) is back");
  }

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
