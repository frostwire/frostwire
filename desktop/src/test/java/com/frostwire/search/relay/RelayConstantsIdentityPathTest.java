/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression: identity path must stay under {@code libtorrent/} so settings UI, Initializer, and
 * IceBridge child share one file.
 */
class RelayConstantsIdentityPathTest {

  @TempDir Path temp;

  @Test
  void identityFile_isLibtorrentIdentityDat() {
    File settings = temp.toFile();
    File identity = RelayConstants.identityFile(settings);
    assertEquals(
        new File(new File(settings, "libtorrent"), "identity.dat").getAbsolutePath(),
        identity.getAbsolutePath());
    assertTrue(identity.getParentFile().getName().equals(RelayConstants.RELAY_HOME_DIR));
    assertEquals(RelayConstants.IDENTITY_FILE, identity.getName());
  }

  @Test
  void relayHomeDir_rejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> RelayConstants.relayHomeDir(null));
  }
}
