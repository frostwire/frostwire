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
    assertTrue(
        adapter.contains("if(sameIdsInOrder(oldList,newList)&&sameInstances(oldList,newList))"));
    assertTrue(adapter.contains("notifyItemChanged(i)"));
    assertTrue(
        fragment.contains("((UIBittorrentDownload)t).updateCachedState();")
            && fragment.indexOf("((UIBittorrentDownload)t).updateCachedState();")
                < fragment.indexOf("filter(allTransfers,selectedStatus)"));
    assertTrue(uiBt.contains("notifyStateChanged(oldState,cachedState)"));
  }

  @Test
  public void returningToTransfersForcesAnImmediateStateRefresh() throws Exception {
    String fragment =
        read("src/main/java/com/frostwire/android/gui/fragments/TransfersFragment.java")
            .replaceAll("(?m)//[^\\n]*", "")
            .replaceAll("\\s+", "");

    assertTrue(fragment.contains("initTimerServiceSubscription();onTime(true);"));
  }

  @Test
  public void libtorrentStateChangeRefreshesWrapperAndNotifiesListListeners() throws Exception {
    String btDownload =
        read("../common/src/main/java/com/frostwire/bittorrent/BTDownload.java")
            .replaceAll("\\s+", "");
    String listener =
        read("../common/src/main/java/com/frostwire/bittorrent/BTDownloadListener.java")
            .replaceAll("\\s+", "");
    String uiListener =
        read("src/main/java/com/frostwire/android/gui/transfers/UIBTDownloadListener.java")
            .replaceAll("\\s+", "");

    assertTrue(btDownload.contains("AlertType.STATE_CHANGED.swig()"));
    assertTrue(btDownload.contains("caseSTATE_CHANGED:"));
    assertTrue(btDownload.contains("listener.stateChanged(BTDownload.this)"));
    assertTrue(listener.contains("defaultvoidstateChanged(BTDownloaddl)"));
    assertTrue(uiListener.contains("voidstateChanged(BTDownloaddl)"));
    assertTrue(
        uiListener.contains("postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER,")
            && uiListener.contains("dl.refreshStatusCache();")
            && uiListener.contains("((UIBittorrentDownload)transfer).updateCachedState();"));
  }

  private static String read(String relativePath) throws Exception {
    File file = new File(relativePath);
    if (!file.isFile()) {
      file = new File("android", relativePath);
    }
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
  }
}
