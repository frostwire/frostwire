/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.adapters;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

public class TransferListAdapterRefreshStructureTest {

  @Test
  public void inPlaceUpdatesDoNotNotifyDataSetChanged() throws Exception {
    String adapter =
        read("src/main/java/com/frostwire/android/gui/adapters/TransferListAdapter.java")
            .replaceAll("\\s+", "");
    String fragment =
        read("src/main/java/com/frostwire/android/gui/fragments/TransfersFragment.java")
            .replaceAll("\\s+", "");
    String uiBt =
        read("src/main/java/com/frostwire/android/gui/transfers/UIBittorrentDownload.java")
            .replaceAll("\\s+", "");

    assertFalse(adapter.contains("submitList(newList,this::notifyDataSetChanged)"));
    assertTrue(adapter.contains("if(sameIdsInOrder(oldList,newList))"));
    assertTrue(adapter.contains("notifyItemChanged(i)"));
    assertTrue(
        fragment.contains("((UIBittorrentDownload)t).updateCachedState();")
            && fragment.indexOf("((UIBittorrentDownload)t).updateCachedState();")
                < fragment.indexOf("filter(allTransfers,selectedStatus)"));
    assertTrue(uiBt.contains("notifyStateChanged(oldState,cachedState)"));
  }

  private static String read(String relativePath) throws Exception {
    File file = new File(relativePath);
    if (!file.isFile()) {
      file = new File("android", relativePath);
    }
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
  }
}
