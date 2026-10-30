/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1408: UrlUtils.magnetHexInfoHash only matched "magnet:?xt=urn:btih:" in lower
 * case, at the start, with 40 hex digits. Valid BEP 9 magnets with a base32 hash, xt after other
 * parameters, or an upper-case scheme gave null, and Android's TorrentFetcherDownload then skipped
 * its check that the fetched metadata matches the link.
 */
class MagnetHexInfoHashTest {

  private static final String HEX = "dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c";
  private static final String BASE32 = "3WBFL3G4PSSV7MF37AJSHWDQMLNR63I4";

  @Test
  void theCanonicalFormStillWorks() {
    assertEquals(HEX, UrlUtils.magnetHexInfoHash("magnet:?xt=urn:btih:" + HEX));
    assertEquals(HEX, UrlUtils.magnetHexInfoHash("magnet:?xt=urn:btih:" + HEX.toUpperCase()));
    assertEquals(HEX, UrlUtils.magnetHexInfoHash("magnet:?xt=urn:btih:" + HEX + "&dn=ubuntu"));
  }

  @Test
  void base32HashesAreConvertedToHex() {
    assertEquals(HEX, UrlUtils.magnetHexInfoHash("magnet:?xt=urn:btih:" + BASE32));
    assertEquals(HEX, UrlUtils.magnetHexInfoHash("magnet:?xt=urn:btih:" + BASE32.toLowerCase()));
    assertEquals(HEX, UrlUtils.magnetHexInfoHash("magnet:?xt=urn:btih:" + BASE32 + "&dn=a"));
  }

  @Test
  void xtCanComeAfterOtherParameters() {
    assertEquals(HEX, UrlUtils.magnetHexInfoHash("magnet:?dn=ubuntu&xt=urn:btih:" + HEX));
    assertEquals(
        HEX,
        UrlUtils.magnetHexInfoHash(
            "magnet:?tr=udp%3A%2F%2Ftracker%3A1337&dn=a&xt=urn:btih:" + HEX + "&x.pe=1.2.3.4:5"));
  }

  @Test
  void theSchemeAndUrnAreCaseInsensitive() {
    assertEquals(HEX, UrlUtils.magnetHexInfoHash("MAGNET:?xt=urn:btih:" + HEX));
    assertEquals(HEX, UrlUtils.magnetHexInfoHash("magnet:?XT=URN:BTIH:" + HEX));
  }

  @Test
  void aPercentEncodedUrnIsDecoded() {
    assertEquals(HEX, UrlUtils.magnetHexInfoHash("magnet:?xt=urn%3Abtih%3A" + HEX));
  }

  @Test
  void theFirstBtihWinsWhenThereAreSeveral() {
    String other = "0123456789abcdef0123456789abcdef01234567";
    assertEquals(
        HEX, UrlUtils.magnetHexInfoHash("magnet:?xt=urn:btih:" + HEX + "&xt=urn:btih:" + other));
  }

  /** Still null: anything that isn't a v1 info hash we can check against. */
  @Test
  void anythingElseIsNull() {
    assertNull(UrlUtils.magnetHexInfoHash(null));
    assertNull(UrlUtils.magnetHexInfoHash(""));
    assertNull(UrlUtils.magnetHexInfoHash("magnet:?xt=urn:btih:"));
    assertNull(UrlUtils.magnetHexInfoHash("magnet:?xt=urn:btih:" + HEX.substring(1)));
    assertNull(UrlUtils.magnetHexInfoHash("magnet:?xt=urn:btih:" + HEX + "0"));
    assertNull(UrlUtils.magnetHexInfoHash("magnet:?xt=urn:btih:" + BASE32.substring(1)));
    // 1, 8, 9 and 0 aren't base32 digits.
    assertNull(UrlUtils.magnetHexInfoHash("magnet:?xt=urn:btih:" + BASE32.substring(1) + "1"));
    // A v2-only magnet has no v1 hash.
    assertNull(
        UrlUtils.magnetHexInfoHash(
            "magnet:?xt=urn:btmh:1220" + HEX + HEX.substring(0, 24) + "&dn=a"));
    assertNull(UrlUtils.magnetHexInfoHash("http://example.com/?xt=urn:btih:" + HEX));
    assertNull(UrlUtils.magnetHexInfoHash("magnet:xt=urn:btih:" + HEX));
    assertNull(UrlUtils.magnetHexInfoHash("magnet:?dn=no-hash"));
  }
}
