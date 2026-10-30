/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/**
 * Endorse Source Peer reads the karma chain writer when the confirmation dialog opens; the relay
 * stack can stop (or the identity be replaced) before the user answers, leaving no writer. The
 * endorsement step must then report failure instead of throwing on its worker thread
 * (frostwire/frostwire#1413).
 */
class BTDownloadMediatorEndorseTest {

  @Test
  void endorsingWithoutAWriterFailsQuietly() {
    byte[] source = new byte[32];
    source[0] = 1;
    byte[] infoHash = new byte[20];

    assertFalse(BTDownloadMediator.endorseSourcePeer(null, source, infoHash));
  }
}
