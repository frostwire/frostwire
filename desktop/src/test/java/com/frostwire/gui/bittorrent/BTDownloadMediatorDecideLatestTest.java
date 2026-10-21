/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BTDownloadMediatorDecideLatestTest {

  /**
   * Selecting a transfer decided whether "Endorse Source Peer" applies on the EDT, reading the info
   * hash from libtorrent (torrent_file_ptr) and the karma chain; a 10-CPU macOS machine stalled for
   * over 5s (Icebase #98eab2d25ad2). The decision now runs in the background and only the latest
   * selection's result is applied.
   */
  @Test
  void decidesInTheBackgroundAndAppliesOnlyTheLatestResult() {
    AtomicInteger seq = new AtomicInteger();
    List<Runnable> background = new ArrayList<>();
    List<Runnable> ui = new ArrayList<>();
    List<String> decidedOn = new ArrayList<>();
    List<Boolean> applied = new ArrayList<>();

    BTDownloadMediator.decideLatest(
        seq, () -> decide(decidedOn, "first", true), background::add, ui::add, applied::add);
    BTDownloadMediator.decideLatest(
        seq, () -> decide(decidedOn, "second", false), background::add, ui::add, applied::add);
    assertEquals(List.of(), decidedOn, "nothing is decided on the selecting thread");

    background.forEach(Runnable::run);
    ui.forEach(Runnable::run);
    assertEquals(List.of("first", "second"), decidedOn);
    assertEquals(List.of(false), applied, "the stale first result is dropped");
  }

  private static boolean decide(List<String> log, String name, boolean result) {
    log.add(name);
    return result;
  }
}
