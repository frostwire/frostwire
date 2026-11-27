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

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.transfers.TransferState;
import com.limegroup.gnutella.gui.tables.SizeHolder;
import com.limegroup.gnutella.gui.tables.TimeRemainingHolder;
import java.lang.reflect.Proxy;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** What the Transfers table shows in each column of a BitTorrent download row. */
class BTDownloadDataLineValuesTest {

  @BeforeAll
  static void headless() {
    System.setProperty("java.awt.headless", "true");
  }

  /** A download whose answers come from a map the test can change. */
  private static BTDownload download(Map<String, Object> answers) {
    return (BTDownload)
        Proxy.newProxyInstance(
            BTDownload.class.getClassLoader(),
            new Class<?>[] {BTDownload.class},
            (proxy, method, args) -> {
              if (answers.containsKey(method.getName())) {
                return answers.get(method.getName());
              }
              return switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> {
                  Class<?> type = method.getReturnType();
                  if (type == boolean.class) yield false;
                  if (type == int.class) yield 0;
                  if (type == long.class) yield 0L;
                  if (type == double.class) yield 0.0;
                  if (type == float.class) yield 0f;
                  yield null;
                }
              };
            });
  }

  private static Map<String, Object> running() {
    Map<String, Object> m = new HashMap<>();
    m.put("getDisplayName", "ubuntu.iso");
    m.put("getState", TransferState.DOWNLOADING);
    m.put("getProgress", 42);
    m.put("getDownloadSpeed", 2048.0);
    m.put("getUploadSpeed", 512.0);
    m.put("getETA", 90L);
    m.put("getSize", 4L * 1024 * 1024);
    m.put("getBytesReceived", 1024L * 1024);
    m.put("getBytesSent", 2048L);
    m.put("getSeedsString", "3 (7)");
    m.put("getPeersString", "5 (9)");
    m.put("getShareRatio", "0.5");
    m.put("getSeedToPeerRatio", "0.6");
    m.put("getDateCreated", new Date(1234L));
    return m;
  }

  private static BTDownloadDataLine initialized(Map<String, Object> answers) {
    BTDownloadDataLine line = new BTDownloadDataLine();
    line.initialize(download(answers));
    return line;
  }

  /** The value of the column with this id. */
  private static Object value(BTDownloadDataLine line, String columnId) {
    for (int i = 0; i < line.getColumnCount(); i++) {
      if (line.getColumn(i).getId().equals(columnId)) {
        return line.getValueAt(i);
      }
    }
    throw new AssertionError("no column " + columnId);
  }

  @Test
  void theTableHasTheDocumentedColumns() {
    BTDownloadDataLine line = new BTDownloadDataLine();
    assertEquals(18, line.getColumnCount());
    assertEquals("TRANSFER_ACTIONS", line.getColumn(0).getId());
    assertEquals("DOWNLOAD_NAME_COLUMN", line.getColumn(line.getTypeAheadColumn()).getId());
    assertNull(BTDownloadDataLine.staticGetColumn(999));
    assertFalse(line.isDynamic(0));
    assertFalse(line.isClippable(0));
  }

  @Test
  void aRunningDownloadShowsItsCachedValues() {
    BTDownloadDataLine line = initialized(running());
    assertEquals("Downloading", value(line, "DOWNLOAD_STATUS_COLUMN"));
    assertEquals(42, value(line, "DOWNLOAD_PROGRESS_COLUMN"));
    assertEquals(2048.0, value(line, "DOWNLOAD_SPEED_COLUMN"));
    assertEquals(512.0, value(line, "UPLOAD_SPEED_COLUMN"));
    assertEquals(new SizeHolder(4L * 1024 * 1024), value(line, "DOWNLOAD_SIZE_COLUMN"));
    assertEquals(new SizeHolder(1024L * 1024), value(line, "DOWNLOAD_BYTES_DOWNLOADED_COLUMN"));
    assertEquals(new SizeHolder(2048L), value(line, "DOWNLOAD_BYTES_UPLOADED_COLUMN"));
    assertEquals("5 (9)", value(line, "PEERS_STATUS_COLUMN"));
    assertEquals("0.5", value(line, "SHARE_RATIO_COLUMN"));
    assertEquals("0.6", value(line, "SEED_TO_PEER_RATIO_COLUMN"));
    assertEquals(new Date(1234L), value(line, "DATE_CREATED_COLUMN"));
    assertEquals("", value(line, "LICENSE_COLUMN"));
    assertEquals("3 (7)", value(line, "SEEDS_STATUS_COLUMN").toString());
    assertNotNull(value(line, "TRANSFER_ACTIONS"));
    assertNotNull(value(line, "SEEDING_COLUMN"));
    assertEquals(TransferState.DOWNLOADING, line.getCachedTransferState());
  }

  @Test
  void anUnknownTransferStateShowsAsUnknownStatus() {
    Map<String, Object> m = running();
    m.put("getState", null);
    assertEquals("Unknown status", value(initialized(m), "DOWNLOAD_STATUS_COLUMN"));
  }

  @Test
  void everyKnownStateHasAReadableStatusText() {
    for (TransferState state : TransferState.values()) {
      Map<String, Object> m = running();
      m.put("getState", state);
      Object status = value(initialized(m), "DOWNLOAD_STATUS_COLUMN");
      assertNotNull(status, state.name());
      assertFalse(status.toString().isEmpty(), state.name());
    }
  }

  @Test
  void partialDownloadsMarkTheirSize() {
    Map<String, Object> m = running();
    m.put("isPartialDownload", true);
    String shown = value(initialized(m), "DOWNLOAD_SIZE_COLUMN").toString();
    assertTrue(shown.length() > new SizeHolder(4L * 1024 * 1024).toString().length(), shown);
  }

  @Test
  void aStalledDownloadHasAnInfiniteTimeAndAFinishedOneHasNone() {
    Map<String, Object> m = running();
    m.put("getDownloadSpeed", 0.0);
    BTDownloadDataLine stalled = initialized(m);
    assertEquals(new TimeRemainingHolder(-1), value(stalled, "DOWNLOAD_TIME_REMAINING_COLUMN"));

    BTDownloadDataLine moving = initialized(running());
    assertEquals(new TimeRemainingHolder(90), value(moving, "DOWNLOAD_TIME_REMAINING_COLUMN"));

    Map<String, Object> done = running();
    BTDownloadDataLine line = initialized(done);
    done.put("isCompleted", true);
    assertEquals(new TimeRemainingHolder(0), value(line, "DOWNLOAD_TIME_REMAINING_COLUMN"));
  }

  @Test
  void seedingAndDownloadingAreToldApart() {
    Map<String, Object> m = running();
    BTDownloadDataLine line = initialized(m);
    assertTrue(line.isDownloading());
    assertFalse(line.isSeeding());
    assertFalse(line.isFinished());

    m.put("getState", TransferState.SEEDING);
    m.put("isCompleted", true);
    assertTrue(line.isSeeding());
    assertFalse(line.isDownloading());
    assertFalse(line.isFinished(), "a seeding download isn't finished");

    m.put("getState", TransferState.FINISHED);
    assertTrue(line.isFinished());

    m.put("isCompleted", false);
    m.put("getState", TransferState.PAUSED);
    assertTrue(line.isDownloading(), "paused before it finished still counts as a download");
    m.put("isCompleted", true);
    assertFalse(line.isDownloading());
  }

  @Test
  void aLineWithoutADownloadHasNoNameAndIsNotSeeding() {
    BTDownloadDataLine line = new BTDownloadDataLine();
    assertEquals("", line.getDisplayName());
    assertFalse(line.isSeeding());
    assertFalse(line.isFinished());
  }

  @Test
  void theDisplayNameComesFromTheDownload() {
    assertEquals("ubuntu.iso", initialized(running()).getDisplayName());
  }

  @Test
  void toolTipListsElevenFactsInOrder() {
    String[] tip = initialized(running()).getToolTipArray(0);
    assertEquals(11, tip.length);
    assertEquals("ubuntu.iso", tip[0]);
    assertTrue(tip[1].startsWith("Status"), tip[1]);
    assertTrue(tip[2].endsWith("42%"), tip[2]);
    assertTrue(tip[3].startsWith("Down Speed"), tip[3]);
    assertTrue(tip[4].startsWith("Up Speed"), tip[4]);
    assertTrue(tip[5].startsWith("Downloaded"), tip[5]);
    assertTrue(tip[6].startsWith("Uploaded"), tip[6]);
    assertTrue(tip[7].endsWith("5 (9)"), tip[7]);
    assertTrue(tip[8].endsWith("3 (7)"), tip[8]);
    assertTrue(tip[9].startsWith("Size"), tip[9]);
    assertTrue(tip[10].startsWith("ETA"), tip[10]);
  }

  @Test
  void aSecondUpdateInsideTheDebounceWindowKeepsTheCachedValues() {
    Map<String, Object> m = running();
    BTDownloadDataLine line = initialized(m);
    m.put("getProgress", 99);
    line.update();
    assertEquals(42, value(line, "DOWNLOAD_PROGRESS_COLUMN"));
  }

  @Test
  void aFailingDownloadDoesNotBreakUpdate() {
    Map<String, Object> m = running();
    BTDownloadDataLine line = new BTDownloadDataLine();
    BTDownload failing =
        (BTDownload)
            Proxy.newProxyInstance(
                BTDownload.class.getClassLoader(),
                new Class<?>[] {BTDownload.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("getState")) {
                    throw new IllegalStateException("torrent handle is gone");
                  }
                  Object v = m.get(method.getName());
                  if (v != null) return v;
                  Class<?> type = method.getReturnType();
                  if (type == boolean.class) return false;
                  if (type == int.class) return 0;
                  if (type == long.class) return 0L;
                  if (type == double.class) return 0.0;
                  return null;
                });
    line.initialize(failing);
    assertNull(line.getCachedTransferState());
    assertSame(line.getInitializeObject(), failing);
  }
}
