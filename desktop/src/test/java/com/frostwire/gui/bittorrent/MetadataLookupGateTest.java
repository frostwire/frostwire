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

/**
 * A transfer whose .torrent can't be read yet re-read and bdecoded its resume file several times
 * per update tick (GitHub #1350). It now looks again at most once per retry interval.
 */
class MetadataLookupGateTest {

  private static final long RETRY = BittorrentDownload.MetadataLookupGate.RETRY_NANOS;

  @Test
  void looksOnceThenWaitsForTheRetryInterval() {
    BittorrentDownload.MetadataLookupGate gate = new BittorrentDownload.MetadataLookupGate();
    long start = -42; // nanoTime can be negative
    assertTrue(gate.tryNow(start), "the first lookup must happen");
    // the four getter calls of one tick, and the ticks after it
    for (long t = start; t < start + RETRY; t += RETRY / 30) {
      assertFalse(gate.tryNow(t), "looked again " + (t - start) + " ns after the last lookup");
    }
    assertTrue(gate.tryNow(start + RETRY), "the retry must come once the interval passed");
    assertFalse(gate.tryNow(start + RETRY + 1));
  }

  @Test
  void theMetadataLookupGoesThroughTheGate() throws Exception {
    String source =
        Files.readString(
                Path.of("src/main/java/com/frostwire/gui/bittorrent/BittorrentDownload.java"))
            .replaceAll("\\s+", "");
    assertTrue(
        source.contains("if(holder==null&&metadataLookupGate.tryNow(System.nanoTime())){"),
        "setupMetadataHolder reads the resume file without the gate");
  }
}
