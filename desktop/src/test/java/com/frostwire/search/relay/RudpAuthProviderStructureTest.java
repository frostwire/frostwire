/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/**
 * rUDP session crypto (HELLO signing/verification) must use {@code
 * IdentityKeys.softwareSignature/softwareKeyFactory} (BouncyCastle provider instance), never the
 * default JCA provider. Android Conscrypt rejects BouncyCastle Ed25519 keys with {@code
 * InvalidKeyException: No installed provider supports this key: BCEdDSAPrivateKey} — every rUDP
 * HELLO failed to sign on Android (2026-07-20: zero packets ever left the client). Desktop JVM's
 * SunEC tolerates foreign EdDSA keys, so only this structural guard prevents regressions.
 */
class RudpAuthProviderStructureTest {

  private static final String RUDP_AUTH =
      "common/src/main/java/com/frostwire/search/relay/icebridge/udp/RudpAuth.java";

  @Test
  void rudpAuthUsesSoftwareSignatureProvider() throws Exception {
    File f = new File("../" + RUDP_AUTH);
    if (!f.isFile()) {
      f = new File(RUDP_AUTH);
    }
    assertTrue(f.isFile(), "RudpAuth.java not found from " + new File(".").getAbsolutePath());
    String src = Files.readString(f.toPath());
    assertFalse(
        src.contains("Signature.getInstance("),
        "RudpAuth must not use the default JCA provider — Android Conscrypt "
            + "rejects BC Ed25519 keys (use IdentityKeys.softwareSignature)");
    assertTrue(
        src.contains("softwareSignature(\"Ed25519\")"),
        "RudpAuth must sign and verify via IdentityKeys.softwareSignature");
  }
}
