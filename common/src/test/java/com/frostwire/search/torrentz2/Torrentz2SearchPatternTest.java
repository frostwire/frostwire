/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.torrentz2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.FileSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

final class Torrentz2SearchPatternTest {

  private static final String HASH = "0123456789abcdef0123456789abcdef01234567";

  private static String row(String title, String magnet, String size, String seeders) {
    return "<a href=\"/torrent/"
        + HASH
        + "\" class=\"x\">  "
        + title
        + " </a>"
        + "<i class=\"fas fa-download\"></i><span>"
        + size
        + "</span>"
        + " seeders</span> <span class=\"font-medium\">"
        + seeders
        + "</span>"
        + "<a href=\""
        + magnet
        + "\" class=\"btn\"><i class=\"fas\"></i> Magnet</a>";
  }

  private final Torrentz2SearchPattern pattern = new Torrentz2SearchPattern();

  @Test
  void searchUrlUsesPlusForSpaces() {
    assertEquals("https://torrentz2.nz/search?q=ubuntu+iso", pattern.getSearchUrl("ubuntu%20iso"));
  }

  @Test
  void parsesTitleSizeSeedsAndMagnet() {
    String page =
        "<h2>Torrents</h2>"
            + row(
                "Ubuntu &amp; Friends",
                "magnet:?xt=urn:btih:" + HASH + "&amp;dn=Ubuntu&#x26;tr=udp%3A%2F%2Ft.example%3A80",
                "1.5 GB",
                "42");
    List<FileSearchResult> results = pattern.parseResults(page);
    assertEquals(1, results.size());
    CompositeFileSearchResult r = (CompositeFileSearchResult) results.get(0);
    assertEquals("Ubuntu & Friends", r.getDisplayName());
    assertEquals("Ubuntu & Friends.torrent", r.getFilename());
    assertEquals("https://torrentz2.nz/torrent/" + HASH, r.getDetailsUrl());
    assertEquals("torrentz2", r.getSource());
    assertTrue(r.isTorrent());
    assertEquals(HASH, r.getTorrentHash().get());
    assertEquals(42, r.getSeeds().get());
    assertEquals(1610612736L, r.getSize());
    assertTrue(r.getTorrentUrl().get().startsWith("magnet:?xt=urn:btih:" + HASH));
    assertTrue(r.getTorrentUrl().get().contains("udp://t.example:80"));
    assertFalse(r.isPreliminary());
  }

  @Test
  void missingMetadataFallsBackToUnknownSizeAndZeroSeeds() {
    String page =
        "<a href=\"/torrent/"
            + HASH
            + "\">Bare title</a> <a href=\"magnet:?xt=urn:btih:"
            + HASH
            + "\"> Magnet</a>";
    List<FileSearchResult> results = pattern.parseResults(page);
    assertEquals(1, results.size());
    CompositeFileSearchResult r = (CompositeFileSearchResult) results.get(0);
    assertEquals(-1, r.getSize());
    assertEquals(0, r.getSeeds().get());
  }

  @Test
  void rowsWithBadMagnetsOrEmptyInputYieldNothing() {
    assertTrue(pattern.parseResults(null).isEmpty());
    assertTrue(pattern.parseResults("").isEmpty());
    assertTrue(pattern.parseResults("<html>nothing here</html>").isEmpty());
    // magnet without a btih hash is skipped
    String noHash = row("T", "magnet:?dn=foo", "1 MB", "1");
    assertTrue(pattern.parseResults(noHash).isEmpty());
    // empty title is skipped
    assertTrue(pattern.parseResults(row("", "magnet:?xt=urn:btih:" + HASH, "1 MB", "1")).isEmpty());
  }

  @Test
  void longTitlesAreCappedAndResultsLimitedToOneHundred() {
    String longTitle = "T".repeat(300);
    List<FileSearchResult> one =
        pattern.parseResults(row(longTitle, "magnet:?xt=urn:btih:" + HASH, "1 MB", "1"));
    assertEquals(150, one.get(0).getDisplayName().length());

    StringBuilder many = new StringBuilder("Torrents");
    for (int i = 0; i < 120; i++) {
      many.append(row("T" + i, "magnet:?xt=urn:btih:" + HASH, "1 MB", "1"));
    }
    assertEquals(100, pattern.parseResults(many.toString()).size());
  }
}
