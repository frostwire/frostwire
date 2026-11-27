/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.adapters.menu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.view.View;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.activities.PeerCatalogActivity;
import com.frostwire.android.gui.activities.TransferDetailActivity;
import com.frostwire.android.gui.transfers.InvalidTransfer;
import com.frostwire.android.gui.transfers.TorrentDownloadInfo;
import com.frostwire.android.gui.transfers.TorrentFetcherDownload;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.TimerObserver;
import com.frostwire.search.SearchResult;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.Transfer;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The small context-menu actions of the transfers and file lists, with fake transfers. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SimpleMenuActionsTest {

  private Activity context;
  private MockedStatic<UIUtils> ui;
  private MockedStatic<TransferManager> transfers;
  private TransferManager manager;

  /** A context that counts the timer ticks MenuActions send to {@link TimerObserver}s. */
  private static final class ObserverContext extends ContextWrapper implements TimerObserver {
    final AtomicInteger ticks = new AtomicInteger();

    ObserverContext(Context base) {
      super(base);
    }

    @Override
    public void onTime() {
      ticks.incrementAndGet();
    }
  }

  private static final class FailingContext extends ContextWrapper {
    FailingContext(Context base) {
      super(base);
    }

    @Override
    public void startActivity(Intent intent) {
      throw new ActivityNotFoundException("nothing handles " + intent);
    }
  }

  @Before
  public void setUp() {
    context = Robolectric.buildActivity(Activity.class).create().get();
    ui = mockStatic(UIUtils.class);
    manager = mock(TransferManager.class);
    transfers = mockStatic(TransferManager.class);
    transfers.when(TransferManager::instance).thenReturn(manager);
  }

  @After
  public void tearDown() {
    ui.close();
    transfers.close();
  }

  private Intent nextStarted() {
    return shadowOf(context).getNextStartedActivity();
  }

  @Test
  public void pauseDownloadPausesOnlyRunningTransfersAndTicksTheTimer() {
    BittorrentDownload dl = mock(BittorrentDownload.class);
    ObserverContext observer = new ObserverContext(context);
    PauseDownloadMenuAction action = new PauseDownloadMenuAction(observer, dl);
    assertEquals(context.getString(R.string.pause_torrent_menu_action), action.getText());
    assertNotNull(action.getImage());
    action.onClick();
    verify(dl).pause();
    assertEquals(1, observer.ticks.get());

    BittorrentDownload paused = mock(BittorrentDownload.class);
    when(paused.isPaused()).thenReturn(true);
    new PauseDownloadMenuAction(context, paused).onClick();
    verify(paused, never()).pause();
  }

  private void withNetwork(boolean online, Runnable body) {
    NetworkManager network = mock(NetworkManager.class);
    when(network.isInternetDataConnectionUp()).thenReturn(online);
    try (MockedStatic<NetworkManager> nets = mockStatic(NetworkManager.class)) {
      nets.when(NetworkManager::instance).thenReturn(network);
      body.run();
    }
  }

  @Test
  public void resumeDownloadResumesPausedTransfersWhenOnline() {
    withNetwork(
        true,
        () -> {
          BittorrentDownload dl = mock(BittorrentDownload.class);
          when(dl.isPaused()).thenReturn(true);
          ObserverContext observer = new ObserverContext(context);
          new ResumeDownloadMenuAction(observer, dl, R.string.resume_torrent_menu_action).onClick();
          verify(dl).resume();
          assertEquals(1, observer.ticks.get());

          BittorrentDownload running = mock(BittorrentDownload.class);
          new ResumeDownloadMenuAction(context, running, R.string.resume_torrent_menu_action)
              .onClick();
          verify(running, never()).resume();
        });
  }

  @Test
  public void resumeDownloadAsksToCheckTheConnectionWhenOffline() {
    withNetwork(
        false,
        () -> {
          BittorrentDownload dl = mock(BittorrentDownload.class);
          when(dl.isPaused()).thenReturn(true);
          new ResumeDownloadMenuAction(context, dl, R.string.resume_torrent_menu_action).onClick();
          verify(dl, never()).resume();
          ui.verify(
              () ->
                  UIUtils.showShortMessage(
                      context, R.string.please_check_connection_status_before_resuming_download));
        });
  }

  @Test
  public void resumeDownloadDialogNegativeAnswerExplainsBittorrentStaysOff() {
    BittorrentDownload dl = mock(BittorrentDownload.class);
    ResumeDownloadMenuAction action =
        new ResumeDownloadMenuAction(context, dl, R.string.resume_torrent_menu_action);
    action.onDialogClick("0", android.app.Dialog.BUTTON_NEGATIVE);
    ui.verify(
        () ->
            UIUtils.showLongMessage(
                context,
                R.string.the_file_could_not_be_seeded_bittorrent_will_remain_disconnected));
    // other tags are ignored
    action.onDialogClick("other", android.app.Dialog.BUTTON_NEGATIVE);
    ui.verify(() -> UIUtils.showLongMessage(any(Context.class), anyInt()), times(1));
  }

  @Test
  public void retryInvalidTransferRemovesItAndStartsTheSearchResultAgain() {
    SearchResult sr = mock(SearchResult.class);
    InvalidTransfer invalid =
        mock(
            InvalidTransfer.class,
            org.mockito.Mockito.withSettings().extraInterfaces(Transfer.class));
    when(invalid.getSearchResult()).thenReturn(sr);
    new RetryDownloadAction(context, (Transfer) invalid).onClick();
    verify(manager).remove((Transfer) invalid);
    verify(manager).download(sr);
  }

  @Test
  public void retryTorrentFetcherDownloadRestartsFromItsTorrentUrl() {
    TorrentFetcherDownload tfd = mock(TorrentFetcherDownload.class);
    TorrentDownloadInfo info = mock(TorrentDownloadInfo.class);
    when(info.getTorrentUrl()).thenReturn("http://example.com/a.torrent");
    when(tfd.getTorrentDownloadInfo()).thenReturn(info);
    when(tfd.getName()).thenReturn("a");
    new RetryDownloadAction(context, tfd).onClick();
    verify(manager).remove(tfd);
    verify(manager).downloadTorrent(eq("http://example.com/a.torrent"), any(), eq("a"));
  }

  @Test
  public void retryOfAnUnrelatedTransferOnlyRemovesIt() {
    Transfer plain = mock(Transfer.class);
    new RetryDownloadAction(context, plain).onClick();
    verify(manager).remove(plain);
    verify(manager, never()).download(any(SearchResult.class));
  }

  @Test
  public void copyToClipboardPutsTheDataOnTheClipboardAndConfirms() {
    ObserverContext observer = new ObserverContext(context);
    new CopyToClipboardMenuAction(
            observer,
            R.drawable.contextmenu_icon_copy,
            R.string.transfers_context_menu_copy_magnet,
            42,
            "magnet:?x")
        .onClick();
    ClipboardManager clipboard =
        (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData clip = clipboard.getPrimaryClip();
    assertNotNull(clip);
    assertEquals("magnet:?x", clip.getItemAt(0).getText().toString());
    ui.verify(() -> UIUtils.showLongMessage(observer, 42));
    assertEquals(1, observer.ticks.get());
  }

  @Test
  public void copyToClipboardWithoutDataDoesNothing() {
    new CopyToClipboardMenuAction(
            context,
            R.drawable.contextmenu_icon_copy,
            R.string.transfers_context_menu_copy_magnet,
            42,
            null)
        .onClick();
    ui.verify(() -> UIUtils.showLongMessage(any(Context.class), anyInt()), never());
  }

  @Test
  public void copyMagnetOfAMissingTorrentCopiesNothingUsable() {
    CopyMagnetMenuAction nullPath =
        new CopyMagnetMenuAction(
            context,
            R.drawable.contextmenu_icon_magnet,
            R.string.transfers_context_menu_copy_magnet,
            7,
            null);
    assertEquals("", nullPath.getData());
    CopyMagnetMenuAction missingFile =
        new CopyMagnetMenuAction(
            context,
            R.drawable.contextmenu_icon_magnet,
            R.string.transfers_context_menu_copy_magnet,
            7,
            "/no/such.torrent",
            false);
    assertEquals("", missingFile.getData());
  }

  @Test
  public void bitcoinTipAddsTheSchemeOnlyWhenMissing() {
    new SendBitcoinTipAction(context, "bc1qexample").onClick();
    assertEquals("bitcoin:bc1qexample", nextStarted().getDataString());
    new SendBitcoinTipAction(context, "bitcoin:bc1qother").onClick();
    assertEquals("bitcoin:bc1qother", nextStarted().getDataString());
  }

  @Test
  public void bitcoinTipWithoutAWalletAppExplainsWhatToInstall() {
    Context failing = new FailingContext(context);
    new SendBitcoinTipAction(failing, "bc1q").onClick();
    ui.verify(() -> UIUtils.showLongMessage(failing, R.string.you_need_a_bitcoin_wallet_app));
  }

  @Test
  public void fiatTipOpensTheUrlAndReportsFailures() {
    new SendFiatTipAction(context, "https://paypal.me/frostwire").onClick();
    Intent started = nextStarted();
    assertEquals(Intent.ACTION_VIEW, started.getAction());
    assertEquals("https://paypal.me/frostwire", started.getDataString());
    Context failing = new FailingContext(context);
    new SendFiatTipAction(failing, "https://paypal.me/frostwire").onClick();
    ui.verify(() -> UIUtils.showLongMessage(failing, R.string.issue_with_tip_donation_uri));
  }

  @Test
  public void browseSharedTorrentsOpensTheCatalogOnlyForA32BytePeerKey() {
    byte[] pub = new byte[32];
    pub[0] = 9;
    new BrowseSharedTorrentsMenuAction(context, pub).onClick();
    Intent started = nextStarted();
    assertEquals(PeerCatalogActivity.class.getName(), started.getComponent().getClassName());
    assertEquals(
        Base64.getUrlEncoder().withoutPadding().encodeToString(pub),
        started.getStringExtra(PeerCatalogActivity.EXTRA_PEER_PUB));

    new BrowseSharedTorrentsMenuAction(context, new byte[5]).onClick();
    new BrowseSharedTorrentsMenuAction(context, null).onClick();
    assertNull(nextStarted());
  }

  @Test
  public void transferDetailsOpensTheDetailScreenForAnInfoHash() {
    new TransferDetailsMenuAction(context, R.string.show_torrent_details, "abc123").onClick();
    Intent started = nextStarted();
    assertEquals(TransferDetailActivity.class.getName(), started.getComponent().getClassName());
    assertEquals("abc123", started.getStringExtra("infoHash"));
  }

  @Test
  public void transferDetailsWithoutInfoHashToastsOnTheClickedView() {
    View clicked = new View(context);
    TransferDetailsMenuAction action =
        new TransferDetailsMenuAction(context, R.string.show_torrent_details, "");
    assertSame(action, action.setClickedView(clicked));
    action.onClick();
    ui.verify(
        () ->
            UIUtils.showShortMessage(
                clicked, R.string.could_not_open_transfer_detail_invalid_infohash));
    assertNull(nextStarted());
    // nothing to toast on when no view was given
    new TransferDetailsMenuAction(context, R.string.show_torrent_details, null).onClick();
    ui.verify(() -> UIUtils.showShortMessage(any(View.class), anyInt()), times(1));
  }

  @Test
  public void sendFileSharesAKnownFileTypeAndRejectsUnknownOnes() {
    FWFileDescriptor fd = new FWFileDescriptor();
    fd.fileType = Constants.FILE_TYPE_AUDIO;
    fd.id = 77;
    fd.mime = "audio/mpeg";
    fd.title = "Song";
    new SendFileMenuAction(context, fd).onClick();
    Intent chooser = nextStarted();
    assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
    Intent send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
    assertEquals("audio/mpeg", send.getType());
    assertEquals("Song", send.getStringExtra(Intent.EXTRA_SUBJECT));
    assertEquals(
        true,
        send.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri.class)
            .toString()
            .endsWith("/77"));

    FWFileDescriptor unknown = new FWFileDescriptor();
    unknown.fileType = (byte) 99;
    new SendFileMenuAction(context, unknown).onClick();
    ui.verify(() -> UIUtils.showLongMessage(context, R.string.cant_open_file));
  }

  @Test
  public void addToThisPlaylistAddsTheFilesAndSwallowsFailures() {
    long[] ids = {1, 2, 3};
    try (MockedStatic<MusicUtils> music = mockStatic(MusicUtils.class)) {
      AddToThisPlaylistMenuAction action =
          new AddToThisPlaylistMenuAction(context, 5L, "Road Trip", ids);
      assertEquals("Road Trip", action.getText());
      action.onClick();
      music.verify(() -> MusicUtils.addToPlaylist(context, ids, 5L));
      music
          .when(() -> MusicUtils.addToPlaylist(any(), any(), anyLong()))
          .thenThrow(new IllegalStateException("db"));
      new AddToThisPlaylistMenuAction(context, 6L, "Other", ids).onClick();
      music.verify(() -> MusicUtils.addToPlaylist(context, ids, 6L));
    }
  }
}
