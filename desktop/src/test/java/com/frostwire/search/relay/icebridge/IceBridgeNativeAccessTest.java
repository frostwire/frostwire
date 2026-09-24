/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.IdentityKeys;
import org.junit.jupiter.api.Test;

class IceBridgeNativeAccessTest {

  @Test
  void jniBindingHasExplicitPermissionOnRestrictedJdks() throws Exception {
    if (Runtime.version().feature() < 24) {
      return;
    }
    Module binding = com.frostwire.jlibtorrent.swig.libtorrent_jni.class.getModule();
    assertTrue((boolean) Module.class.getMethod("isNativeAccessEnabled").invoke(binding));

    // Proves the permission is actually used for native Ed25519, not just set on an idle worker.
    assertEquals(32, IdentityKeys.generate(0).ed25519PubRaw().length);
  }
}
