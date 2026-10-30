/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1407: with the default locale set to Turkish, "client".toUpperCase() is
 * "CLİENT" and "ISO".toLowerCase() is "ıso". IceBridge roles and keyspace targets are protocol
 * values every peer must compute the same way, whatever its system language.
 */
class LocaleIndependentRoutingTest {

  private Locale saved;

  @BeforeEach
  void turkish() {
    saved = Locale.getDefault();
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));
  }

  @AfterEach
  void restore() {
    Locale.setDefault(saved);
  }

  @Test
  void rolesParseTheSameInTurkish() {
    assertEquals(NodeCapabilities.DEFAULT_PEER, NodeCapabilities.fromRole("client"));
    assertEquals(NodeCapabilities.DEFAULT_PEER, NodeCapabilities.fromRole("Client"));
    assertEquals(NodeCapabilities.DEFAULT_FORWARDER, NodeCapabilities.fromRole("forwarder"));
    assertEquals(NodeCapabilities.DEFAULT_BOTH, NodeCapabilities.fromRole("both"));
  }

  @Test
  void keywordsWithACapitalIHashToTheSameKeyspaceTargetInTurkish() throws Exception {
    MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
    for (String keywords : new String[] {"ISO", "Linux ISO", "  INDIE music  ", "iso"}) {
      byte[] expected =
          sha1.digest(keywords.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
      assertArrayEquals(expected, KeyspaceRouter.keyspaceTarget(keywords), keywords);
    }
  }
}
