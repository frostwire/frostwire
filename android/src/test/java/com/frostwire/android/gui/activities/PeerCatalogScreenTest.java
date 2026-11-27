/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.activities;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.InvalidTransfer;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.search.AndroidRelayStack;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.relay.RemoteIndexFetcher.RemoteTorrentEntry;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.TransferState;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The "Browse Shared Torrents" screen: loading, listing and start-up outcomes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class PeerCatalogScreenTest {

  private static final String HASH = "d6ce91299d15e8dac37591868de2d1fefabd3e16";

  private MockedStatic<Engine> engines;
  private MockedStatic<TransferManager> managers;
  private MockedStatic<UIUtils> ui;
  private MockedStatic<SystemUtils> system;
  private MockedStatic<AndroidRelayStack> stacks;
  private TransferManager manager;
  private ExecutorService pool;
  private final byte[] holder = new byte[32];
  private String pub;

  @Before
  public void setUp() {
    holder[0] = 4;
    pub = Base64.getUrlEncoder().withoutPadding().encodeToString(holder);
    pool = mock(ExecutorService.class);
    Engine engine = mock(Engine.class);
    when(engine.getThreadPool()).thenReturn(pool);
    engines = mockStatic(Engine.class);
    engines.when(Engine::instance).thenReturn(engine);
    manager = mock(TransferManager.class);
    managers = mockStatic(TransferManager.class);
    managers.when(TransferManager::instance).thenReturn(manager);
    ui = mockStatic(UIUtils.class);
    ui.when(() -> UIUtils.getBytesInHuman(org.mockito.ArgumentMatchers.anyDouble()))
        .thenReturn("1 KB");
    system = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    system
        .when(
            () ->
                SystemUtils.postToHandler(
                    any(SystemUtils.HandlerThreadName.class), any(Runnable.class)))
        .thenAnswer(
            i -> {
              ((Runnable) i.getArgument(1)).run();
              return null;
            });
    stacks = mockStatic(AndroidRelayStack.class);
  }

  @After
  public void tearDown() {
    engines.close();
    managers.close();
    ui.close();
    system.close();
    stacks.close();
  }

  private PeerCatalogActivity open(String peerPub) {
    Intent intent = new Intent();
    if (peerPub != null) {
      intent.putExtra(PeerCatalogActivity.EXTRA_PEER_PUB, peerPub);
    }
    return Robolectric.buildActivity(PeerCatalogActivity.class, intent).create().get();
  }

  private static RemoteTorrentEntry entry(String hash, String name) {
    return new RemoteTorrentEntry(hash, name, 2048L, 3);
  }

  /** Runs the queued catalog load task, as the engine pool would. */
  private void runCatalogLoad(PeerCatalogActivity activity) {
    org.mockito.ArgumentCaptor<Runnable> task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
    verify(pool).execute(task.capture());
    task.getValue().run();
    shadowOf(Looper.getMainLooper()).idle();
  }

  @Test
  public void aMissingOrMalformedPeerKeyShowsTheEmptyState() {
    for (String bad : new String[] {null, "", "not base64 !!", "AAAA"}) {
      PeerCatalogActivity activity = open(bad);
      assertEquals(View.VISIBLE, activity.findViewById(R.id.peer_catalog_empty).getVisibility());
      assertEquals(View.GONE, activity.findViewById(R.id.peer_catalog_list).getVisibility());
      assertEquals(View.GONE, activity.findViewById(R.id.peer_catalog_progress).getVisibility());
    }
    verify(pool, never()).execute(any());
  }

  @Test
  public void aLoadedCatalogIsListedWithNameSizeAndFileCount() {
    AndroidRelayStack stack = mock(AndroidRelayStack.class);
    when(stack.browseCatalog(any(), anyInt()))
        .thenReturn(Arrays.asList(entry(HASH, "Ubuntu ISO"), entry(HASH, "Debian ISO")));
    stacks.when(AndroidRelayStack::live).thenReturn(stack);
    PeerCatalogActivity activity = open(pub);
    runCatalogLoad(activity);
    ListView list = activity.findViewById(R.id.peer_catalog_list);
    assertEquals(View.VISIBLE, list.getVisibility());
    assertEquals(2, list.getAdapter().getCount());
    assertEquals(0, list.getAdapter().getItemId(0));
    View row = list.getAdapter().getView(1, null, list);
    assertEquals(
        "Debian ISO",
        ((TextView) row.findViewById(R.id.peer_catalog_item_name)).getText().toString());
    String details =
        ((TextView) row.findViewById(R.id.peer_catalog_item_details)).getText().toString();
    assertEquals("1 KB · 3 " + activity.getString(R.string.files), details);
    // a recycled row is reused
    assertEquals(row, list.getAdapter().getView(0, row, list));
    assertEquals(View.GONE, activity.findViewById(R.id.peer_catalog_progress).getVisibility());
  }

  @Test
  public void anEmptyCatalogOrNoRelayStackShowsTheEmptyState() {
    stacks.when(AndroidRelayStack::live).thenReturn(null);
    PeerCatalogActivity activity = open(pub);
    runCatalogLoad(activity);
    assertEquals(View.VISIBLE, activity.findViewById(R.id.peer_catalog_empty).getVisibility());

    AndroidRelayStack stack = mock(AndroidRelayStack.class);
    when(stack.browseCatalog(any(), anyInt())).thenReturn(Collections.emptyList());
    stacks.when(AndroidRelayStack::live).thenReturn(stack);
    org.mockito.Mockito.clearInvocations(pool);
    PeerCatalogActivity second = open(pub);
    runCatalogLoad(second);
    assertEquals(View.VISIBLE, second.findViewById(R.id.peer_catalog_empty).getVisibility());
  }

  @Test
  public void aFinishedActivityIgnoresTheLateCatalog() {
    AndroidRelayStack stack = mock(AndroidRelayStack.class);
    when(stack.browseCatalog(any(), anyInt())).thenReturn(Arrays.asList(entry(HASH, "late")));
    stacks.when(AndroidRelayStack::live).thenReturn(stack);
    PeerCatalogActivity activity = open(pub);
    activity.finish();
    runCatalogLoad(activity);
    ListView list = activity.findViewById(R.id.peer_catalog_list);
    assertEquals(null, list.getAdapter());
  }

  private ListView listWith(PeerCatalogActivity activity, List<RemoteTorrentEntry> entries) {
    AndroidRelayStack stack = mock(AndroidRelayStack.class);
    when(stack.browseCatalog(any(), anyInt())).thenReturn(entries);
    stacks.when(AndroidRelayStack::live).thenReturn(stack);
    runCatalogLoad(activity);
    return activity.findViewById(R.id.peer_catalog_list);
  }

  @Test
  public void anEntryWithABadInfoHashIsRejectedWithAMessage() {
    PeerCatalogActivity activity = open(pub);
    ListView list = listWith(activity, Arrays.asList(entry("not-a-hash", "Broken")));
    list.performItemClick(null, 0, 0);
    ui.verify(
        () -> UIUtils.showLongMessage(activity, R.string.torrent_scheme_download_not_supported));
    verify(manager, never()).downloadTorrent(anyString(), any(), anyString(), any());
  }

  @Test
  public void aDownloadOnMobileDataWithSavingsOnIsQueuedPaused() {
    BittorrentDownload transfer = mock(BittorrentDownload.class);
    when(transfer.getState()).thenReturn(TransferState.DOWNLOADING);
    when(manager.downloadTorrent(anyString(), any(), anyString(), any())).thenReturn(transfer);
    when(manager.isBittorrentDownloadAndMobileDataSavingsOn(transfer)).thenReturn(true);
    PeerCatalogActivity activity = open(pub);
    listWith(activity, Arrays.asList(entry(HASH, "Ubuntu"))).performItemClick(null, 0, 0);
    shadowOf(Looper.getMainLooper()).idle();
    ui.verify(
        () -> UIUtils.showLongMessage(activity, R.string.torrent_transfer_enqueued_on_mobile_data));
    verify(transfer).pause();
    ui.verify(() -> UIUtils.showTransfersOnDownloadStart(activity), never());
  }

  @Test
  public void aDownloadThatWillUseMobileDataWarnsThenShowsTheTransfers() {
    BittorrentDownload transfer = mock(BittorrentDownload.class);
    when(transfer.getState()).thenReturn(TransferState.DOWNLOADING);
    when(manager.downloadTorrent(anyString(), any(), anyString(), any())).thenReturn(transfer);
    when(manager.isBittorrentDownloadAndMobileDataSavingsOff(transfer)).thenReturn(true);
    PeerCatalogActivity activity = open(pub);
    listWith(activity, Arrays.asList(entry(HASH, "Ubuntu"))).performItemClick(null, 0, 0);
    shadowOf(Looper.getMainLooper()).idle();
    ui.verify(
        () -> UIUtils.showLongMessage(activity, R.string.torrent_transfer_consuming_mobile_data));
    ui.verify(() -> UIUtils.showShortMessage(activity, R.string.download_added_to_queue));
    ui.verify(() -> UIUtils.showTransfersOnDownloadStart(activity));
  }

  @Test
  public void anInvalidTransferShowsItsReasonAndAnErroredOneReportsTheFailure() {
    BittorrentDownload invalid =
        mock(BittorrentDownload.class, withSettings().extraInterfaces(InvalidTransfer.class));
    when(((InvalidTransfer) invalid).getReasonResId())
        .thenReturn(R.string.cannot_start_engine_without_vpn);
    when(manager.downloadTorrent(anyString(), any(), anyString(), any())).thenReturn(invalid);
    PeerCatalogActivity activity = open(pub);
    listWith(activity, Arrays.asList(entry(HASH, "Ubuntu"))).performItemClick(null, 0, 0);
    shadowOf(Looper.getMainLooper()).idle();
    ui.verify(() -> UIUtils.showLongMessage(activity, R.string.cannot_start_engine_without_vpn));
  }

  @Test
  public void aFinishedActivityIgnoresALateDownloadResult() {
    BittorrentDownload transfer = mock(BittorrentDownload.class);
    when(transfer.getState()).thenReturn(TransferState.DOWNLOADING);
    when(manager.downloadTorrent(anyString(), any(), anyString(), any())).thenReturn(transfer);
    PeerCatalogActivity activity = open(pub);
    ListView list = listWith(activity, Arrays.asList(entry(HASH, "Ubuntu")));
    activity.finish();
    list.performItemClick(null, 0, 0);
    shadowOf(Looper.getMainLooper()).idle();
    ui.verify(() -> UIUtils.showTransfersOnDownloadStart(activity), never());
    ui.verify(() -> UIUtils.showShortMessage(any(PeerCatalogActivity.class), anyInt()), never());
  }

  @Test
  public void clickingSomethingThatIsNotACatalogEntryDoesNothing() {
    PeerCatalogActivity activity = open(pub);
    ListView list = activity.findViewById(R.id.peer_catalog_list);
    list.setAdapter(
        new android.widget.ArrayAdapter<>(
            activity, android.R.layout.simple_list_item_1, new String[] {"x"}));
    list.performItemClick(null, 0, 0);
    verify(manager, never()).downloadTorrent(anyString(), any(), anyString(), any());
  }
}
