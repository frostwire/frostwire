/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.crawl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RelayControlClientTest {

  @Test
  void parsesLookupPubsAndIgnoresAddresses() {
    String json =
        "{\"ok\":true,\"data\":["
            + "{\"pub\":\"AAECAwQ\",\"host\":\"10.0.0.1\",\"rudpPort\":12345,"
            + "\"role\":\"relay\",\"lastSeenMs\":1700000000000,\"icebridgeVersion\":\"1.2.3\"},"
            + "{\"pub\":\"BQYHCAk\",\"host\":\"10.0.0.2\",\"rudpPort\":23456,"
            + "\"role\":\"peer\",\"lastSeenMs\":1700000001000,\"icebridgeVersion\":\"1.2.4\"}]}";

    List<String> pubs = RelayControlClient.parseLookupPubs(json);

    assertEquals(List.of("AAECAwQ", "BQYHCAk"), pubs);
  }

  @Test
  void parsesCatalogRows() {
    String json =
        "{\"ok\":true,\"data\":["
            + "{\"ih\":\"0123456789abcdef0123456789abcdef01234567\",\"name\":\"Ubuntu 24.04\","
            + "\"s\":5368709120,\"fc\":3,\"pub\":\"AAECAwQ\"},"
            + "{\"ih\":\"fedcba9876543210fedcba9876543210fedcba98\",\"name\":\"Debian\","
            + "\"s\":2147483648,\"fc\":1,\"pub\":\"BQYHCAk\"}]}";

    List<RelayControlClient.CatalogEntry> entries = RelayControlClient.parseCatalog(json);

    assertEquals(2, entries.size());
    RelayControlClient.CatalogEntry first = entries.get(0);
    assertEquals("0123456789abcdef0123456789abcdef01234567", first.infohash());
    assertEquals("Ubuntu 24.04", first.name());
    assertEquals(5368709120L, first.sizeBytes());
    assertEquals(3, first.files());
    assertEquals("AAECAwQ", first.publisherPeerId());
    assertEquals("Debian", entries.get(1).name());
  }

  @Test
  void catalogSkipsRowsWithoutInfohashAndAcceptsStringNumbers() {
    String json =
        "{\"ok\":true,\"data\":["
            + "{\"name\":\"no hash\",\"s\":1,\"fc\":1,\"pub\":\"x\"},"
            + "{\"ih\":\"abc\",\"name\":\"strings\",\"s\":\"2048\",\"fc\":\"5\",\"pub\":\"p\"}]}";

    List<RelayControlClient.CatalogEntry> entries = RelayControlClient.parseCatalog(json);

    assertEquals(1, entries.size());
    assertEquals("abc", entries.get(0).infohash());
    assertEquals(2048L, entries.get(0).sizeBytes());
    assertEquals(5, entries.get(0).files());
  }

  @Test
  void malformedOrUnsuccessfulPayloadsYieldEmptyLists() {
    assertTrue(RelayControlClient.parseLookupPubs(null).isEmpty());
    assertTrue(RelayControlClient.parseLookupPubs("").isEmpty());
    assertTrue(RelayControlClient.parseLookupPubs("{not json").isEmpty());
    assertTrue(RelayControlClient.parseLookupPubs("{\"ok\":false,\"error\":\"nope\"}").isEmpty());
    assertTrue(RelayControlClient.parseLookupPubs("[1,2,3]").isEmpty());

    assertTrue(RelayControlClient.parseCatalog(null).isEmpty());
    assertTrue(RelayControlClient.parseCatalog("{not json").isEmpty());
    assertTrue(RelayControlClient.parseCatalog("{\"ok\":true}").isEmpty());
    assertTrue(RelayControlClient.parseCatalog("{\"ok\":true,\"data\":\"oops\"}").isEmpty());
  }
}
