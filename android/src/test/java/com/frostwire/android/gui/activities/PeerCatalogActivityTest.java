/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.activities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.app.Application;
import android.content.Intent;
import android.widget.ListView;
import com.frostwire.android.R;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.InvalidTransfer;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.Debug;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.relay.RemoteIndexFetcher.RemoteTorrentEntry;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.TransferState;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class PeerCatalogActivityTest {

  @Test
  public void catalogLoadTaskIsAcceptedByTheEngineThreadPoolContextCheck() {
    // "Browse Shared Torrents" crashed: EngineThreadPool rejects tasks that pin a Context, and
    // the fetch lambda captured the activity.
    assertTrue("debug builds run the context-leak check", Debug.isEnabled());
    PeerCatalogActivity activity = mock(PeerCatalogActivity.class);
    Runnable capturing = () -> activity.getTitle();
    assertTrue("sanity: a task holding the activity is flagged", Debug.hasContext(capturing));

    assertFalse(Debug.hasContext(new PeerCatalogActivity.CatalogLoadTask(activity, new byte[32])));
  }

  @Test
  public void rowClickUsesNormalPipelineWithCanonicalHashAndHolderAttribution() throws Exception {
    BittorrentDownload transfer = mock(BittorrentDownload.class);
    exerciseRowClick(transfer, false);
  }

  @Test
  public void vpnRejectionShowsReasonWithoutAnnouncingSuccess() throws Exception {
    BittorrentDownload transfer =
        mock(BittorrentDownload.class, withSettings().extraInterfaces(InvalidTransfer.class));
    when(((InvalidTransfer) transfer).getReasonResId())
        .thenReturn(R.string.cannot_start_engine_without_vpn);
    exerciseRowClick(transfer, false);
  }

  @Test
  public void duplicateRowClickOpensTransfersWithoutAnnouncingSuccess() throws Exception {
    exerciseRowClick(null, false);
  }

  @Test
  public void startExceptionShowsFailureWithoutAnnouncingSuccess() throws Exception {
    exerciseRowClick(null, true);
  }

  @Test
  public void rejectedFetcherQueueShowsFailureAndErrorTransferWithoutAnnouncingSuccess()
      throws Exception {
    BittorrentDownload transfer = mock(BittorrentDownload.class);
    when(transfer.getState()).thenReturn(TransferState.ERROR);
    exerciseRowClick(transfer, false);
  }

  private void exerciseRowClick(BittorrentDownload transfer, boolean throwsOnStart)
      throws Exception {
    byte[] holder = new byte[32];
    holder[0] = 7;
    String pub = Base64.getUrlEncoder().withoutPadding().encodeToString(holder);
    String hash = "d6ce91299d15e8dac37591868de2d1fefabd3e16";
    Engine engine = mock(Engine.class);
    when(engine.getThreadPool()).thenReturn(mock(ExecutorService.class));
    TransferManager manager = mock(TransferManager.class);
    if (throwsOnStart) {
      when(manager.downloadTorrent(anyString(), isNull(), eq("ElGeneral"), aryEq(holder)))
          .thenThrow(new IllegalStateException("cannot enqueue"));
    } else {
      when(manager.downloadTorrent(anyString(), isNull(), eq("ElGeneral"), aryEq(holder)))
          .thenReturn(transfer);
    }
    try (MockedStatic<Engine> engines = mockStatic(Engine.class);
        MockedStatic<TransferManager> managers = mockStatic(TransferManager.class);
        MockedStatic<SystemUtils> system = mockStatic(SystemUtils.class);
        MockedStatic<UIUtils> ui = mockStatic(UIUtils.class)) {
      engines.when(Engine::instance).thenReturn(engine);
      managers.when(TransferManager::instance).thenReturn(manager);
      system
          .when(
              () -> SystemUtils.postToHandler(eq(SystemUtils.HandlerThreadName.DOWNLOADER), any()))
          .thenAnswer(
              invocation -> {
                ((Runnable) invocation.getArgument(1)).run();
                return null;
              });
      Intent intent = new Intent().putExtra(PeerCatalogActivity.EXTRA_PEER_PUB, pub);
      PeerCatalogActivity activity =
          Robolectric.buildActivity(PeerCatalogActivity.class, intent).create().get();
      java.lang.reflect.Method populate =
          PeerCatalogActivity.class.getDeclaredMethod("populate", java.util.List.class);
      populate.setAccessible(true);
      populate.invoke(
          activity, Collections.singletonList(new RemoteTorrentEntry(hash, "ElGeneral", 123L, 1)));
      ListView list = activity.findViewById(R.id.peer_catalog_list);
      list.performItemClick(null, 0, 0);
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      verify(manager)
          .downloadTorrent(
              argThat(
                  magnet ->
                      magnet.startsWith("magnet:?xt=urn:btih:" + hash + "&")
                          && magnet.contains("&x.hp=" + pub)),
              isNull(),
              eq("ElGeneral"),
              aryEq(holder));
      if (throwsOnStart) {
        ui.verify(() -> UIUtils.showLongMessage(activity, R.string.peer_catalog_download_failed));
        ui.verify(() -> UIUtils.showTransfersOnDownloadStart(activity), never());
      } else if (transfer instanceof InvalidTransfer) {
        ui.verify(
            () -> UIUtils.showLongMessage(activity, R.string.cannot_start_engine_without_vpn));
        ui.verify(() -> UIUtils.showTransfersOnDownloadStart(activity), never());
      } else {
        ui.verify(() -> UIUtils.showTransfersOnDownloadStart(activity));
        if (transfer != null && transfer.getState() == TransferState.ERROR) {
          ui.verify(() -> UIUtils.showLongMessage(activity, R.string.peer_catalog_download_failed));
        }
      }
      if (throwsOnStart
          || transfer == null
          || transfer instanceof InvalidTransfer
          || transfer.getState() == TransferState.ERROR) {
        ui.verify(
            () -> UIUtils.showShortMessage(activity, R.string.download_added_to_queue), never());
      } else {
        ui.verify(() -> UIUtils.showShortMessage(activity, R.string.download_added_to_queue));
      }
    }
  }
}
