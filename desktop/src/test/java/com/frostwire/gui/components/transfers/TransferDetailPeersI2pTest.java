/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.components.transfers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.jlibtorrent.PeerInfo;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1304: the Peers table marks peers that are on the I2P network, from the
 * {@code i2p_socket} peer flag (bit 16). The destination is not bound in jlibtorrent yet, so no
 * address is shown for them.
 */
class TransferDetailPeersI2pTest {

  private static final int I2P_SOCKET = 1 << 16;
  private static final int UTP_SOCKET = 1 << 17;

  @Test
  void anI2pPeerGetsTheMarkerAndOthersDoNot() {
    assertTrue(TransferDetailPeersDataLine.getFlagsAsString(I2P_SOCKET, 0).contains("i"));
    assertFalse(TransferDetailPeersDataLine.getFlagsAsString(UTP_SOCKET, 0).contains("i"));
    assertFalse(TransferDetailPeersDataLine.getFlagsAsString(0, 0).contains("i"));
  }

  @Test
  void anI2pPeerHasNoIpAddressToShow() {
    assertEquals(
        "i2p://destination-not-available",
        TransferDetailPeersDataLine.addressOf(
            PeerInfo.ConnectionType.STANDARD_BITTORRENT, I2P_SOCKET, "0.0.0.0:0"));
  }

  @Test
  void otherPeersKeepTheirAddresses() {
    assertEquals(
        "bt://1.2.3.4:6881",
        TransferDetailPeersDataLine.addressOf(
            PeerInfo.ConnectionType.STANDARD_BITTORRENT, 0, "1.2.3.4:6881"));
    assertEquals(
        "uTP://1.2.3.4:6881",
        TransferDetailPeersDataLine.addressOf(
            PeerInfo.ConnectionType.STANDARD_BITTORRENT, UTP_SOCKET, "1.2.3.4:6881"));
  }
}
