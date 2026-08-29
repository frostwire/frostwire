/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * CGNAT rebinds the phone's UDP tuple while PeerRegistry still holds the
 * first-seen mapping. Inbound SEARCH is keyed by connectionId so it arrives;
 * RELAY replies must follow the live session by pub, not registry host:port.
 */
class RudpRelayCgnatDeliveryStructureTest {

  @Test
  void relayDeliveryPrefersLiveSessionByPub() throws Exception {
    String source =
        read(
            "../common/src/main/java/com/frostwire/search/relay/icebridge/udp/RudpSessionManager.java");
    String compact = source.replaceAll("\\s+", "");

    assertTrue(
        compact.contains("RudpSessiontargetSession=findSessionByPub(target.ed25519Pub());"),
        "RELAY delivery must locate the live session by pub, not registry host:port");
    assertTrue(
        compact.contains("rebindSessionAddress(senderSession,sender);"),
        "handleRelay must migrate the sender endpoint on CGNAT rebind");
    assertTrue(
        compact.contains("privateRudpSessionfindSessionByPub(byte[]pub)"),
        "findSessionByPub must exist");
  }

  private static String read(String relativePath) throws IOException {
    Path cwd = Path.of(System.getProperty("user.dir"));
    Path file = cwd.resolve(relativePath);
    if (!Files.isRegularFile(file)) {
      file = cwd.resolve(relativePath.substring("../".length()));
    }
    return Files.readString(file, StandardCharsets.UTF_8);
  }
}
