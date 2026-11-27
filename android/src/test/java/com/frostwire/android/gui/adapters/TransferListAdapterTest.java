/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.views.MenuAdapter;
import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.HttpDownload;
import com.frostwire.transfers.SoundcloudDownload;
import com.frostwire.transfers.Transfer;
import com.frostwire.transfers.TransferItem;
import com.frostwire.transfers.TransferState;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

/** Drives the transfers list adapter with fake transfers under a plain Application. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TransferListAdapterTest {

  private Activity activity;
  private FrameLayout parent;
  private MockedStatic<NetworkManager> networks;
  private MockedStatic<ConfigurationManager> configs;
  private NetworkManager network;
  private ConfigurationManager config;

  @Before
  public void setUp() {
    activity = Robolectric.buildActivity(Activity.class).create().get();
    activity.setTheme(R.style.Theme_FrostWire);
    parent = new FrameLayout(activity);
    network = mock(NetworkManager.class);
    when(network.isInternetDataConnectionUp()).thenReturn(true);
    when(network.isDataWIFIUp()).thenReturn(true);
    config = mock(ConfigurationManager.class);
    networks = mockStatic(NetworkManager.class);
    networks.when(NetworkManager::instance).thenReturn(network);
    configs = mockStatic(ConfigurationManager.class);
    configs.when(ConfigurationManager::instance).thenReturn(config);
  }

  @After
  public void tearDown() {
    networks.close();
    configs.close();
  }

  private static BittorrentDownload torrent(String hash, String name, TransferState state) {
    BittorrentDownload dl = mock(BittorrentDownload.class);
    when(dl.getInfoHash()).thenReturn(hash);
    when(dl.getDisplayName()).thenReturn(name);
    when(dl.getState()).thenReturn(state);
    when(dl.getSize()).thenReturn(2048L);
    when(dl.getDownloadSpeed()).thenReturn(1024L);
    when(dl.getConnectedSeeds()).thenReturn(2);
    when(dl.getTotalSeeds()).thenReturn(5);
    when(dl.getConnectedPeers()).thenReturn(3);
    when(dl.getTotalPeers()).thenReturn(7);
    when(dl.getProgress()).thenReturn(40);
    when(dl.getPredominantFileExtension()).thenReturn("mp3");
    return dl;
  }

  private TransferListAdapter.ViewHolder bind(TransferListAdapter adapter, int position) {
    TransferListAdapter.ViewHolder holder = adapter.onCreateViewHolder(parent, 0);
    adapter.onBindViewHolder(holder, position);
    return holder;
  }

  /** ListAdapter diffs on a background thread; wait for the main-thread commit. */
  private static void settle(TransferListAdapter adapter, int expectedCount) {
    settleUntil(() -> adapter.getItemCount() == expectedCount);
  }

  private static void settleUntil(java.util.function.BooleanSupplier done) {
    long deadline = System.nanoTime() + 10_000_000_000L;
    while (true) {
      shadowOf(Looper.getMainLooper()).idle();
      if (done.getAsBoolean()) {
        return;
      }
      if (System.nanoTime() > deadline) {
        throw new AssertionError("list never settled");
      }
      Thread.yield();
    }
  }

  private static String text(View root, int id) {
    return ((TextView) root.findViewById(id)).getText().toString();
  }

  @Test
  public void nullInitialListIsEmptyAndUpdateListAcceptsNull() {
    TransferListAdapter adapter = new TransferListAdapter(activity, null);
    assertEquals(0, adapter.getItemCount());
    adapter.updateList(null);
    assertTrue(adapter.getList().isEmpty());
  }

  @Test
  public void getListReturnsACopyOfTheInitialList() {
    Transfer a = torrent("aa", "A", TransferState.DOWNLOADING);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(a));
    settle(adapter, 1);
    List<Transfer> copy = adapter.getList();
    copy.clear();
    assertEquals(1, adapter.getList().size());
    assertEquals(1, adapter.getItemCount());
  }

  @Test
  public void bindingATorrentFillsTheRow() {
    BittorrentDownload dl = torrent("aa", "My Torrent", TransferState.DOWNLOADING);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    TransferListAdapter.ViewHolder holder = bind(adapter, 0);
    View row = holder.itemView;

    assertEquals("My Torrent", text(row, R.id.view_transfer_list_item_title));
    assertEquals(
        activity.getString(R.string.seeds_n, "2 / 5"),
        text(row, R.id.view_transfer_list_item_seeds));
    assertEquals(
        activity.getString(R.string.peers_n, "3 / 7"),
        text(row, R.id.view_transfer_list_item_peers));
    assertEquals(
        40, ((ProgressBar) row.findViewById(R.id.view_transfer_list_item_progress)).getProgress());
    assertTrue(text(row, R.id.view_transfer_list_item_speed).endsWith("/s"));
    assertFalse(text(row, R.id.view_transfer_list_item_size).isEmpty());
    assertEquals(
        activity.getString(R.string.azureus_manager_item_downloading),
        text(row, R.id.view_transfer_list_item_status));
    assertSame(dl, row.getTag());
    // plain BittorrentDownload is not a UIBittorrentDownload, so no details button
    assertEquals(
        View.GONE, row.findViewById(R.id.view_transfer_list_item_button_details).getVisibility());
    assertEquals(
        View.GONE, row.findViewById(R.id.view_transfer_list_item_button_play).getVisibility());
  }

  private static void assertSame(Object a, Object b) {
    org.junit.Assert.assertSame(a, b);
  }

  @Test
  public void seedAndPeerCountsDropTheTotalWhenConnectedExceedsIt() {
    BittorrentDownload dl = torrent("aa", "T", TransferState.DOWNLOADING);
    when(dl.getConnectedSeeds()).thenReturn(9);
    when(dl.getTotalSeeds()).thenReturn(5);
    when(dl.getConnectedPeers()).thenReturn(11);
    when(dl.getTotalPeers()).thenReturn(7);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    View row = bind(adapter, 0).itemView;
    assertEquals(
        activity.getString(R.string.seeds_n, "9"), text(row, R.id.view_transfer_list_item_seeds));
    assertEquals(
        activity.getString(R.string.peers_n, "11"), text(row, R.id.view_transfer_list_item_peers));
  }

  @Test
  public void unknownTotalSeedsShowsAQuestionMark() {
    BittorrentDownload dl = torrent("aa", "T", TransferState.DOWNLOADING);
    when(dl.getConnectedSeeds()).thenReturn(-1);
    when(dl.getTotalSeeds()).thenReturn(-1);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    View row = bind(adapter, 0).itemView;
    assertEquals(
        activity.getString(R.string.seeds_n, "-1 / ?"),
        text(row, R.id.view_transfer_list_item_seeds));
  }

  @Test
  public void withoutInternetTheStatusExplainsAndSeedsPeersAreCleared() {
    when(network.isInternetDataConnectionUp()).thenReturn(false);
    BittorrentDownload dl = torrent("aa", "T", TransferState.DOWNLOADING);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    View row = bind(adapter, 0).itemView;
    String status = text(row, R.id.view_transfer_list_item_status);
    assertTrue(status, status.contains(activity.getString(R.string.check_internet_connection)));
    assertEquals("", text(row, R.id.view_transfer_list_item_seeds));
    assertEquals("", text(row, R.id.view_transfer_list_item_peers));
  }

  @Test
  public void uiTorrentWithDetailsShowsDetailsButtonAndPaymentDrawable() {
    UIBittorrentDownload dl = mock(UIBittorrentDownload.class);
    when(dl.getInfoHash()).thenReturn("bb");
    when(dl.getDisplayName()).thenReturn("UI Torrent");
    when(dl.getState()).thenReturn(TransferState.DOWNLOADING);
    when(dl.hasPaymentOptions()).thenReturn(true);
    PaymentOptions options = new PaymentOptions("bc1qexample", null);
    when(dl.getPaymentOptions()).thenReturn(options);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    View row = bind(adapter, 0).itemView;
    assertEquals(
        View.VISIBLE,
        row.findViewById(R.id.view_transfer_list_item_button_details).getVisibility());
    TextView title = row.findViewById(R.id.view_transfer_list_item_title);
    assertNotNull(title.getCompoundDrawables()[0]);
    // subscribed for state events on bind
    verify(dl).addListener(adapter);
  }

  @Test
  public void fiatPaymentOptionUsesTheFiatDrawableAndMetadataStateHidesDetails() {
    UIBittorrentDownload dl = mock(UIBittorrentDownload.class);
    when(dl.getInfoHash()).thenReturn("bb");
    when(dl.getDisplayName()).thenReturn("UI Torrent");
    when(dl.getState()).thenReturn(TransferState.DOWNLOADING_METADATA);
    when(dl.hasPaymentOptions()).thenReturn(true);
    PaymentOptions options = new PaymentOptions(null, "https://paypal.me/x");
    when(dl.getPaymentOptions()).thenReturn(options);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    View row = bind(adapter, 0).itemView;
    assertEquals(
        View.GONE, row.findViewById(R.id.view_transfer_list_item_button_details).getVisibility());
    TextView title = row.findViewById(R.id.view_transfer_list_item_title);
    assertNotNull(title.getCompoundDrawables()[0]);
  }

  @Test
  public void httpDownloadWithStreamablePreviewShowsPlayButton() {
    HttpDownload dl = mock(HttpDownload.class);
    when(dl.getDisplayName()).thenReturn("Song");
    when(dl.getState()).thenReturn(TransferState.DOWNLOADING);
    when(dl.getSavePath()).thenReturn(new File("/tmp/song.mp3"));
    when(dl.previewFile()).thenReturn(new File("/tmp/song.mp3"));
    when(dl.getSize()).thenReturn(4096L);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    View row = bind(adapter, 0).itemView;
    assertEquals("Song", text(row, R.id.view_transfer_list_item_title));
    assertEquals("", text(row, R.id.view_transfer_list_item_seeds));
    View play = row.findViewById(R.id.view_transfer_list_item_button_play);
    assertEquals(View.VISIBLE, play.getVisibility());
    assertEquals(new File("/tmp/song.mp3"), play.getTag());
  }

  @Test
  public void httpDownloadWithNonStreamablePreviewHidesPlayButton() {
    HttpDownload dl = mock(HttpDownload.class);
    when(dl.getDisplayName()).thenReturn("Doc");
    when(dl.getState()).thenReturn(TransferState.DOWNLOADING);
    when(dl.getSavePath()).thenReturn(new File("/tmp/doc.txt"));
    when(dl.previewFile()).thenReturn(new File("/tmp/doc.txt"));
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    View row = bind(adapter, 0).itemView;
    assertEquals(
        View.GONE, row.findViewById(R.id.view_transfer_list_item_button_play).getVisibility());
  }

  @Test
  public void httpDownloadWithoutPreviewHidesPlayButton() {
    HttpDownload dl = mock(HttpDownload.class);
    when(dl.getDisplayName()).thenReturn("Doc");
    when(dl.getState()).thenReturn(TransferState.DOWNLOADING);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    View row = bind(adapter, 0).itemView;
    assertEquals(
        View.GONE, row.findViewById(R.id.view_transfer_list_item_button_play).getVisibility());
  }

  @Test
  public void soundcloudDownloadAlwaysOffersPreviewWhenItHasOne() {
    SoundcloudDownload dl = mock(SoundcloudDownload.class);
    when(dl.getDisplayName()).thenReturn("Cloud");
    when(dl.getState()).thenReturn(TransferState.DOWNLOADING);
    when(dl.previewFile()).thenReturn(new File("/tmp/x.bin"));
    when(dl.getSavePath()).thenReturn(new File("/tmp/x.bin"));
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    View row = bind(adapter, 0).itemView;
    assertEquals(
        View.VISIBLE, row.findViewById(R.id.view_transfer_list_item_button_play).getVisibility());

    SoundcloudDownload noPreview = mock(SoundcloudDownload.class);
    when(noPreview.getDisplayName()).thenReturn("Cloud2");
    when(noPreview.getState()).thenReturn(TransferState.DOWNLOADING);
    TransferListAdapter adapter2 = new TransferListAdapter(activity, Arrays.asList(noPreview));
    settle(adapter2, 1);
    View row2 = bind(adapter2, 0).itemView;
    assertEquals(
        View.GONE, row2.findViewById(R.id.view_transfer_list_item_button_play).getVisibility());
  }

  @Test
  public void updateViewIgnoresNullAndSwallowsPopulateFailures() {
    TransferListAdapter adapter = new TransferListAdapter(activity, null);
    TransferListAdapter.ViewHolder holder = adapter.onCreateViewHolder(parent, 0);
    holder.updateView(null);
    assertNull(holder.itemView.getTag());
    BittorrentDownload dl = mock(BittorrentDownload.class);
    when(dl.getDisplayName()).thenReturn("x");
    // getState() null -> NPE inside populate must be swallowed
    holder.updateView(dl);
    assertSame(dl, holder.itemView.getTag());
  }

  @Test
  public void canOpenTorrentDetailsRules() {
    assertFalse(TransferListAdapter.canOpenTorrentDetails(mock(BittorrentDownload.class)));
    UIBittorrentDownload ui = mock(UIBittorrentDownload.class);
    when(ui.getInfoHash()).thenReturn("cc");
    when(ui.getState()).thenReturn(TransferState.DOWNLOADING);
    assertTrue(TransferListAdapter.canOpenTorrentDetails(ui));
    when(ui.getState()).thenReturn(TransferState.DOWNLOADING_METADATA);
    assertFalse(TransferListAdapter.canOpenTorrentDetails(ui));
    when(ui.getState()).thenReturn(TransferState.DOWNLOADING_TORRENT);
    assertFalse(TransferListAdapter.canOpenTorrentDetails(ui));
    when(ui.getState()).thenReturn(TransferState.SEEDING);
    when(ui.getInfoHash()).thenReturn("");
    assertFalse(TransferListAdapter.canOpenTorrentDetails(ui));
    when(ui.getInfoHash()).thenReturn(null);
    assertFalse(TransferListAdapter.canOpenTorrentDetails(ui));
  }

  @Test
  public void updateListNotifiesOnlyRowsWhoseVisibleStateChanged() {
    BittorrentDownload a = torrent("aa", "A", TransferState.DOWNLOADING);
    BittorrentDownload b = torrent("bb", "B", TransferState.DOWNLOADING);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(a, b));
    settle(adapter, 2);
    List<Integer> changed = new ArrayList<>();
    adapter.registerAdapterDataObserver(
        new RecyclerView.AdapterDataObserver() {
          @Override
          public void onItemRangeChanged(int positionStart, int itemCount) {
            changed.add(positionStart);
          }
        });
    // first pass records baseline UI state for both rows
    adapter.updateList(Arrays.asList(a, b));
    assertEquals(Arrays.asList(0, 1), changed);
    changed.clear();
    // nothing changed: no notifications
    adapter.updateList(Arrays.asList(a, b));
    assertTrue(changed.isEmpty());
    // progress of b changes
    when(b.getProgress()).thenReturn(90);
    adapter.updateList(Arrays.asList(a, b));
    assertEquals(Collections.singletonList(1), changed);
  }

  @Test
  public void updateListWithDifferentMembersSubmitsAndUnsubscribesDroppedTransfers() {
    UIBittorrentDownload a = mock(UIBittorrentDownload.class);
    when(a.getInfoHash()).thenReturn("aa");
    when(a.getState()).thenReturn(TransferState.DOWNLOADING);
    BittorrentDownload b = torrent("bb", "B", TransferState.DOWNLOADING);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(a));
    settle(adapter, 1);
    adapter.updateList(Arrays.asList(b));
    settleUntil(() -> adapter.getList().get(0) == b);
    verify(a).removeListener(adapter);
    assertEquals(1, adapter.getItemCount());
    assertSame(b, adapter.getList().get(0));
  }

  @Test
  public void removedTransfersAreNotReAddedByLaterUpdates() {
    BittorrentDownload a = torrent("aa", "A", TransferState.DOWNLOADING);
    BittorrentDownload b = torrent("bb", "B", TransferState.DOWNLOADING);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(a, b));
    settle(adapter, 2);
    adapter.removeTransferItem(a);
    settle(adapter, 1);
    assertEquals(1, adapter.getItemCount());
    adapter.updateList(Arrays.asList(a, b));
    settle(adapter, 1);
    assertEquals(1, adapter.getItemCount());
    assertSame(b, adapter.getList().get(0));
  }

  @Test
  public void removeTransferItemEdgeCases() {
    TransferListAdapter empty = new TransferListAdapter(activity, null);
    empty.removeTransferItem(null);
    empty.removeTransferItem(torrent("zz", "Z", TransferState.DOWNLOADING));
    assertEquals(0, empty.getItemCount());

    BittorrentDownload a = torrent("aa", "A", TransferState.DOWNLOADING);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(a));
    settle(adapter, 1);
    // an unrelated transfer that is not in the list leaves it untouched
    adapter.removeTransferItem(torrent("zz", "Z", TransferState.DOWNLOADING));
    settle(adapter, 1);
    assertEquals(1, adapter.getItemCount());
  }

  @Test
  public void transferIdFallsBackToPathNameAndInstance() {
    // two path-only transfers with different paths are different rows, same path are the same
    Transfer p1 = mock(Transfer.class);
    when(p1.getSavePath()).thenReturn(new File("/tmp/one"));
    Transfer p2 = mock(Transfer.class);
    when(p2.getSavePath()).thenReturn(new File("/tmp/two"));
    Transfer named = mock(Transfer.class);
    when(named.getDisplayName()).thenReturn("named");
    Transfer anonymous = mock(Transfer.class);
    TransferListAdapter adapter =
        new TransferListAdapter(activity, Arrays.asList(p1, p2, named, anonymous));
    settle(adapter, 4);
    assertEquals(4, adapter.getItemCount());
    adapter.removeTransferItem(named);
    settle(adapter, 3);
    adapter.removeTransferItem(anonymous);
    settle(adapter, 2);
    adapter.removeTransferItem(p1);
    settle(adapter, 1);
    assertEquals(1, adapter.getItemCount());
    assertSame(p2, adapter.getList().get(0));
  }

  @Test
  public void bindingNullItemIsANoOpAndTransferStateEventsPostToTheMainThread() {
    BittorrentDownload a = torrent("aa", "A", TransferState.DOWNLOADING);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(a));
    settle(adapter, 1);
    List<Integer> changed = new ArrayList<>();
    adapter.registerAdapterDataObserver(
        new RecyclerView.AdapterDataObserver() {
          @Override
          public void onItemRangeChanged(int positionStart, int itemCount) {
            changed.add(positionStart);
          }
        });
    adapter.onTransferStateChanged(a, TransferState.DOWNLOADING, TransferState.SEEDING);
    adapter.onTransferProgressChanged(a, 50);
    assertTrue("posted, not run inline", changed.isEmpty());
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals(Arrays.asList(0, 0), changed);
    // a transfer that is not in the list changes nothing
    changed.clear();
    adapter.onTransferStateChanged(
        torrent("other", "O", TransferState.DOWNLOADING),
        TransferState.DOWNLOADING,
        TransferState.SEEDING);
    adapter.onTransferProgressChanged(torrent("other", "O", TransferState.DOWNLOADING), 5);
    shadowOf(Looper.getMainLooper()).idle();
    assertTrue(changed.isEmpty());
  }

  @Test
  public void cleanupUnsubscribesEverythingAndDismissesTrackedDialogs() {
    UIBittorrentDownload a = mock(UIBittorrentDownload.class);
    when(a.getInfoHash()).thenReturn("aa");
    when(a.getDisplayName()).thenReturn("A");
    when(a.getState()).thenReturn(TransferState.DOWNLOADING);
    HttpDownload h = mock(HttpDownload.class);
    when(h.getDisplayName()).thenReturn("H");
    when(h.getState()).thenReturn(TransferState.DOWNLOADING);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(a, h));
    settle(adapter, 2);
    bind(adapter, 0);
    bind(adapter, 1);
    verify(h).addListener(adapter);
    adapter.cleanup();
    verify(a).removeListener(adapter);
    verify(h).removeListener(adapter);
    adapter.dismissDialogs();
  }

  @Test
  public void clickingATorrentRowShowsItsContextMenuAndTracksTheDialog() {
    BittorrentDownload dl = torrent("aa", "Pausable", TransferState.DOWNLOADING);
    when(dl.isComplete()).thenReturn(false);
    when(dl.isSeeding()).thenReturn(false);
    when(dl.isPaused()).thenReturn(false);
    when(dl.magnetUri()).thenReturn("magnet:?xt=urn:btih:aa");
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    View row = bind(adapter, 0).itemView;
    row.performClick();
    AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
    assertNotNull(dialog);
    MenuAdapter menu = (MenuAdapter) dialog.getListView().getAdapter();
    assertEquals("Pausable", menu.getTitle());
    List<String> kinds = kinds(menu);
    assertEquals(
        Arrays.asList(
            "PauseDownloadMenuAction",
            "CancelMenuAction",
            "CopyToClipboardMenuAction",
            "CopyToClipboardMenuAction"),
        kinds);
    adapter.dismissDialogs();
    assertFalse(dialog.isShowing());
  }

  private static List<String> kinds(MenuAdapter menu) {
    List<String> kinds = new ArrayList<>();
    for (int i = 0; i < menu.getCount(); i++) {
      kinds.add(menu.getItem(i).getClass().getSimpleName());
    }
    return kinds;
  }

  private MenuAdapter longClickMenu(TransferListAdapter adapter, int position) {
    View row = bind(adapter, position).itemView;
    assertTrue(row.performLongClick());
    AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
    return (MenuAdapter) dialog.getListView().getAdapter();
  }

  @Test
  public void pausedTorrentOffersResumeWhenWifiIsUp() {
    BittorrentDownload dl = torrent("aa", "Paused", TransferState.PAUSED);
    when(dl.isPaused()).thenReturn(true);
    when(dl.magnetUri()).thenReturn("");
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    assertEquals(
        Arrays.asList("ResumeDownloadMenuAction", "CancelMenuAction", "CopyToClipboardMenuAction"),
        kinds(longClickMenu(adapter, 0)));
  }

  @Test
  public void pausedTorrentOnMobileDataWithWifiOnlyOffersNoResume() {
    when(network.isDataWIFIUp()).thenReturn(false);
    when(config.getBoolean(com.frostwire.android.core.Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY))
        .thenReturn(true);
    BittorrentDownload dl = torrent("aa", "Paused", TransferState.PAUSED);
    when(dl.isPaused()).thenReturn(true);
    when(dl.magnetUri()).thenReturn(null);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    assertEquals(
        Arrays.asList("CancelMenuAction", "CopyToClipboardMenuAction"),
        kinds(longClickMenu(adapter, 0)));
  }

  @Test
  public void pausedTorrentOnMobileDataAllowedStillOffersResume() {
    when(network.isDataWIFIUp()).thenReturn(false);
    when(config.getBoolean(com.frostwire.android.core.Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY))
        .thenReturn(false);
    BittorrentDownload dl = torrent("aa", "Paused", TransferState.PAUSED);
    when(dl.isPaused()).thenReturn(true);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    assertTrue(kinds(longClickMenu(adapter, 0)).contains("ResumeDownloadMenuAction"));
  }

  @Test
  public void finishedTorrentOffersOpenSeedAndRemoveTorrentAndData() {
    BittorrentDownload dl = torrent("aa", "Done", TransferState.FINISHED);
    when(dl.isComplete()).thenReturn(true);
    TransferItem item = mock(TransferItem.class);
    when(item.getFile()).thenReturn(new File("/tmp/done.mp3"));
    when(dl.getItems()).thenReturn(Collections.singletonList(item));
    when(dl.magnetUri()).thenReturn("magnet:?x");
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    assertEquals(
        Arrays.asList(
            "OpenMenuAction",
            "SeedAction",
            "CancelMenuAction",
            "CopyToClipboardMenuAction",
            "CopyToClipboardMenuAction",
            "CancelMenuAction"),
        kinds(longClickMenu(adapter, 0)));
  }

  @Test
  public void seedingTorrentOffersStopSeeding() {
    BittorrentDownload dl = torrent("aa", "Seeding", TransferState.SEEDING);
    when(dl.isSeeding()).thenReturn(true);
    when(dl.magnetUri()).thenReturn("magnet:?x");
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    List<String> kinds = kinds(longClickMenu(adapter, 0));
    assertTrue(kinds.toString(), kinds.contains("StopSeedingAction"));
    assertFalse(kinds.contains("PauseDownloadMenuAction"));
  }

  @Test
  public void uiTorrentMenuIncludesTipActionsAndDetails() {
    UIBittorrentDownload dl = mock(UIBittorrentDownload.class);
    when(dl.getInfoHash()).thenReturn("dd");
    when(dl.getDisplayName()).thenReturn("Tips");
    when(dl.getState()).thenReturn(TransferState.DOWNLOADING);
    when(dl.getCachedMagnetUri()).thenReturn("magnet:?x");
    when(dl.hasPaymentOptions()).thenReturn(true);
    PaymentOptions options = new PaymentOptions("bc1q", "https://paypal.me/x");
    when(dl.getPaymentOptions()).thenReturn(options);
    when(dl.peekItems()).thenReturn(null);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    List<String> kinds = kinds(longClickMenu(adapter, 0));
    assertTrue(kinds.toString(), kinds.contains("SendBitcoinTipAction"));
    assertTrue(kinds.toString(), kinds.contains("SendFiatTipAction"));
    assertTrue(kinds.toString(), kinds.contains("TransferDetailsMenuAction"));
  }

  @Test
  public void erroredHttpDownloadOffersOnlyCancel() {
    HttpDownload errored = mock(HttpDownload.class);
    when(errored.getDisplayName()).thenReturn("Broken");
    when(errored.getState()).thenReturn(TransferState.ERROR);
    when(errored.getSavePath()).thenReturn(new File("/tmp/broken"));
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(errored));
    settle(adapter, 1);
    assertEquals(Collections.singletonList("CancelMenuAction"), kinds(longClickMenu(adapter, 0)));
  }

  @Test
  public void completedHttpDownloadOffersOpenAndRemove() throws Exception {
    File saved = File.createTempFile("saved", ".mp3");
    saved.deleteOnExit();
    HttpDownload done = mock(HttpDownload.class);
    when(done.getDisplayName()).thenReturn("Saved");
    when(done.getState()).thenReturn(TransferState.FINISHED);
    when(done.isComplete()).thenReturn(true);
    when(done.getSavePath()).thenReturn(saved);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(done));
    settle(adapter, 1);
    List<String> kinds = kinds(longClickMenu(adapter, 0));
    assertTrue(kinds.toString(), kinds.contains("OpenMenuAction"));
    assertTrue(kinds.toString(), kinds.contains("CancelMenuAction"));
  }

  @Test
  public void rowsWithoutAnyMenuActionsDoNotShowADialog() {
    // a Transfer that is neither a torrent nor otherwise tagged still gets Cancel; an
    // untagged view yields no adapter
    TransferListAdapter adapter = new TransferListAdapter(activity, null);
    TransferListAdapter.ViewHolder holder = adapter.onCreateViewHolder(parent, 0);
    holder.itemView.setTag(null);
    holder.updateView(null);
    holder.itemView.performClick();
    assertNull(ShadowDialog.getLatestDialog());
  }

  @Test
  public void detailsButtonOpensTransferDetailsOnlyForUiTorrents() {
    UIBittorrentDownload dl = mock(UIBittorrentDownload.class);
    when(dl.getInfoHash()).thenReturn("ee");
    when(dl.getDisplayName()).thenReturn("D");
    when(dl.getState()).thenReturn(TransferState.DOWNLOADING);
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    View row = bind(adapter, 0).itemView;
    View details = row.findViewById(R.id.view_transfer_list_item_button_details);
    details.performClick();
    android.content.Intent started = shadowOf(activity).getNextStartedActivity();
    assertNotNull(started);
    // a non-torrent tag does nothing
    details.setTag("not a transfer");
    details.performClick();
    assertNull(shadowOf(activity).getNextStartedActivity());
    verify(dl, never()).remove(true);
  }

  @Test
  public void playButtonOnAFileTagOpensTheFile() throws Exception {
    File txt = File.createTempFile("play", ".txt");
    txt.deleteOnExit();
    HttpDownload dl = mock(HttpDownload.class);
    when(dl.getDisplayName()).thenReturn("Clip");
    when(dl.getState()).thenReturn(TransferState.DOWNLOADING);
    when(dl.getSavePath()).thenReturn(new File("/tmp/clip.mp4"));
    when(dl.previewFile()).thenReturn(new File("/tmp/clip.mp4"));
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    View row = bind(adapter, 0).itemView;
    View play = row.findViewById(R.id.view_transfer_list_item_button_play);
    try (MockedStatic<com.frostwire.android.gui.util.UIUtils> ui =
        mockStatic(com.frostwire.android.gui.util.UIUtils.class)) {
      ui.when(() -> com.frostwire.android.gui.util.UIUtils.isAudioFile(any())).thenReturn(false);
      ui.when(() -> com.frostwire.android.gui.util.UIUtils.getBytesInHuman(anyLongValue()))
          .thenReturn("0 B");
      play.performClick();
      ui.verify(
          () ->
              com.frostwire.android.gui.util.UIUtils.openFile(activity, new File("/tmp/clip.mp4")));
    }
  }

  private static long anyLongValue() {
    return org.mockito.ArgumentMatchers.anyLong();
  }

  @Test
  public void playButtonOnATransferItemTagPlaysCompletedItemsAndIgnoresIncompleteOnes() {
    TransferListAdapter adapter = new TransferListAdapter(activity, null);
    TransferListAdapter.ViewHolder holder = adapter.onCreateViewHolder(parent, 0);
    HttpDownload dl = mock(HttpDownload.class);
    when(dl.getDisplayName()).thenReturn("Clip");
    when(dl.getState()).thenReturn(TransferState.DOWNLOADING);
    when(dl.getSavePath()).thenReturn(new File("/tmp/clip.mp4"));
    when(dl.previewFile()).thenReturn(new File("/tmp/clip.mp4"));
    holder.updateView(dl);
    View play = holder.itemView.findViewById(R.id.view_transfer_list_item_button_play);
    TransferItem complete = mock(TransferItem.class);
    when(complete.isComplete()).thenReturn(true);
    when(complete.getFile()).thenReturn(new File("/tmp/a.mkv"));
    TransferItem incomplete = mock(TransferItem.class);
    when(incomplete.isComplete()).thenReturn(false);
    try (MockedStatic<com.frostwire.android.gui.util.UIUtils> ui =
        mockStatic(com.frostwire.android.gui.util.UIUtils.class)) {
      ui.when(() -> com.frostwire.android.gui.util.UIUtils.isAudioFile(any())).thenReturn(false);
      play.setTag(complete);
      play.performClick();
      ui.verify(
          () -> com.frostwire.android.gui.util.UIUtils.openFile(activity, new File("/tmp/a.mkv")));
      play.setTag(incomplete);
      play.performClick();
      ui.verify(
          () ->
              com.frostwire.android.gui.util.UIUtils.openFile(any(Context.class), any(File.class)),
          org.mockito.Mockito.times(1));
    }
  }

  @Test
  public void playingAnAudioFileFromTheRowGoesThroughTheMusicPlayer() throws Exception {
    HttpDownload dl = mock(HttpDownload.class);
    when(dl.getDisplayName()).thenReturn("Song");
    when(dl.getState()).thenReturn(TransferState.DOWNLOADING);
    when(dl.getSavePath()).thenReturn(new File("/tmp/song.mp3"));
    when(dl.previewFile()).thenReturn(new File("/tmp/song.mp3"));
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(dl));
    settle(adapter, 1);
    View play = bind(adapter, 0).itemView.findViewById(R.id.view_transfer_list_item_button_play);
    TransferItem complete = mock(TransferItem.class);
    when(complete.isComplete()).thenReturn(true);
    when(complete.getFile()).thenReturn(new File("/tmp/a.mp3"));
    try (MockedStatic<com.frostwire.android.gui.util.UIUtils> ui =
            mockStatic(com.frostwire.android.gui.util.UIUtils.class);
        MockedStatic<com.andrew.apollo.utils.MusicUtils> music =
            mockStatic(com.andrew.apollo.utils.MusicUtils.class)) {
      ui.when(() -> com.frostwire.android.gui.util.UIUtils.isAudioFile(any())).thenReturn(true);
      play.performClick();
      music.verify(
          () ->
              com.andrew.apollo.utils.MusicUtils.playFileFromUserItemClick(
                  activity, new File("/tmp/song.mp3")));
      play.setTag(complete);
      play.performClick();
      music.verify(
          () ->
              com.andrew.apollo.utils.MusicUtils.playFileFromUserItemClick(
                  activity, new File("/tmp/a.mp3")));
    }
  }

  @Test
  public void anErroredInvalidTransferOffersRetryBeforeCancel() {
    HttpDownload errored =
        mock(
            HttpDownload.class,
            org.mockito.Mockito.withSettings()
                .extraInterfaces(com.frostwire.android.gui.transfers.InvalidTransfer.class));
    when(errored.getDisplayName()).thenReturn("Broken");
    when(errored.getState()).thenReturn(TransferState.ERROR);
    when(errored.getSavePath()).thenReturn(new File("/tmp/broken"));
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(errored));
    settle(adapter, 1);
    assertEquals(
        Arrays.asList("RetryDownloadAction", "CancelMenuAction"), kinds(longClickMenu(adapter, 0)));
  }

  @Test
  public void aRowWhoseMenuCannotBeBuiltReportsTheLongPressAsUnhandled() {
    Transfer broken = mock(Transfer.class);
    when(broken.getDisplayName()).thenReturn("Broken");
    TransferListAdapter adapter = new TransferListAdapter(activity, Arrays.asList(broken));
    settle(adapter, 1);
    View row = bind(adapter, 0).itemView;
    row.setTag(broken);
    parent.addView(row);
    // getState() is null for this fake, so building the menu throws and the press is not consumed
    assertFalse(row.performLongClick());
    assertNull(ShadowDialog.getLatestDialog());
  }
}
