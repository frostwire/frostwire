/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.Test;

/**
 * The Create Torrent dialog shows how far hashing has got, from libtorrent's per-piece progress
 * callback, posting an update only when the percentage changes (frostwire/frostwire#1474).
 */
class CreateTorrentDialogHashingProgressTest {

  @Test
  void eachPieceMovesThePercentage() {
    List<Integer> shown = new ArrayList<>();
    IntConsumer hashed = CreateTorrentDialog.hashingPercentReporter(4, shown::add);

    for (int piece = 0; piece < 4; piece++) {
      hashed.accept(piece);
    }

    assertEquals(List.of(25, 50, 75, 100), shown);
  }

  @Test
  void manyPiecesPostAtMostOneUpdatePerPercent() {
    List<Integer> shown = new ArrayList<>();
    IntConsumer hashed = CreateTorrentDialog.hashingPercentReporter(250_000, shown::add);

    for (int piece = 0; piece < 250_000; piece++) {
      hashed.accept(piece);
    }

    // 0% through 100%, each once.
    assertEquals(101, shown.size());
    assertEquals(0, shown.get(0));
    assertEquals(100, shown.get(shown.size() - 1));
  }

  @Test
  void noPiecesReportNothing() {
    List<Integer> shown = new ArrayList<>();
    CreateTorrentDialog.hashingPercentReporter(0, shown::add).accept(0);
    assertTrue(shown.isEmpty());
  }
}
