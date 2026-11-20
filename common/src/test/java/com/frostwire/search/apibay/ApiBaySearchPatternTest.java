/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.search.apibay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.FileSearchResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Parser tests of the ApiBay search (frostwire/frostwire#1533) over responses recorded from
 * https://apibay.org/q.php?q=ubuntu&cat=0 on 2026-11-20. The network is not a fixture: the live
 * search is in the desktop module's ApiBaySearchPatternTest, behind -Plive.
 */
class ApiBaySearchPatternTest {

  private static final String HASH = "2C6B6858D61DA9543D4231A71DB4B1C9264B0685";
  private final ApiBaySearchPattern pattern = new ApiBaySearchPattern();

  private static String fixture(String name) throws IOException {
    try (InputStream in = ApiBaySearchPatternTest.class.getResourceAsStream("/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String row(String name, String hash, String seeders, String size) {
    return "{\"id\":\"7\",\"name\":"
        + name
        + ",\"info_hash\":\""
        + hash
        + "\",\"seeders\":"
        + seeders
        + ",\"leechers\":\"1\",\"size\":"
        + size
        + ",\"num_files\":\"1\",\"added\":\"1652877231\",\"category\":\"303\"}";
  }

  private List<FileSearchResult> parse(String body) {
    return pattern.parseResults(body);
  }

  @Test
  void searchUrlQueriesApibayDirectly() {
    assertEquals(
        "https://apibay.org/q.php?q=ubuntu%2024&cat=0", pattern.getSearchUrl("ubuntu%2024"));
  }

  @Test
  void searchHostIsInTheSslHostAllowList() {
    java.net.URI uri = java.net.URI.create(pattern.getSearchUrl("ubuntu"));
    assertTrue(com.frostwire.util.Ssl.fwHostnameVerifier().verify(uri.getHost(), null));
  }

  @Test
  void recordedResponseParses() throws IOException {
    List<FileSearchResult> results =
        parse(fixture("com/frostwire/search/apibay/apibay-ubuntu.json"));

    assertEquals(3, results.size());
    CompositeFileSearchResult first = (CompositeFileSearchResult) results.get(0);
    assertEquals("Ubuntu 22.04 LTS", first.getDisplayName());
    assertEquals("Ubuntu 22.04 LTS.torrent", first.getFilename());
    assertEquals("apibay", first.getSource());
    assertEquals(3654957056L, first.getSize());
    assertEquals(1652877231L, first.getCreationTime());
    assertEquals("https://thepiratebay.org/description.php?id=59191690", first.getDetailsUrl());
    assertTrue(first.isTorrent());
    assertEquals(HASH, first.getTorrentHash().get());
    assertEquals(34, first.getSeeds().get());
    assertTrue(
        first.getTorrentUrl().get().startsWith("magnet:?xt=urn:btih:" + HASH + "&dn=Ubuntu"));
  }

  @Test
  void noResultsRowIsNotAResult() throws IOException {
    assertTrue(parse(fixture("com/frostwire/search/apibay/apibay-no-results.json")).isEmpty());
  }

  @Test
  void emptyAndBlankBodiesGiveNoResults() {
    assertTrue(parse(null).isEmpty());
    assertTrue(parse("").isEmpty());
    assertTrue(parse("  \n").isEmpty());
    assertTrue(parse("[]").isEmpty());
  }

  @Test
  void malformedJsonGivesNoResults() {
    assertTrue(parse("[{\"name\":\"x\",").isEmpty());
    assertTrue(parse("<html>Cloudflare</html>").isEmpty());
  }

  @Test
  void notAnArrayGivesNoResults() {
    assertTrue(parse("{\"error\":\"rate limited\"}").isEmpty());
    assertTrue(parse("\"[]\"").isEmpty());
    assertTrue(parse("42").isEmpty());
    assertTrue(parse("null").isEmpty());
  }

  @Test
  void rowsMissingNameOrHashAreSkippedAndTheRestKept() {
    String body =
        "[{\"id\":\"1\",\"info_hash\":\""
            + HASH
            + "\"},{\"id\":\"2\",\"name\":\"no hash\"},"
            + "{\"name\":null,\"info_hash\":\""
            + HASH
            + "\"},"
            + "{\"name\":\"\",\"info_hash\":\""
            + HASH
            + "\"},42,null,\"text\",[],"
            + row("\"kept\"", HASH, "\"5\"", "\"10\"")
            + "]";
    List<FileSearchResult> results = parse(body);
    assertEquals(1, results.size());
    assertEquals("kept", results.get(0).getDisplayName());
  }

  @Test
  void invalidInfoHashesAreSkipped() {
    String body =
        "["
            + row("\"short\"", "ABCD", "\"1\"", "\"1\"")
            + ","
            + row("\"not hex\"", "ZZ6B6858D61DA9543D4231A71DB4B1C9264B0685", "\"1\"", "\"1\"")
            + ","
            + row("\"injected\"", HASH.substring(0, 30) + "&tr=http://x", "\"1\"", "\"1\"")
            + ","
            + row("\"zeros\"", "0".repeat(40), "\"0\"", "\"0\"")
            + "]";
    assertTrue(parse(body).isEmpty());
  }

  @Test
  void numbersMayBeJsonNumbersOrStrings() {
    CompositeFileSearchResult r =
        (CompositeFileSearchResult) parse("[" + row("\"n\"", HASH, "12", "3000") + "]").get(0);
    assertEquals(12, r.getSeeds().get());
    assertEquals(3000L, r.getSize());
  }

  @Test
  void hugeNumbersAreClampedNotRejected() {
    String huge = "\"99999999999999999999999999999\"";
    List<FileSearchResult> results = parse("[" + row("\"big\"", HASH, huge, huge) + "]");
    assertEquals(1, results.size());
    CompositeFileSearchResult r = (CompositeFileSearchResult) results.get(0);
    assertEquals(Integer.MAX_VALUE, r.getSeeds().get());
    assertEquals(Long.MAX_VALUE, r.getSize());
  }

  @Test
  void seedersAboveIntRangeAreClamped() {
    List<FileSearchResult> results =
        parse("[" + row("\"s\"", HASH, "\"3000000000\"", "\"1\"") + "]");
    assertEquals(Integer.MAX_VALUE, ((CompositeFileSearchResult) results.get(0)).getSeeds().get());
  }

  @Test
  void nonNumericAndNegativeNumbersCountAsZero() {
    List<FileSearchResult> results =
        parse(
            "["
                + row("\"w\"", HASH, "\"lots\"", "\"-5\"")
                + ","
                + row("\"x\"", HASH, "{}", "[]")
                + "]");
    assertEquals(2, results.size());
    for (FileSearchResult r : results) {
      assertEquals(0, ((CompositeFileSearchResult) r).getSeeds().get());
      assertEquals(0L, r.getSize());
    }
  }

  @Test
  void longNamesAreTruncatedForDisplayOnly() {
    String name = "n".repeat(400);
    CompositeFileSearchResult r =
        (CompositeFileSearchResult)
            parse("[" + row("\"" + name + "\"", HASH, "\"1\"", "\"1\"") + "]").get(0);
    assertEquals(150, r.getDisplayName().length());
    assertTrue(r.getTorrentUrl().get().contains("&dn=" + name));
  }
}
