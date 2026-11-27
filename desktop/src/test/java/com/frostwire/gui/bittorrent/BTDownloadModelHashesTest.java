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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.transfers.TransferState;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The Transfers table model: which hashes are being downloaded, rows and editable cells. */
class BTDownloadModelHashesTest {

  @BeforeAll
  static void headless() {
    System.setProperty("java.awt.headless", "true");
  }

  private static BTDownload download(String hash) {
    Map<String, Object> answers = new HashMap<>();
    answers.put("getHash", hash);
    answers.put("getDisplayName", hash + ".iso");
    answers.put("getState", TransferState.DOWNLOADING);
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
                  yield null;
                }
              };
            });
  }

  @Test
  void addingADownloadRemembersItsHash() {
    BTDownloadModel model = new BTDownloadModel();
    BTDownload a = download("aaa");
    BTDownload b = download("bbb");
    assertFalse(model.isDownloading("aaa"));
    assertEquals(0, model.add(a));
    assertEquals(0, model.add(b));
    assertTrue(model.isDownloading("aaa"));
    assertTrue(model.isDownloading("bbb"));
    assertEquals(2, model.getTotalDownloads());
    assertSame(b, model.get(0).getInitializeObject());
    assertSame(a, model.get(1).getInitializeObject());
  }

  @Test
  void addingAtARowKeepsThePlaceAndRemembersTheHash() {
    BTDownloadModel model = new BTDownloadModel();
    model.add(download("aaa"));
    model.add(download("bbb"), 1);
    assertEquals("bbb", model.get(1).getInitializeObject().getHash());
    assertTrue(model.isDownloading("bbb"));
  }

  @Test
  void hashesCanBeAddedAndForgottenWithoutRows() {
    BTDownloadModel model = new BTDownloadModel();
    model.addHash("pending");
    assertTrue(model.isDownloading("pending"));
    model.remove("pending");
    assertFalse(model.isDownloading("pending"));
    assertEquals(0, model.getTotalDownloads());
  }

  @Test
  void removingALineForgetsItsHashAndHandsBackTheDownload() {
    BTDownloadModel model = new BTDownloadModel();
    BTDownload a = download("aaa");
    model.add(a);
    assertSame(a, model.removeLine(0));
    assertFalse(model.isDownloading("aaa"));
    assertEquals(0, model.getRowCount());
  }

  @Test
  void onlyTheActionPaymentAndSeedingCellsAreEditable() {
    BTDownloadModel model = new BTDownloadModel();
    model.add(download("aaa"));
    java.util.Set<Object> editable = new java.util.HashSet<>();
    for (int col = 0; col < model.getColumnCount(); col++) {
      if (model.isCellEditable(0, col)) {
        editable.add(model.getColumnId(col));
      }
    }
    assertEquals(
        java.util.Set.of("TRANSFER_ACTIONS", "SEEDING_COLUMN", "PAYMENT_OPTIONS_COLUMN"), editable);
  }

  @Test
  void aNewLineIsABittorrentDataLine() {
    BTDownloadModel model = new BTDownloadModel();
    assertNotNull(model.createDataLine());
    assertEquals(18, model.getColumnCount());
  }
}
