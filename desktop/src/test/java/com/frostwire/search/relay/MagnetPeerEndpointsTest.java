/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.util.UrlUtils;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class MagnetPeerEndpointsTest {

  private static final String HASH = "0123456789abcdef0123456789abcdef01234567";

  @Test
  void magnetWithoutEndpointsMatchesLegacyBuilder() {
    String legacy = UrlUtils.buildMagnetUrl(HASH, "file.bin", "tr=udp%3A%2F%2Ft.example%3A80");
    String withNull =
        UrlUtils.buildMagnetUrl(HASH, "file.bin", "tr=udp%3A%2F%2Ft.example%3A80", null);
    String withEmpty =
        UrlUtils.buildMagnetUrl(
            HASH, "file.bin", "tr=udp%3A%2F%2Ft.example%3A80", Collections.emptyList());
    assertEquals(legacy, withNull);
    assertEquals(legacy, withEmpty);
    assertFalse(legacy.contains("x.pe"));
  }

  @Test
  void magnetWithEndpointsAppendsXpeParams() {
    String magnet =
        UrlUtils.buildMagnetUrl(
            HASH, "file.bin", "tr=x", Arrays.asList("192.168.1.10:45321", "76.130.145.63:45321"));
    assertTrue(magnet.startsWith("magnet:?xt=urn:btih:" + HASH));
    assertTrue(magnet.contains("&x.pe=192.168.1.10:45321"));
    assertTrue(magnet.contains("&x.pe=76.130.145.63:45321"));
  }

  @Test
  void magnetEndpointsCappedAtFourAndBlanksSkipped() {
    String magnet =
        UrlUtils.buildMagnetUrl(
            HASH,
            "f",
            "tr=x",
            Arrays.asList("1.1.1.1:1", "", "2.2.2.2:2", "3.3.3.3:3", "4.4.4.4:4", "5.5.5.5:5"));
    int count = magnet.split("&x\\.pe=", -1).length - 1;
    assertEquals(4, count);
    assertFalse(magnet.contains("5.5.5.5"));
  }
}
