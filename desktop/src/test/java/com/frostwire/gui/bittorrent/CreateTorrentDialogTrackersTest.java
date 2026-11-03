/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Create Torrent's tracker tiers, private flag and comment (frostwire/frostwire#1474). Every
 * tracker used to go in tier 0, the torrent was never private, and it had no comment.
 */
class CreateTorrentDialogTrackersTest {

  @Test
  void aBlankLineStartsTheNextTier() {
    CreateTorrentDialog.ParsedTrackers parsed =
        CreateTorrentDialog.parseTrackers(
            "udp://a.example:1337/announce\nhttps://b.example/announce\n\n\n"
                + "c.example/announce\n");

    assertNull(parsed.invalid());
    assertEquals(
        List.of(
            Map.entry("udp://a.example:1337/announce", 0),
            Map.entry("https://b.example/announce", 0),
            Map.entry("http://c.example/announce", 1)),
        parsed.trackers());
  }

  @Test
  void leadingBlankLinesDoNotSkipATier() {
    assertEquals(
        List.of(Map.entry("udp://a.example:1337", 0)),
        CreateTorrentDialog.parseTrackers("\n\nudp://a.example:1337\n\n").trackers());
  }

  @Test
  void anInvalidUrlOrNoneAtAllIsReported() {
    assertEquals("http://not a url", CreateTorrentDialog.parseTrackers("not a url").invalid());
    assertEquals("", CreateTorrentDialog.parseTrackers(" \n\n").invalid());
  }

  @Test
  void tiersAreWrittenBackWithABlankLineBetweenThem() {
    assertEquals(
        "udp://a:1\nudp://b:2\n\nudp://c:3\n",
        CreateTorrentDialog.formatTrackers(
            List.of(
                Map.entry("udp://a:1", 0), Map.entry("udp://b:2", 0), Map.entry("udp://c:3", 1))));
  }

  @Test
  void privateAndCommentReachLibtorrentAndPrivateTurnsDhtOff() throws Exception {
    String source =
        Files.readString(
                Path.of("src/main/java/com/frostwire/gui/bittorrent/CreateTorrentDialog.java"))
            .replaceAll("\\s+", "");
    assertTrue(source.contains("torrent.set_priv(makePrivate);"));
    assertTrue(source.contains("torrent.set_comment(comment);"));
    assertTrue(source.contains("torrent.add_tracker(tracker.getKey(),tracker.getValue());"));
    assertTrue(
        source.contains("if(isPrivate){checkUseDHT.setSelected(false);}"),
        "a private torrent can't be trackerless");
  }
}
