/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.one337x;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.frostwire.search.FileSearchResult;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The 1337x result page parser strips the markup out of each link text. It did that with {@code
 * String.replaceAll("<.*?>", "")}, which backtracks: link text made of tens of thousands of '<'
 * with no '>' took seconds (60 000 of them about 8 s, a 1 MB page tens of minutes), tying up a
 * search thread. The display names below are the boundary the cleanup must keep.
 */
class One337xSearchPatternTest {

  private static List<FileSearchResult> parse(String linkText) {
    return new One337xSearchPattern()
        .parseResults(
            "<td class=\"coll-1 name\"><a"
                + " href=\"/torrent/6187471/ubuntu-24-04-1-desktop-amd64-iso/\">"
                + linkText
                + "</a></td>");
  }

  @Test
  void linkTextOfUnclosedTagsParsesInLinearTime() {
    String hostile = "<".repeat(60_000);
    List<FileSearchResult> results =
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> parse(hostile));
    assertEquals(1, results.size());
    assertEquals(hostile.substring(0, 150), results.get(0).getDisplayName());
  }

  @Test
  void plainLinkTextIsKept() {
    List<FileSearchResult> results = parse("ubuntu-24.04.1-desktop-amd64.iso");
    assertEquals(1, results.size());
    assertEquals("ubuntu-24.04.1-desktop-amd64.iso", results.get(0).getDisplayName());
    assertEquals("ubuntu-24.04.1-desktop-amd64.iso.torrent", results.get(0).getFilename());
    assertEquals(
        "https://www.1377x.to/torrent/6187471/ubuntu-24-04-1-desktop-amd64-iso/",
        results.get(0).getDetailsUrl());
  }

  @Test
  void tagsInsideLinkTextAreStripped() {
    assertEquals(
        "Linux Mint 21 Cinnamon 64bit",
        parse("Linux Mint 21 <b>Cinnamon</b> 64bit").get(0).getDisplayName());
    assertEquals(
        "x264", parse("<span class=\"hl\"><i class=\"x\"></i>x264</span>").get(0).getDisplayName());
  }

  @Test
  void strayAngleBracketsOutsideTagsAreKept() {
    assertEquals("a > b", parse("a > b").get(0).getDisplayName());
    assertEquals("<<b", parse("<<b").get(0).getDisplayName());
  }

  @Test
  void longNamesAreCutAt150Characters() {
    String name = "n".repeat(151);
    assertEquals(name.substring(0, 150), parse(name).get(0).getDisplayName());
  }
}
