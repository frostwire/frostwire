/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.mcp.desktop.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.transfers.TransferItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TransferFilesBoundTest {

  @Test
  void fileSerializationStopsAtTheRemainingBudget() {
    List<TransferItem> items = List.of(item("a"), item("b"), item("c"));

    JsonObject entry = TransferAdapter.transferFilesEntry("hash", "name", items, 2);

    assertEquals(3, entry.get("fileCount").getAsInt());
    assertTrue(entry.get("truncated").getAsBoolean());
    assertEquals(2, entry.getAsJsonArray("files").size());
    assertEquals(
        "a", entry.getAsJsonArray("files").get(0).getAsJsonObject().get("name").getAsString());
  }

  @Test
  void laterTransfersAreNotLoadedAfterTheFileBudgetIsSpent() {
    AtomicInteger loads = new AtomicInteger();
    List<TransferItem> first = List.of(item("a"), item("b"));

    JsonArray result =
        TransferAdapter.boundTransferFiles(
            2,
            2,
            i -> new TransferAdapter.NamedTransfer("id-" + i, "name-" + i),
            i -> {
              loads.incrementAndGet();
              return first;
            });

    assertEquals(1, loads.get());
    assertEquals(2, result.size());
    assertFalse(result.get(0).getAsJsonObject().get("truncated").getAsBoolean());
    assertTrue(result.get(1).getAsJsonObject().get("truncated").getAsBoolean());
    assertEquals(0, result.get(1).getAsJsonObject().getAsJsonArray("files").size());
  }

  private static TransferItem item(String name) {
    return new TransferItem() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public String getDisplayName() {
        return name;
      }

      @Override
      public File getFile() {
        return null;
      }

      @Override
      public long getSize() {
        return 1;
      }

      @Override
      public boolean isSkipped() {
        return false;
      }

      @Override
      public long getDownloaded() {
        return 0;
      }

      @Override
      public int getProgress() {
        return 0;
      }

      @Override
      public boolean isComplete() {
        return false;
      }

      @Override
      public int getPriority() {
        return 0;
      }
    };
  }
}
