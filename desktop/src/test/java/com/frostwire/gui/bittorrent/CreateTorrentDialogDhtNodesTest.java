/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.bittorrent.BTEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A trackerless torrent embeds DHT nodes (BEP 5), the routers the session bootstraps from, so a
 * client that starts with an empty DHT can still find peers (frostwire/frostwire#1474).
 */
class CreateTorrentDialogDhtNodesTest {

  @Test
  void hostPortsBecomeNodesAndMalformedOnesAreSkipped() {
    assertEquals(
        List.of(Map.entry("dht.libtorrent.org", 25401), Map.entry("router.bittorrent.com", 6881)),
        CreateTorrentDialog.dhtNodes(
            "dht.libtorrent.org:25401, router.bittorrent.com:6881,no-port,:80,host:,host:99999"));
  }

  @Test
  void theSessionsBootstrapRoutersAreValidNodes() {
    List<Map.Entry<String, Integer>> nodes =
        CreateTorrentDialog.dhtNodes(BTEngine.dhtBootstrapNodes());
    assertFalse(nodes.isEmpty());
    assertEquals(BTEngine.dhtBootstrapNodes().split(",").length, nodes.size());
  }

  @Test
  void onlyTrackerlessTorrentsGetNodes() throws Exception {
    String source =
        Files.readString(
                Path.of("src/main/java/com/frostwire/gui/bittorrent/CreateTorrentDialog.java"))
            .replaceAll("\\s+", "");
    int trackers = source.indexOf("torrent.add_tracker(");
    int nodes = source.indexOf("dhtNodes(BTEngine.dhtBootstrapNodes())");
    assertTrue(trackers >= 0 && nodes > trackers, "nodes are added in the trackerless branch");
    assertTrue(source.substring(trackers, nodes).contains("}else{"), "only without trackers");
  }
}
