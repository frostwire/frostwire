/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** frostwire/frostwire#1304: persisted per-torrent network mode, keyed by canonical info hash. */
class TorrentNetworkPolicyStoreTest {

  private static final String V1 = "0123456789abcdef0123456789abcdef01234567";
  private static final String V2 =
      "89abcdef0123456789abcdef0123456789abcdef0123456789abcdef01234567";
  private static final String V1_B = "ffffffffffffffffffffffffffffffffffffffff";

  @TempDir Path dir;

  private TorrentNetworkPolicyStore store() {
    return new TorrentNetworkPolicyStore(new File(dir.toFile(), "network-policy.txt"));
  }

  @Test
  void defaultIsMixedAndMixedIsTheCurrentBehavior() {
    TorrentNetworkPolicyStore s = store();
    assertEquals(TorrentNetworkMode.MIXED, TorrentNetworkMode.DEFAULT);
    assertEquals(TorrentNetworkMode.MIXED, s.get(V1));
    assertTrue(TorrentNetworkMode.MIXED.allowsClearnet() && TorrentNetworkMode.MIXED.allowsI2p());
    assertFalse(TorrentNetworkMode.I2P_ONLY.allowsClearnet());
    assertTrue(TorrentNetworkMode.I2P_ONLY.allowsI2p());
    assertTrue(TorrentNetworkMode.CLEARNET_ONLY.allowsClearnet());
    assertFalse(TorrentNetworkMode.CLEARNET_ONLY.allowsI2p());
  }

  @Test
  void roundTripsThroughTheFile() {
    TorrentNetworkPolicyStore a = store();
    assertTrue(a.set(V1, TorrentNetworkMode.I2P_ONLY));
    assertTrue(a.set(V2, TorrentNetworkMode.CLEARNET_ONLY));
    assertTrue(a.flushIfDirty());

    TorrentNetworkPolicyStore b = store();
    b.load();
    assertEquals(TorrentNetworkMode.I2P_ONLY, b.get(V1));
    assertEquals(TorrentNetworkMode.CLEARNET_ONLY, b.get(V2));
    assertEquals(TorrentNetworkMode.MIXED, b.get(V1_B));
    assertEquals(a.snapshot(), b.snapshot());
  }

  @Test
  void defaultModeIsNotWrittenAndSettingItForgetsTheChoice() throws Exception {
    TorrentNetworkPolicyStore s = store();
    s.set(V1, TorrentNetworkMode.I2P_ONLY);
    s.flushIfDirty();
    s.set(V1, TorrentNetworkMode.MIXED);
    assertTrue(s.flushIfDirty());
    String text = Files.readString(dir.resolve("network-policy.txt"));
    assertFalse(text.contains(V1), text);
    TorrentNetworkPolicyStore again = store();
    again.load();
    assertEquals(TorrentNetworkMode.MIXED, again.get(V1));
  }

  @Test
  void removeForgetsTheChoice() {
    TorrentNetworkPolicyStore s = store();
    s.set(V1, TorrentNetworkMode.I2P_ONLY);
    s.remove(V1);
    assertEquals(TorrentNetworkMode.MIXED, s.get(V1));
    assertTrue(s.snapshot().isEmpty());
  }

  @Test
  void missingFileLoadsEmpty() {
    TorrentNetworkPolicyStore s = store();
    s.set(V1, TorrentNetworkMode.I2P_ONLY);
    s.load();
    assertEquals(TorrentNetworkMode.MIXED, s.get(V1));
  }

  @Test
  void unknownModesFallBackToMixedWithoutFailingTheOtherLines() {
    String text =
        "# comment\n"
            + V1
            + "=I2P_ONLY\n"
            + V2
            + "=QUANTUM_ONLY\n"
            + V1_B
            + "=\n"
            + "\n"
            + "garbage line\n"
            + "=I2P_ONLY\n"
            + "not-a-hash=I2P_ONLY\n"
            + V1.substring(0, 39)
            + "=I2P_ONLY\n"
            + V1.replace('0', 'g')
            + "=I2P_ONLY\n";
    Map<String, TorrentNetworkMode> parsed = TorrentNetworkPolicyStore.parse(text);
    assertEquals(Map.of(V1, TorrentNetworkMode.I2P_ONLY), parsed);
  }

  @Test
  void parsingIsLenientAboutCaseWhitespaceAndLineEndings() {
    String text = "  " + V1.toUpperCase() + " =  i2p_only \r\n" + V2 + "=Clearnet_Only";
    Map<String, TorrentNetworkMode> parsed = TorrentNetworkPolicyStore.parse(text);
    assertEquals(TorrentNetworkMode.I2P_ONLY, parsed.get(V1));
    assertEquals(TorrentNetworkMode.CLEARNET_ONLY, parsed.get(V2));
  }

  @Test
  void aLaterLineWins() {
    String text = V1 + "=I2P_ONLY\n" + V1 + "=CLEARNET_ONLY\n";
    assertEquals(TorrentNetworkMode.CLEARNET_ONLY, TorrentNetworkPolicyStore.parse(text).get(V1));
    assertTrue(TorrentNetworkPolicyStore.parse(text + V1 + "=MIXED\n").isEmpty());
  }

  @Test
  void parseNeverThrowsOnHostileText() {
    assertTrue(TorrentNetworkPolicyStore.parse(null).isEmpty());
    assertTrue(TorrentNetworkPolicyStore.parse("").isEmpty());
    assertTrue(TorrentNetworkPolicyStore.parse("\0\0\n\r\r\n====\n#\n").isEmpty());
    assertTrue(TorrentNetworkPolicyStore.parse("x".repeat(5_000_000)).isEmpty());
    assertTrue(TorrentNetworkPolicyStore.parse(V1 + "=" + "I2P_ONLY".repeat(100_000)).isEmpty());
  }

  @Test
  void parseCapsTheNumberOfEntries() {
    StringBuilder sb = new StringBuilder();
    int total = TorrentNetworkPolicyStore.MAX_ENTRIES + 50;
    for (int i = 0; i < total; i++) {
      sb.append(String.format("%040x", i + 1)).append("=I2P_ONLY\n");
    }
    assertEquals(TorrentNetworkPolicyStore.MAX_ENTRIES, TorrentNetworkPolicyStore.parse(sb).size());
  }

  @Test
  void invalidKeysAreRejectedAndReadAsDefault() {
    TorrentNetworkPolicyStore s = store();
    assertFalse(s.set(null, TorrentNetworkMode.I2P_ONLY));
    assertFalse(s.set("", TorrentNetworkMode.I2P_ONLY));
    assertFalse(s.set("abc", TorrentNetworkMode.I2P_ONLY));
    assertFalse(s.set(V1, null));
    assertEquals(TorrentNetworkMode.MIXED, s.get(null));
    assertEquals(TorrentNetworkMode.MIXED, s.get("zz"));
    s.remove(null);
    assertTrue(s.snapshot().isEmpty());
  }

  @Test
  void keysAreCaseInsensitive() {
    TorrentNetworkPolicyStore s = store();
    s.set(V1.toUpperCase(), TorrentNetworkMode.I2P_ONLY);
    assertEquals(TorrentNetworkMode.I2P_ONLY, s.get(V1));
    assertEquals(TorrentNetworkMode.I2P_ONLY, s.get(" " + V1.toUpperCase() + " "));
  }

  @Test
  void canonicalKeyIsV1ForV1AndHybridAndV2ForV2Only() {
    // v1-only
    assertEquals(V1, TorrentNetworkPolicyStore.canonicalKey(V1, null));
    assertEquals(V1, TorrentNetworkPolicyStore.canonicalKey(V1, "0".repeat(64)));
    // hybrid: both hashes, v1 wins, in any letter case
    assertEquals(V1, TorrentNetworkPolicyStore.canonicalKey(V1.toUpperCase(), V2));
    // v2-only
    assertEquals(V2, TorrentNetworkPolicyStore.canonicalKey(null, V2));
    assertEquals(V2, TorrentNetworkPolicyStore.canonicalKey("0".repeat(40), V2));
    // nothing usable
    assertNull(TorrentNetworkPolicyStore.canonicalKey(null, null));
    assertNull(TorrentNetworkPolicyStore.canonicalKey("", "0".repeat(64)));
    assertNull(TorrentNetworkPolicyStore.canonicalKey("xyz", "xyz"));
  }

  @Test
  void hybridTorrentKeepsItsChoiceWhetherLookedUpByV1OrByTheCanonicalKey() {
    TorrentNetworkPolicyStore s = store();
    String key = TorrentNetworkPolicyStore.canonicalKey(V1, V2);
    s.set(key, TorrentNetworkMode.I2P_ONLY);
    s.flushIfDirty();
    TorrentNetworkPolicyStore restarted = store();
    restarted.load();
    assertEquals(
        TorrentNetworkMode.I2P_ONLY, restarted.get(TorrentNetworkPolicyStore.canonicalKey(V1, V2)));
    assertEquals(
        TorrentNetworkMode.I2P_ONLY,
        restarted.get(TorrentNetworkPolicyStore.canonicalKey(V1, null)));
  }

  @Test
  void corruptFileLoadsWhatItCanAndNeverThrows() throws Exception {
    Files.write(
        dir.resolve("network-policy.txt"),
        new byte[] {(byte) 0xff, (byte) 0xfe, 0, '\n', 'x', '='});
    TorrentNetworkPolicyStore s = store();
    s.load();
    assertTrue(s.snapshot().isEmpty());

    Files.write(
        dir.resolve("network-policy.txt"),
        ("junk\n" + V1 + "=I2P_ONLY\n\u0000\u0000").getBytes(StandardCharsets.UTF_8));
    s.load();
    assertEquals(TorrentNetworkMode.I2P_ONLY, s.get(V1));
  }

  @Test
  void anOversizedFileIsIgnored() throws Exception {
    byte[] big = new byte[TorrentNetworkPolicyStore.MAX_FILE_BYTES + 1];
    java.util.Arrays.fill(big, (byte) '#');
    Files.write(dir.resolve("network-policy.txt"), big);
    TorrentNetworkPolicyStore s = store();
    s.set(V1, TorrentNetworkMode.I2P_ONLY);
    s.load();
    assertTrue(s.snapshot().isEmpty());
  }

  @Test
  void flushCreatesMissingDirectoriesAndLeavesNoTempFile() {
    File nested = new File(dir.toFile(), "a/b/policy.txt");
    TorrentNetworkPolicyStore s = new TorrentNetworkPolicyStore(nested);
    s.set(V1, TorrentNetworkMode.I2P_ONLY);
    assertTrue(s.flushIfDirty());
    assertTrue(nested.isFile());
    assertFalse(new File(nested.getPath() + ".tmp").exists());
  }

  @Test
  void flushOverwritesAnExistingFile() throws Exception {
    TorrentNetworkPolicyStore s = store();
    s.set(V1, TorrentNetworkMode.I2P_ONLY);
    s.flushIfDirty();
    s.set(V1, TorrentNetworkMode.CLEARNET_ONLY);
    assertTrue(s.flushIfDirty());
    assertTrue(Files.readString(dir.resolve("network-policy.txt")).contains("CLEARNET_ONLY"));
  }

  @Test
  void flushWithNothingChangedDoesNotTouchTheFile() {
    TorrentNetworkPolicyStore s = store();
    assertTrue(s.flushIfDirty());
    assertFalse(new File(dir.toFile(), "network-policy.txt").exists());
  }

  @Test
  void failedFlushIsRetried() throws Exception {
    // A regular file where the parent directory should be makes every write fail.
    File blocker = new File(dir.toFile(), "blocker");
    Files.write(blocker.toPath(), new byte[0]);
    TorrentNetworkPolicyStore s = new TorrentNetworkPolicyStore(new File(blocker, "policy.txt"));
    s.set(V1, TorrentNetworkMode.I2P_ONLY);
    assertFalse(s.flushIfDirty());
    assertFalse(s.flushIfDirty());
    assertEquals(TorrentNetworkMode.I2P_ONLY, s.get(V1));
  }

  @Test
  void serializeIsSortedAndDeterministic() {
    Map<String, TorrentNetworkMode> in =
        Map.of(
            V1_B, TorrentNetworkMode.CLEARNET_ONLY,
            V1, TorrentNetworkMode.I2P_ONLY,
            V2, TorrentNetworkMode.MIXED);
    String text = TorrentNetworkPolicyStore.serialize(in);
    assertEquals(text, TorrentNetworkPolicyStore.serialize(in));
    assertTrue(text.indexOf(V1) < text.indexOf(V1_B), text);
    assertFalse(text.contains(V2), text);
    assertEquals(
        Map.of(V1, TorrentNetworkMode.I2P_ONLY, V1_B, TorrentNetworkMode.CLEARNET_ONLY),
        TorrentNetworkPolicyStore.parse(text));
  }

  @Test
  void modeParsingNeverThrows() {
    assertEquals(TorrentNetworkMode.MIXED, TorrentNetworkMode.fromPersisted(null));
    assertEquals(TorrentNetworkMode.MIXED, TorrentNetworkMode.fromPersisted(""));
    assertEquals(TorrentNetworkMode.MIXED, TorrentNetworkMode.fromPersisted("i2p"));
    assertEquals(TorrentNetworkMode.MIXED, TorrentNetworkMode.fromPersisted("x".repeat(1000)));
    for (TorrentNetworkMode m : TorrentNetworkMode.values()) {
      assertEquals(m, TorrentNetworkMode.fromPersisted(m.persistedName()));
    }
  }

  @Test
  void concurrentReadersAndWritersSeeConsistentValuesAndNothingIsLost() throws Exception {
    TorrentNetworkPolicyStore s = store();
    int threads = 8;
    int perThread = 500;
    ExecutorService pool = Executors.newFixedThreadPool(threads + 1);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();
    for (int t = 0; t < threads; t++) {
      final int base = t * perThread;
      futures.add(
          pool.submit(
              () -> {
                go.await();
                for (int i = 0; i < perThread; i++) {
                  String key = String.format("%040x", base + i + 1);
                  s.set(key, TorrentNetworkMode.I2P_ONLY);
                  // a reader of a key never sees anything but a real mode
                  TorrentNetworkMode seen = s.get(key);
                  if (seen != TorrentNetworkMode.I2P_ONLY) {
                    throw new AssertionError(key + " read back as " + seen);
                  }
                  if (i % 7 == 0) {
                    s.flushIfDirty();
                  }
                }
                return null;
              }));
    }
    futures.add(
        pool.submit(
            () -> {
              go.await();
              for (int i = 0; i < 200; i++) {
                s.snapshot();
                s.flushIfDirty();
              }
              return null;
            }));
    go.countDown();
    for (Future<?> f : futures) {
      f.get(60, TimeUnit.SECONDS);
    }
    pool.shutdownNow();
    assertTrue(s.flushIfDirty());

    assertEquals(threads * perThread, s.snapshot().size());
    TorrentNetworkPolicyStore restarted = store();
    restarted.load();
    assertEquals(threads * perThread, restarted.snapshot().size());
  }
}
