/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.components.transfers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.tables.SizeHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The peer flag letters and source names, and the tracker rows of the transfer details. */
class TransferDetailDataLinesTest {

  // libtorrent peer_info flag bits
  private static final int INTERESTING = 1;
  private static final int CHOKED = 1 << 1;
  private static final int REMOTE_INTERESTED = 1 << 2;
  private static final int REMOTE_CHOKED = 1 << 3;
  private static final int LOCAL_CONNECTION = 1 << 5;
  private static final int HANDSHAKE = 1 << 6;
  private static final int OPTIMISTIC_UNCHOKE = 1 << 11;
  private static final int SNUBBED = 1 << 12;
  private static final int UTP_SOCKET = 1 << 17;
  private static final int SSL_SOCKET = 1 << 18;
  private static final int RC4_ENCRYPTED = 1 << 19;
  private static final int PLAINTEXT_ENCRYPTED = 1 << 20;
  // peer source bits
  private static final int TRACKER = 1;
  private static final int DHT = 1 << 1;
  private static final int PEX = 1 << 2;
  private static final int LSD = 1 << 3;
  private static final int RESUME_DATA = 1 << 4;
  private static final int INCOMING = 1 << 5;

  @BeforeAll
  static void headless() {
    System.setProperty("java.awt.headless", "true");
  }

  private static String flags(int flags, int source) {
    return TransferDetailPeersDataLine.getFlagsAsString(flags, source);
  }

  @Test
  void aPeerWeDownloadFromShowsCapitalD() {
    // interested, not choked by them, a connection we opened
    String shown = flags(INTERESTING | LOCAL_CONNECTION | CHOKED, 0);
    assertEquals("D", shown);
  }

  @Test
  void aPeerThatChokesUsShowsLowerCaseD() {
    assertEquals("d", flags(INTERESTING | REMOTE_CHOKED | LOCAL_CONNECTION | CHOKED, 0));
  }

  @Test
  void uploadingAndWantingToUploadAreCapitalAndLowerCaseU() {
    int base = REMOTE_INTERESTED | LOCAL_CONNECTION | INTERESTING | REMOTE_CHOKED;
    assertEquals("dU", flags(base, 0));
    assertEquals("du", flags(base | CHOKED, 0));
  }

  @Test
  void optimisticUnchokeAndSnubbedAreMarked() {
    String shown =
        flags(OPTIMISTIC_UNCHOKE | SNUBBED | LOCAL_CONNECTION | CHOKED | REMOTE_CHOKED, 0);
    assertEquals("OS", shown);
  }

  @Test
  void anIncomingConnectionIsMarkedWithI() {
    assertTrue(flags(CHOKED | REMOTE_CHOKED, 0).contains("I"));
    assertFalse(flags(CHOKED | REMOTE_CHOKED | LOCAL_CONNECTION, 0).contains("I"));
  }

  @Test
  void aPeerUnchokingUsWhenWeAreNotInterestedIsK() {
    assertEquals("K", flags(LOCAL_CONNECTION | CHOKED, 0));
  }

  @Test
  void aPeerWeUnchokedThatIsNotInterestedIsAQuestionMark() {
    assertEquals("?", flags(LOCAL_CONNECTION | REMOTE_CHOKED, 0));
  }

  @Test
  void sourceFlagsAddXHAndL() {
    int quiet = LOCAL_CONNECTION | CHOKED | REMOTE_CHOKED;
    assertEquals("X", flags(quiet, PEX));
    assertEquals("H", flags(quiet, DHT));
    assertEquals("L", flags(quiet, LSD));
    assertEquals("XHL", flags(quiet, PEX | DHT | LSD));
    assertEquals("", flags(quiet, TRACKER | RESUME_DATA | INCOMING));
  }

  @Test
  void encryptionIsEAndEAfterTheHandshakeIsLowerCaseE() {
    int quiet = LOCAL_CONNECTION | CHOKED | REMOTE_CHOKED;
    assertEquals("E", flags(quiet | RC4_ENCRYPTED, 0));
    assertEquals("E", flags(quiet | PLAINTEXT_ENCRYPTED, 0));
    assertEquals("E", flags(quiet | SSL_SOCKET, 0));
    assertEquals("Ee", flags(quiet | RC4_ENCRYPTED | HANDSHAKE, 0));
    assertEquals("", flags(quiet | HANDSHAKE, 0));
  }

  @Test
  void aUtpPeerIsMarkedWithP() {
    assertEquals("P", flags(LOCAL_CONNECTION | CHOKED | REMOTE_CHOKED | UTP_SOCKET, 0));
  }

  @Test
  void sourcesAreNamedInOrder() {
    assertEquals("", TransferDetailPeersDataLine.getSourceAsString((byte) 0));
    assertEquals("Tracker ", TransferDetailPeersDataLine.getSourceAsString((byte) TRACKER));
    assertEquals(
        "Tracker DHT PEX LSD ",
        TransferDetailPeersDataLine.getSourceAsString((byte) (TRACKER | DHT | PEX | LSD)));
    String resumedIncoming =
        TransferDetailPeersDataLine.getSourceAsString((byte) (RESUME_DATA | INCOMING));
    assertEquals("Resumed Incoming ", resumedIncoming);
  }

  @Test
  void aTrackerRowShowsItsFiveColumns() {
    TransferDetailTrackersDataLine line = new TransferDetailTrackersDataLine();
    line.initialize(
        new TransferDetailTrackers.TrackerItemHolder(
            0, true, 12, 34, 2048L, "udp://tracker.example:6969/announce"));
    assertEquals(5, line.getColumnCount());
    assertEquals("udp://tracker.example:6969/announce", line.getValueAt(0));
    assertEquals("Active", line.getValueAt(1));
    assertEquals(12, line.getValueAt(2));
    assertEquals(34, line.getValueAt(3));
    assertEquals(new SizeHolder(2048L), line.getValueAt(4));
    assertNull(line.getValueAt(5));
    assertEquals("URL", line.getColumn(0).getId());
    assertEquals(0, line.getTypeAheadColumn());
    assertFalse(line.isDynamic(0));
    assertFalse(line.isClippable(0));
  }

  @Test
  void anInactiveTrackerAndAnEmptyRowAreHandled() {
    TransferDetailTrackersDataLine line = new TransferDetailTrackersDataLine();
    assertNull(line.getValueAt(0));
    line.initialize(new TransferDetailTrackers.TrackerItemHolder(1, false, 0, 0, 0L, "http://t"));
    assertEquals("Inactive", line.getValueAt(1));
  }

  @Test
  void aPeerLineWithoutAPeerShowsNothingAndHasNineColumns() {
    TransferDetailPeersDataLine line = new TransferDetailPeersDataLine();
    assertEquals(9, line.getColumnCount());
    assertNull(line.getValueAt(0));
    assertEquals("IP", line.getColumn(0).getId());
    assertEquals(0, line.getTypeAheadColumn());
    assertFalse(line.isDynamic(3));
    assertFalse(line.isClippable(3));
  }
}
