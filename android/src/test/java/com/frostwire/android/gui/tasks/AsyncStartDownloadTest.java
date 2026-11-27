/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.tasks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.R;
import com.frostwire.android.gui.dialogs.HandpickedTorrentDownloadDialogOnFetch;
import com.frostwire.android.gui.transfers.ExistingDownload;
import com.frostwire.android.gui.transfers.InvalidDownload;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.Transfer;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The download pipeline with its worker thread run inline: what a result turns into in the
 * TransferManager, and what the user is told about it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class AsyncStartDownloadTest {

  private MockedStatic<SystemUtils> system;
  private MockedStatic<TransferManager> managers;
  private MockedStatic<UIUtils> ui;
  private TransferManager manager;
  private Context context;

  @Before
  public void setUp() {
    system = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(1)).run();
              return null;
            });
    system
        .when(() -> SystemUtils.postToUIThreadAtFront(any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(0)).run();
              return null;
            });
    manager = mock(TransferManager.class);
    managers = mockStatic(TransferManager.class);
    managers.when(TransferManager::instance).thenReturn(manager);
    ui = mockStatic(UIUtils.class);
    context = ApplicationProvider.getApplicationContext();
  }

  @After
  public void tearDown() {
    ui.close();
    managers.close();
    system.close();
  }

  @Test
  public void aPlainResultIsDownloadedAndTheMessageShown() {
    SearchResult sr = mock(SearchResult.class);
    Transfer transfer = mock(Transfer.class);
    when(manager.download(sr)).thenReturn(transfer);

    new AsyncStartDownload(context, sr, "Downloading song");

    verify(manager).download(sr);
    ui.verify(() -> UIUtils.showShortMessage(context, "Downloading song"));
    ui.verify(() -> UIUtils.showTransfersOnDownloadStart(context));
  }

  @Test
  public void withoutAMessageNothingIsAnnouncedButTheTransfersTabOpens() {
    SearchResult sr = mock(SearchResult.class);
    when(manager.download(sr)).thenReturn(mock(Transfer.class));

    new AsyncStartDownload(context, sr);

    ui.verify(() -> UIUtils.showShortMessage(any(Context.class), anyString()), never());
    ui.verify(() -> UIUtils.showTransfersOnDownloadStart(context));
  }

  @Test
  public void aTorrentOnMobileDataWithSavingsOnIsQueuedPaused() {
    SearchResult sr = mock(SearchResult.class);
    BittorrentDownload torrent = mock(BittorrentDownload.class);
    when(manager.download(sr)).thenReturn(torrent);
    when(manager.isBittorrentDownloadAndMobileDataSavingsOn(torrent)).thenReturn(true);

    new AsyncStartDownload(context, sr, "msg");

    verify(torrent).pause();
    ui.verify(
        () -> UIUtils.showLongMessage(context, R.string.torrent_transfer_enqueued_on_mobile_data));
    ui.verify(() -> UIUtils.showShortMessage(any(Context.class), anyString()), never());
  }

  @Test
  public void aTorrentOnMobileDataWithSavingsOffWarnsItConsumesData() {
    SearchResult sr = mock(SearchResult.class);
    BittorrentDownload torrent = mock(BittorrentDownload.class);
    when(manager.download(sr)).thenReturn(torrent);
    when(manager.isBittorrentDownloadAndMobileDataSavingsOff(torrent)).thenReturn(true);

    new AsyncStartDownload(context, sr, "msg");

    verify(torrent, never()).pause();
    ui.verify(
        () -> UIUtils.showLongMessage(context, R.string.torrent_transfer_consuming_mobile_data));
    ui.verify(() -> UIUtils.showShortMessage(context, "msg"));
  }

  @Test
  public void anInvalidDownloadTellsTheUserWhy() {
    SearchResult sr = mock(SearchResult.class);
    InvalidDownload invalid = new InvalidDownload(R.string.download_type_not_supported, sr);
    when(manager.download(sr)).thenReturn(invalid);

    new AsyncStartDownload(context, sr, "msg");

    ui.verify(() -> UIUtils.showLongMessage(context, R.string.download_type_not_supported));
    ui.verify(() -> UIUtils.showTransfersOnDownloadStart(any()), never());
  }

  @Test
  public void anAlreadyExistingDownloadIsSilent() {
    SearchResult sr = mock(SearchResult.class);
    when(manager.download(sr)).thenReturn(new ExistingDownload());

    new AsyncStartDownload(context, sr, "msg");

    ui.verifyNoInteractions();
  }

  @Test
  public void aFailingManagerLeavesNoTransferAndNoMessage() {
    SearchResult sr = mock(SearchResult.class);
    when(manager.download(sr)).thenThrow(new IllegalStateException("boom"));

    new AsyncStartDownload(context, sr, "msg");

    ui.verifyNoInteractions();
  }

  @Test
  public void aGoneContextDoesNothing() {
    SearchResult sr = mock(SearchResult.class);

    new AsyncStartDownload(null, sr, "msg");

    verifyNoInteractions(manager);
    ui.verifyNoInteractions();
  }

  @Test
  public void aMeshMagnetIsDownloadedDirectlyWithoutTheFilePickerListener() {
    byte[] peer = new byte[] {1, 2, 3};
    String magnet = "magnet:?xt=urn:btih:abc&x.pe=127.0.0.1:6881";
    TorrentSearchResult sr = mock(TorrentSearchResult.class);
    when(sr.getTorrentUrl()).thenReturn(magnet);
    when(sr.getDistributedSourcePeerPub()).thenReturn(peer);
    when(sr.getDisplayName()).thenReturn("Mesh torrent");
    BittorrentDownload torrent = mock(BittorrentDownload.class);
    when(manager.downloadTorrent(eq(magnet), isNull(), eq("Mesh torrent"), eq(peer)))
        .thenReturn(torrent);

    new AsyncStartDownload(context, sr, null);

    verify(manager).downloadTorrent(eq(magnet), isNull(), eq("Mesh torrent"), eq(peer));
    verify(manager, never()).download(any(SearchResult.class));
    ui.verify(() -> UIUtils.showTransfersOnDownloadStart(context));
  }

  @Test
  public void aTorrentFileResultOpensTheHandpickedFilesDialogOnFetch() {
    TorrentSearchResult sr = mock(TorrentSearchResult.class);
    when(sr.getTorrentUrl()).thenReturn("https://example.com/a.torrent");
    when(sr.getDisplayName()).thenReturn("A torrent");
    AppCompatActivity activity = mock(AppCompatActivity.class);
    when(manager.downloadTorrent(
            eq("https://example.com/a.torrent"),
            any(HandpickedTorrentDownloadDialogOnFetch.class),
            eq("A torrent"),
            isNull()))
        .thenReturn(mock(BittorrentDownload.class));

    new AsyncStartDownload(activity, sr);

    verify(manager)
        .downloadTorrent(
            eq("https://example.com/a.torrent"),
            any(HandpickedTorrentDownloadDialogOnFetch.class),
            eq("A torrent"),
            isNull());
  }

  @Test
  public void aCompositeTorrentResultIsDownloadedAsATorrent() {
    CompositeFileSearchResult sr = mock(CompositeFileSearchResult.class);
    when(sr.isTorrent()).thenReturn(true);
    when(sr.getTorrentUrl()).thenReturn(Optional.of("magnet:?xt=urn:btih:abc&x.pe=1.2.3.4:5"));
    when(sr.getDistributedSourcePeerPub()).thenReturn(Optional.empty());
    when(sr.getDisplayName()).thenReturn("Composite");

    new AsyncStartDownload(context, sr);

    verify(manager)
        .downloadTorrent(
            eq("magnet:?xt=urn:btih:abc&x.pe=1.2.3.4:5"), isNull(), eq("Composite"), isNull());
  }

  @Test
  public void aCompositeNonTorrentResultGoesThroughThePlainDownload() {
    CompositeFileSearchResult sr = mock(CompositeFileSearchResult.class);
    when(sr.isTorrent()).thenReturn(false);
    when(manager.download(sr)).thenReturn(mock(Transfer.class));

    new AsyncStartDownload(context, sr);

    verify(manager).download(sr);
  }

  @Test
  public void startingAPlainDownloadFromAnActivityShowsTheTransfersTabOnItsUiThread() {
    SearchResult sr = mock(SearchResult.class);
    Activity activity = mock(Activity.class);
    doAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(0)).run();
              return null;
            })
        .when(activity)
        .runOnUiThread(any(Runnable.class));
    when(manager.download(sr)).thenReturn(mock(Transfer.class));

    new AsyncStartDownload(activity, sr);

    // once from the activity's UI thread right after the start, once after the post-execute
    ui.verify(() -> UIUtils.showTransfersOnDownloadStart(activity), org.mockito.Mockito.times(2));
  }

  @Test
  public void anInvalidPlainDownloadDoesNotOpenTheTransfersTabFromTheActivity() {
    SearchResult sr = mock(SearchResult.class);
    Activity activity = mock(Activity.class);
    when(manager.download(sr)).thenReturn(new InvalidDownload());

    new AsyncStartDownload(activity, sr);

    verify(activity, never()).runOnUiThread(any(Runnable.class));
  }
}
