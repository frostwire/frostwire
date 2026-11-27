/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.torrentscsv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.FileSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

final class TorrentsCSVSearchPatternTest {

  private static final String HASH = "0123456789abcdef0123456789abcdef01234567";
  private final TorrentsCSVSearchPattern pattern = new TorrentsCSVSearchPattern();

  @Test
  void searchUrlCarriesTheQueryAndSizeLimit() {
    assertEquals(
        "https://torrents-csv.com/service/search?q=ubuntu%20iso&size=50",
        pattern.getSearchUrl("ubuntu%20iso"));
  }

  @Test
  void parsesAPlainArrayAndDerivesTheMagnet() {
    String json =
        "[{\"name\":\"Ubuntu\",\"infohash\":\""
            + HASH.toUpperCase()
            + "\",\"size\":1048576,\"seeds\":12,\"created\":\"2024-03-05\"}]";
    List<FileSearchResult> results = pattern.parseResults(json);
    assertEquals(1, results.size());
    CompositeFileSearchResult r = (CompositeFileSearchResult) results.get(0);
    assertEquals("Ubuntu", r.getDisplayName());
    assertEquals("Ubuntu.torrent", r.getFilename());
    assertEquals(1048576L, r.getSize());
    assertEquals(HASH, r.getTorrentHash().get());
    assertEquals(12, r.getSeeds().get());
    assertEquals("TorrentsCSV", r.getSource());
    assertTrue(r.getTorrentUrl().get().startsWith("magnet:?xt=urn:btih:" + HASH));
    assertTrue(r.isTorrent());
  }

  @Test
  void acceptsWrapperObjectsAndAlternateFieldNames() {
    String json =
        "{\"results\":[{\"title\":\"T1\",\"info_hash\":\""
            + HASH
            + "\",\"magnet\":\"magnet:?xt=urn:btih:"
            + HASH
            + "&dn=T1\",\"length\":\"2048\",\"seeders\":\"7\"}]}";
    CompositeFileSearchResult r = (CompositeFileSearchResult) pattern.parseResults(json).get(0);
    assertEquals("T1", r.getDisplayName());
    assertEquals(2048L, r.getSize());
    assertEquals(7, r.getSeeds().get());
    assertEquals("magnet:?xt=urn:btih:" + HASH + "&dn=T1", r.getTorrentUrl().get());
    assertEquals(
        1,
        pattern.parseResults("{\"torrents\":[{\"name\":\"a\",\"hash\":\"" + HASH + "\"}]}").size());
    assertEquals(
        1,
        pattern.parseResults("{\"data\":[{\"filename\":\"a\",\"hash\":\"" + HASH + "\"}]}").size());
  }

  @Test
  void normalisesAndValidatesInfoHashes() {
    String base32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    List<FileSearchResult> b32 =
        pattern.parseResults("[{\"name\":\"b32\",\"infohash\":\"" + base32.toLowerCase() + "\"}]");
    assertEquals(1, b32.size());
    assertEquals(base32, ((CompositeFileSearchResult) b32.get(0)).getTorrentHash().get());

    // stray characters are cleaned away when what remains is a full hex hash
    List<FileSearchResult> dirty =
        pattern.parseResults("[{\"name\":\"d\",\"infohash\":\"zz" + HASH + "!!\"}]");
    assertEquals(1, dirty.size());
    assertEquals(HASH, ((CompositeFileSearchResult) dirty.get(0)).getTorrentHash().get());

    assertTrue(pattern.parseResults("[{\"name\":\"bad\",\"infohash\":\"tooshort\"}]").isEmpty());
    assertTrue(pattern.parseResults("[{\"infohash\":\"" + HASH + "\"}]").isEmpty());
    assertTrue(pattern.parseResults("[{\"name\":\"x\"}]").isEmpty());
  }

  @Test
  void toleratesGarbageAndEmptyInput() {
    assertTrue(pattern.parseResults(null).isEmpty());
    assertTrue(pattern.parseResults("").isEmpty());
    assertTrue(pattern.parseResults("not json").isEmpty());
    assertTrue(pattern.parseResults("{\"unrelated\":1}").isEmpty());
    assertTrue(pattern.parseResults("42").isEmpty());
    // non-object array entries are skipped, valid ones kept
    assertEquals(
        1,
        pattern
            .parseResults("[1,null,{\"name\":\"ok\",\"infohash\":\"" + HASH + "\",\"size\":null}]")
            .size());
  }

  @Test
  void resultsAreCappedAtFifty() {
    StringBuilder json = new StringBuilder("[");
    for (int i = 0; i < 80; i++) {
      if (i > 0) json.append(',');
      json.append("{\"name\":\"n")
          .append(i)
          .append("\",\"infohash\":\"")
          .append(HASH)
          .append("\"}");
    }
    json.append(']');
    assertEquals(50, pattern.parseResults(json.toString()).size());
  }
}
