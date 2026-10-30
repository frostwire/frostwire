/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.transfers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Compatibility fixtures for the TorrentUrlInfo.getHash fix (frostwire/frostwire#1367). The
 * boundary is the btih value's length and alphabet: a 40-digit hex hash (any case, followed by more
 * parameters or not) must come back as the same lowercase hash. Both encodings BEP 9 allows (40
 * hex, 32 base32) name the same 20-byte info hash.
 */
public class TorrentUrlInfoCompatibilityTest {

  private static final String HEX = "dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c";
  private static final String BASE32 = "3WBFL3G4PSSV7MF37AJSHWDQMLNR63I4";

  private static String hash(String url) {
    return new TorrentUrlInfo(url).getHash();
  }

  @Test
  public void fortyHexDigitsAtTheEndOfTheLink() {
    assertEquals(HEX, hash("magnet:?xt=urn:btih:" + HEX));
  }

  @Test
  public void fortyHexDigitsInAnyCase() {
    assertEquals(HEX, hash("magnet:?xt=urn:btih:" + HEX.toUpperCase()));
    assertEquals(HEX, hash("magnet:?xt=urn:btih:DD8255ecdc7ca55fb0bbf81323d87062db1f6D1C"));
  }

  @Test
  public void fortyHexDigitsFollowedByOtherParameters() {
    assertEquals(HEX, hash("magnet:?xt=urn:btih:" + HEX + "&dn=ubuntu-24.04-desktop-amd64.iso"));
    assertEquals(
        HEX,
        hash(
            "magnet:?xt=urn:btih:"
                + HEX
                + "&dn=a%20b&tr=udp%3A%2F%2Ftracker.example.org%3A1337%2Fannounce&x.pe=1.2.3.4:5"));
    // A hybrid v1/v2 magnet carries a second xt after the v1 hash.
    assertEquals(
        HEX,
        hash(
            "magnet:?xt=urn:btih:"
                + HEX
                + "&xt=urn:btmh:1220"
                + "caf1e1c30e81cb361b9ee167c4aa64228a7fa4fa9f6105232b28ad099f3a302e"));
  }

  @Test
  public void linksThatAreNotMagnetsHaveNoHash() {
    assertNull(hash(null));
    assertNull(hash(""));
    assertNull(hash("https://example.com/" + HEX + ".torrent"));
    assertNull(hash("/sdcard/Download/" + HEX + ".torrent"));
  }

  /**
   * Rejected since the fix (null, "nothing to check"): the old substring(20, 60) threw on links
   * shorter than 60 characters and returned the first 40 characters of a longer value.
   */
  @Test
  public void btihValuesThatAreNotFortyHexDigitsHaveNoHash() {
    assertNull(hash("magnet:?xt=urn:btih:"));
    assertNull(hash("magnet:?xt=urn:btih:" + HEX.substring(0, 39)));
    assertNull(hash("magnet:?xt=urn:btih:" + HEX + "0"));
    assertNull(hash("magnet:?xt=urn:btih:" + HEX.substring(0, 39) + "g"));
  }

  @Test
  public void base32HashesAreTheSameInfoHashInHex() {
    assertEquals(HEX, hash("magnet:?xt=urn:btih:" + BASE32));
    assertEquals(HEX, hash("magnet:?xt=urn:btih:" + BASE32 + "&dn=ubuntu-24.04-desktop-amd64.iso"));
    assertEquals(HEX, hash("magnet:?xt=urn:btih:" + BASE32.toLowerCase()));
  }

  @Test
  public void xtNeedNotBeTheFirstParameter() {
    assertEquals(HEX, hash("magnet:?dn=ubuntu&xt=urn:btih:" + HEX));
    assertEquals(
        HEX, hash("magnet:?tr=udp%3A%2F%2Fx%3A1&dn=a&xt=urn:btih:" + HEX + "&x.pe=1.2.3.4:5"));
  }

  @Test
  public void schemeAndUrnInUpperCase() {
    assertEquals(HEX, hash("MAGNET:?xt=urn:btih:" + HEX));
    assertEquals(HEX, hash("magnet:?xt=URN:BTIH:" + HEX));
  }
}
