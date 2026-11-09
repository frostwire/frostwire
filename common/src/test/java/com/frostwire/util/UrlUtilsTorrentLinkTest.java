/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link UrlUtils#isMagnetLink(String)} and {@link UrlUtils#isTorrentLink(String)} decide whether
 * text typed or pasted into a search or URL box starts a download. A check for the exact prefix
 * {@code magnet:?xt=urn:btih:} sent valid magnets to a search instead (frostwire/frostwire#1513).
 */
class UrlUtilsTorrentLinkTest {

  private static final String HEX = "c12fe1c06bba254a9dc9f519b335aa7c1367a88a";

  @Test
  void magnetWithXtFirstIsAMagnetLink() {
    assertTrue(UrlUtils.isMagnetLink("magnet:?xt=urn:btih:" + HEX));
  }

  @Test
  void magnetWithNameOrTrackerFirstIsAMagnetLink() {
    assertTrue(UrlUtils.isMagnetLink("magnet:?dn=Ubuntu+24.04&xt=urn:btih:" + HEX));
    assertTrue(
        UrlUtils.isMagnetLink(
            "magnet:?tr=udp%3A%2F%2Ftracker.example%3A1337&dn=x&xt=urn:btih:" + HEX));
  }

  @Test
  void magnetInAnyCaseIsAMagnetLink() {
    assertTrue(UrlUtils.isMagnetLink("Magnet:?xt=urn:btih:" + HEX));
    assertTrue(UrlUtils.isMagnetLink("MAGNET:?XT=URN:BTIH:" + HEX.toUpperCase()));
  }

  @Test
  void magnetWithBase32HashIsAMagnetLink() {
    assertTrue(UrlUtils.isMagnetLink("magnet:?xt=urn:btih:YEX6DQDLXISUUTOJ6UM3GNK2PQJWPKEK"));
  }

  @Test
  void hybridMagnetIsAMagnetLink() {
    assertTrue(
        UrlUtils.isMagnetLink(
            "magnet:?xt=urn:btmh:1220" + HEX + HEX.substring(0, 24) + "&xt=urn:btih:" + HEX));
  }

  @Test
  void surroundingWhitespaceIsIgnored() {
    assertTrue(UrlUtils.isMagnetLink("  magnet:?xt=urn:btih:" + HEX + "\n"));
    assertTrue(UrlUtils.isTorrentLink("\thttps://example.com/a.torrent "));
  }

  @Test
  void textThatIsNotADownloadableMagnetIsNot() {
    assertFalse(UrlUtils.isMagnetLink(null));
    assertFalse(UrlUtils.isMagnetLink(""));
    assertFalse(UrlUtils.isMagnetLink("ubuntu"));
    assertFalse(UrlUtils.isMagnetLink("magnet"));
    assertFalse(UrlUtils.isMagnetLink("magnet:?dn=only-a-name"));
    assertFalse(UrlUtils.isMagnetLink("magnet:?xt=urn:btih:c12fe1c0"));
    assertFalse(UrlUtils.isMagnetLink("magnets for my fridge"));
  }

  @Test
  void torrentFileUrlsAreTorrentLinks() {
    assertTrue(UrlUtils.isTorrentLink("https://example.com/files/ubuntu.torrent"));
    assertTrue(UrlUtils.isTorrentLink("http://example.com/UBUNTU.TORRENT"));
    assertTrue(UrlUtils.isTorrentLink("https://example.com/get/a.torrent?key=1&b=2"));
    assertTrue(UrlUtils.isTorrentLink("https://example.com/a.torrent#top"));
    assertTrue(UrlUtils.isTorrentLink("magnet:?dn=x&xt=urn:btih:" + HEX));
  }

  @Test
  void otherTextIsNotATorrentLink() {
    assertFalse(UrlUtils.isTorrentLink(null));
    assertFalse(UrlUtils.isTorrentLink("ubuntu torrent"));
    assertFalse(UrlUtils.isTorrentLink("https://example.com/watch?v=abc"));
    assertFalse(UrlUtils.isTorrentLink("https://example.com/search?q=a.torrent"));
    assertFalse(UrlUtils.isTorrentLink("ftp://example.com/a.torrent"));
    assertFalse(UrlUtils.isTorrentLink("ubuntu.torrent"));
  }

  @Test
  void normalizedLinksHaveALowerCaseMagnetSchemeAndNoSurroundingWhitespace() {
    assertEquals(
        "magnet:?XT=urn:btih:" + HEX, UrlUtils.normalizeTorrentLink(" MAGNET:?XT=urn:btih:" + HEX));
    assertEquals(
        "magnet:?xt=urn:btih:" + HEX, UrlUtils.normalizeTorrentLink("magnet:?xt=urn:btih:" + HEX));
    assertEquals(
        "https://example.com/A.torrent",
        UrlUtils.normalizeTorrentLink("https://example.com/A.torrent\n"));
    assertNull(UrlUtils.normalizeTorrentLink(null));
  }
}
