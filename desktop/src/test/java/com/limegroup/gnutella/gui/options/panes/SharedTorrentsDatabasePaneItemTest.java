/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.LocalSharedTorrent;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.swing.JCheckBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Options &gt; Shared Torrents Database: how sizes, times, keys and rows are shown. */
class SharedTorrentsDatabasePaneItemTest {
  /**
   * What the pane shows for a value it does not have: one placeholder everywhere. The tests do not
   * spell it out, the build compiles the main sources as ISO-8859-1 so the em dash is not a dash.
   */
  private static final String MISSING = SharedTorrentsDatabasePaneItem.formatSize(-1);

  private Locale locale;
  private boolean includeInactive;

  @BeforeEach
  void pinTheLocale() {
    locale = Locale.getDefault();
    Locale.setDefault(Locale.US);
    includeInactive = SearchEnginesSettings.LOCAL_SEARCH_INCLUDE_INACTIVE.getValue();
  }

  @AfterEach
  void restore() {
    Locale.setDefault(locale);
    SearchEnginesSettings.LOCAL_SEARCH_INCLUDE_INACTIVE.setValue(includeInactive);
  }

  private static LocalSharedTorrent torrent(String name, long size, int files, String matched) {
    byte[] hash = new byte[20];
    for (int i = 0; i < hash.length; i++) {
      hash[i] = (byte) (0xA0 + i);
    }
    byte[] pub = new byte[32];
    for (int i = 0; i < pub.length; i++) {
      pub[i] = (byte) i;
    }
    return new LocalSharedTorrent.Builder()
        .infoHash(hash)
        .name(name)
        .sizeBytes(size)
        .fileCount(files)
        .publisherNodeId(new byte[20])
        .publisherEd25519Pub(pub)
        .addedAt(1_700_000_000L)
        .lastSeenAt(1_700_000_500L)
        .matchedFile(matched)
        .build();
  }

  @Test
  void theMissingValuePlaceholderIsNotANumberOrBlank() {
    assertFalse(MISSING.isBlank());
    assertFalse(Character.isDigit(MISSING.charAt(0)));
  }

  @Test
  void sizesAreShownInTheLargestUnitUnderOneThousandTwentyFour() {
    assertEquals(MISSING, SharedTorrentsDatabasePaneItem.formatSize(-1));
    assertEquals("0 B", SharedTorrentsDatabasePaneItem.formatSize(0));
    assertEquals("1023 B", SharedTorrentsDatabasePaneItem.formatSize(1023));
    assertEquals("1.0 KB", SharedTorrentsDatabasePaneItem.formatSize(1024));
    assertEquals("1.5 KB", SharedTorrentsDatabasePaneItem.formatSize(1536));
    assertEquals("1.0 MB", SharedTorrentsDatabasePaneItem.formatSize(1024L * 1024));
    assertEquals(
        "2.5 GB", SharedTorrentsDatabasePaneItem.formatSize((long) (2.5 * 1024 * 1024 * 1024)));
    assertEquals(
        "2048.0 GB", SharedTorrentsDatabasePaneItem.formatSize(2L * 1024 * 1024 * 1024 * 1024));
  }

  @Test
  void timestampsAreShownAsDateAndMinuteAndAMissingOneAsADash() {
    assertEquals(MISSING, SharedTorrentsDatabasePaneItem.formatTimestamp(0));
    assertEquals(MISSING, SharedTorrentsDatabasePaneItem.formatTimestamp(-5));
    assertEquals(
        new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(1_700_000_000L * 1000L)),
        SharedTorrentsDatabasePaneItem.formatTimestamp(1_700_000_000L));
  }

  @Test
  void hashesAreCutToTheRequestedLength() {
    assertEquals(MISSING, SharedTorrentsDatabasePaneItem.shortHex((String) null, 8));
    assertEquals("abcd", SharedTorrentsDatabasePaneItem.shortHex("abcd", 8));
    assertEquals("abcdefgh", SharedTorrentsDatabasePaneItem.shortHex("abcdefgh", 8));
    assertEquals("abcdefgh", SharedTorrentsDatabasePaneItem.shortHex("abcdefghij", 8));
    assertEquals(MISSING, SharedTorrentsDatabasePaneItem.shortHex((byte[]) null, 8));
    assertEquals(MISSING, SharedTorrentsDatabasePaneItem.shortHex(new byte[0], 8));
    assertEquals("00ff10", SharedTorrentsDatabasePaneItem.shortHex(new byte[] {0, -1, 16}, 8));
    assertEquals("00ff", SharedTorrentsDatabasePaneItem.shortHex(new byte[] {0, -1, 16}, 4));
  }

  @Test
  void aMagnetLinkIsTheInfoHash() {
    LocalSharedTorrent t = torrent("Linux ISO", 100, 1, null);
    assertEquals(
        "magnet:?xt=urn:btih:" + t.infoHashHex(), SharedTorrentsDatabasePaneItem.magnetLink(t));
    assertEquals(40, t.infoHashHex().length());
  }

  @Test
  void theTableShowsOneRowPerTorrentWithReadableColumns() {
    SharedTorrentsDatabasePaneItem.TorrentTableModel model =
        new SharedTorrentsDatabasePaneItem.TorrentTableModel();
    assertEquals(8, model.getColumnCount());
    assertEquals("Name", model.getColumnName(0));
    assertEquals("Matched File", model.getColumnName(7));
    assertEquals(0, model.getRowCount());

    LocalSharedTorrent a = torrent("Linux ISO", 1536, 3, "docs/readme.txt");
    LocalSharedTorrent b = torrent("Other", 10, 1, null);
    model.set(List.of(a, b));
    assertEquals(2, model.getRowCount());
    assertEquals("Linux ISO", model.getValueAt(0, 0));
    assertEquals("1.5 KB", model.getValueAt(0, 1));
    assertEquals(3, model.getValueAt(0, 2));
    assertEquals(a.infoHashHex().substring(0, 16), model.getValueAt(0, 3));
    assertEquals("000102030405060708090a0b0c0d0e0f".substring(0, 16), model.getValueAt(0, 4));
    assertEquals(
        SharedTorrentsDatabasePaneItem.formatTimestamp(1_700_000_000L), model.getValueAt(0, 5));
    assertEquals(
        SharedTorrentsDatabasePaneItem.formatTimestamp(1_700_000_500L), model.getValueAt(0, 6));
    assertEquals("docs/readme.txt", model.getValueAt(0, 7));
    assertEquals("", model.getValueAt(1, 7), "a name-only match has no file");
    assertEquals("", model.getValueAt(0, 99));
    assertFalse(model.isCellEditable(0, 0));
  }

  @Test
  void theTableHandsBackTheTorrentOfARowAndNothingOutOfRange() {
    SharedTorrentsDatabasePaneItem.TorrentTableModel model =
        new SharedTorrentsDatabasePaneItem.TorrentTableModel();
    LocalSharedTorrent a = torrent("Linux ISO", 1, 1, null);
    model.set(List.of(a));
    assertEquals(a.infoHashHex(), model.torrentAt(0).infoHashHex());
    assertNull(model.torrentAt(1));
    assertNull(model.torrentAt(-1));
    model.set(List.of());
    assertEquals(0, model.getRowCount());
  }

  @Test
  void theInactiveSharesBoxFollowsItsSetting() throws Exception {
    SearchEnginesSettings.LOCAL_SEARCH_INCLUDE_INACTIVE.setValue(false);
    SharedTorrentsDatabasePaneItem pane = PaneSupport.onEdt(SharedTorrentsDatabasePaneItem::new);
    JCheckBox box = PaneSupport.only(pane.getContainer(), JCheckBox.class);
    assertFalse(pane.isDirty());
    box.setSelected(true);
    assertTrue(pane.isDirty());
    assertFalse(pane.applyOptions());
    assertTrue(SearchEnginesSettings.LOCAL_SEARCH_INCLUDE_INACTIVE.getValue());
    assertFalse(pane.isDirty());
  }
}
