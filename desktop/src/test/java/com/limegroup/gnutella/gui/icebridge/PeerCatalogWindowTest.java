/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.icebridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.LibTorrentMagnetDownloader;
import com.frostwire.search.relay.RemoteIndexFetcher;
import com.frostwire.util.Hex;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for the peer catalog window helpers: magnet synthesis (must carry the holder pub
 * so the download path fetches metadata over the mesh) and peer pub parsing for the picker.
 */
class PeerCatalogWindowTest {

  @Test
  void meshMagnetCarriesInfoHashNameAndHolderPub() {
    byte[] peerPub = new byte[32];
    peerPub[0] = 7;
    peerPub[31] = 42;
    RemoteIndexFetcher.RemoteTorrentEntry entry =
        new RemoteIndexFetcher.RemoteTorrentEntry(
            "d8e8fca2dc0f896fd7cb4cb0031ba24900000000", "some name.bin", 123456L, 3);

    String magnet = PeerCatalogWindow.meshMagnet(entry, peerPub);

    assertTrue(magnet.startsWith("magnet:?xt=urn:btih:d8e8fca2dc0f896fd7cb4cb0031ba24900000000"));
    // A browsed catalog row is not itself a public-catalog holder flag.
    assertFalse(LibTorrentMagnetDownloader.hasPublicCatalogFlag(magnet));
    // x.hp must round-trip: it is the mesh return address for TORRENT_FETCH metadata.
    assertTrue(Arrays.equals(peerPub, LibTorrentMagnetDownloader.parseHolderPub(magnet)));
    assertTrue(
        magnet.contains("x.hp=" + Base64.getUrlEncoder().withoutPadding().encodeToString(peerPub)));
    assertTrue(magnet.contains("dn=some%20name.bin") || magnet.contains("dn=some+name.bin"));
  }

  @Test
  void parsePubAcceptsHexAndBothBase64Alphabets() {
    byte[] peerPub = new byte[32];
    peerPub[0] = 1;
    peerPub[16] = 2;
    String hex = Hex.encode(peerPub);

    assertTrue(Arrays.equals(peerPub, PeerCatalogWindow.parsePub(hex)));
    assertTrue(Arrays.equals(peerPub, PeerCatalogWindow.parsePub("  " + hex + "  ")));
    assertTrue(
        Arrays.equals(
            peerPub,
            PeerCatalogWindow.parsePub(
                Base64.getUrlEncoder().withoutPadding().encodeToString(peerPub))));
    assertTrue(
        Arrays.equals(
            peerPub, PeerCatalogWindow.parsePub(Base64.getEncoder().encodeToString(peerPub))));
  }

  @Test
  void parsePubRejectsJunkAndWrongLength() {
    assertNull(PeerCatalogWindow.parsePub(null));
    assertNull(PeerCatalogWindow.parsePub(""));
    assertNull(PeerCatalogWindow.parsePub("not-a-key"));
    assertNull(PeerCatalogWindow.parsePub("abcd1234"));
    // Valid encodings of the wrong size (16 bytes) are not peer keys.
    byte[] sixteen = new byte[16];
    assertNull(PeerCatalogWindow.parsePub(Hex.encode(sixteen)));
    assertNull(PeerCatalogWindow.parsePub(Base64.getEncoder().encodeToString(sixteen)));
  }
}
