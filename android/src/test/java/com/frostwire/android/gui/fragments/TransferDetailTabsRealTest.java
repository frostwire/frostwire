/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Looper;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.RealTransfer;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractTransferDetailFragment;
import com.frostwire.android.libtorrent.TestEngine;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

/**
 * The tabs of the transfer detail screen over a real transfer: what they show comes from libtorrent
 * (status, peers, pieces, files, rate limits), not from a test double.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TransferDetailTabsRealTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private RealTransfer transfer;

  @After
  public void tearDown() {
    if (transfer != null) {
      transfer.close();
    }
  }

  private <F extends AbstractTransferDetailFragment> F open(
      F fragment, boolean[] selection, boolean withSeeder) throws Exception {
    return open(fragment, selection, withSeeder, false);
  }

  private <F extends AbstractTransferDetailFragment> F open(
      F fragment, boolean[] selection, boolean withSeeder, boolean partialPeer) throws Exception {
    transfer =
        RealTransfer.start(tmp.newFolder("root").toPath(), selection, withSeeder, partialPeer);
    ActivityController<FragmentTestHost> host = FragmentTestHost.launch(fragment);
    fragment.bindToTransfer(transfer.ui);
    host.pause().resume();
    settle();
    return fragment;
  }

  private static void settle() throws Exception {
    Thread.sleep(300);
    shadowOf(Looper.getMainLooper()).idle();
  }

  /** Runs the screen's timer until {@code done}: the tabs refresh themselves on every tick. */
  private static void tickUntil(AbstractTransferDetailFragment fragment, BooleanSupplier done)
      throws Exception {
    for (int i = 0; i < 300 && !done.getAsBoolean(); i++) {
      fragment.onTime();
      Thread.sleep(50);
      shadowOf(Looper.getMainLooper()).idle();
    }
    assertTrue("the tab never showed it", done.getAsBoolean());
  }

  /** What a string with one argument says before the argument. */
  private static String before(String withMarker) {
    return withMarker.substring(0, withMarker.indexOf('@'));
  }

  private static String text(Fragment fragment, int id) {
    return ((TextView) fragment.requireView().findViewById(id)).getText().toString();
  }

  private static void layout(RecyclerView list) {
    list.measure(
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY));
    list.layout(0, 0, 1080, 4000);
  }

  // ---------------------------------------------------------------- status

  @Test
  public void theStatusTabShowsTheProgressAndTrafficOfAFinishedTransfer() throws Exception {
    TransferDetailStatusFragment tab = open(new TransferDetailStatusFragment(), null, true);
    transfer.awaitComplete();

    tickUntil(tab, () -> text(tab, R.id.fragment_transfer_detail_status_completion).equals("100%"));

    // a transfer that is not downloading has no time left
    assertEquals("0", text(tab, R.id.fragment_transfer_detail_status_time_left));
    assertEquals(
        tab.getString(
            R.string.m_of_n_strings,
            UIUtils.getBytesInHuman(transfer.dl.getTotalBytesReceived()),
            UIUtils.getBytesInHuman(transfer.ui.getSize())),
        text(tab, R.id.fragment_transfer_detail_status_downloaded));
    assertEquals(
        UIUtils.getBytesInHuman(transfer.ui.getBytesSent()),
        text(tab, R.id.fragment_transfer_detail_status_uploaded));
    assertFalse(text(tab, R.id.fragment_transfer_detail_status_share_ratio).isEmpty());
    assertFalse(text(tab, R.id.fragment_transfer_detail_status_peers).isEmpty());
    assertFalse(text(tab, R.id.fragment_transfer_detail_status_seeds).isEmpty());
    assertFalse(text(tab, R.id.fragment_transfer_detail_status_active_time).isEmpty());
    assertFalse(text(tab, R.id.fragment_transfer_detail_status_seeding_time).isEmpty());
  }

  @Test
  public void theStatusTabOfATransferWithoutPeersShowsNothingDownloaded() throws Exception {
    TransferDetailStatusFragment tab = open(new TransferDetailStatusFragment(), null, false);

    tickUntil(tab, () -> !text(tab, R.id.fragment_transfer_detail_status_completion).isEmpty());

    assertEquals("0%", text(tab, R.id.fragment_transfer_detail_status_completion));
    assertEquals(
        tab.getString(R.string.m_of_n_decimals, 0, 0),
        text(tab, R.id.fragment_transfer_detail_status_peers));
  }

  // --------------------------------------------------------------- details

  @Test
  public void theDetailsTabShowsWhatTheTorrentIs() throws Exception {
    TransferDetailDetailsFragment tab = open(new TransferDetailDetailsFragment(), null, false);

    tickUntil(tab, () -> !text(tab, R.id.fragment_transfer_detail_details_magnet).isEmpty());

    assertEquals(
        transfer.ui.getSavePath().getAbsolutePath(),
        text(tab, R.id.fragment_transfer_detail_details_storage_path));
    assertEquals(
        UIUtils.getBytesInHuman(transfer.ui.getSize()),
        text(tab, R.id.fragment_transfer_detail_details_total_size));
    assertEquals("2", text(tab, R.id.fragment_transfer_detail_details_files_number));
    assertEquals(transfer.ui.getInfoHash(), text(tab, R.id.fragment_transfer_detail_details_hash));
    assertEquals(
        transfer.ui.getCachedMagnetUri(), text(tab, R.id.fragment_transfer_detail_details_magnet));
    assertFalse(text(tab, R.id.fragment_transfer_detail_details_created_on).isEmpty());
    // no limits set: both are unlimited, and the torrent has no comment to show
    assertEquals(
        tab.getString(R.string.unlimited),
        text(tab, R.id.fragment_transfer_detail_details_speed_limit_download));
    assertEquals(
        tab.getString(R.string.unlimited),
        text(tab, R.id.fragment_transfer_detail_details_speed_limit_upload));
    assertEquals(
        View.GONE,
        tab.requireView()
            .findViewById(R.id.fragment_transfer_detail_details_comment_container)
            .getVisibility());
  }

  @Test
  public void theHashAndTheMagnetCanBeCopiedToTheClipboard() throws Exception {
    TransferDetailDetailsFragment tab = open(new TransferDetailDetailsFragment(), null, false);
    tickUntil(tab, () -> !text(tab, R.id.fragment_transfer_detail_details_magnet).isEmpty());
    ClipboardManager clipboard =
        (ClipboardManager) tab.requireContext().getSystemService(Context.CLIPBOARD_SERVICE);

    tab.requireView()
        .findViewById(R.id.fragment_transfer_detail_details_hash_copy_button)
        .performClick();
    assertEquals(
        transfer.ui.getInfoHash(), clipboard.getPrimaryClip().getItemAt(0).getText().toString());

    tab.requireView().findViewById(R.id.fragment_transfer_detail_details_magnet).performClick();
    assertEquals(
        transfer.ui.getCachedMagnetUri(),
        clipboard.getPrimaryClip().getItemAt(0).getText().toString());
  }

  @Test
  public void aSpeedLimitChosenInTheDialogIsSetOnTheRealTorrent() throws Exception {
    TransferDetailDetailsFragment tab = open(new TransferDetailDetailsFragment(), null, false);
    tickUntil(tab, () -> !text(tab, R.id.fragment_transfer_detail_details_magnet).isEmpty());

    tab.requireView()
        .findViewById(R.id.fragment_transfer_detail_details_speed_limit_download)
        .performClick();
    settle();
    AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
    assertTrue(dialog.isShowing());
    // unlimited is shown first; taking it off and moving the slider sets a limit
    View unlimited =
        dialog.findViewById(R.id.dialog_preference_seekbar_with_checkbox_unlimited_checkbox);
    unlimited.performClick();
    SeekBar seek = dialog.findViewById(R.id.dialog_preference_seekbar_with_checkbox_seekbar);
    seek.setProgress(200_000);
    dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    shadowOf(Looper.getMainLooper()).idle();

    assertEquals(200_000, transfer.dl.getDownloadRateLimit());
    transfer.ui.updateCachedState();
    tickUntil(
        tab,
        () ->
            text(tab, R.id.fragment_transfer_detail_details_speed_limit_download)
                .equals(UIUtils.getBytesInHuman(200_000) + "/s"));

    // and back to unlimited
    tab.requireView()
        .findViewById(R.id.fragment_transfer_detail_details_speed_limit_download_arrow)
        .performClick();
    settle();
    AlertDialog again = (AlertDialog) ShadowDialog.getLatestDialog();
    again
        .findViewById(R.id.dialog_preference_seekbar_with_checkbox_unlimited_checkbox)
        .performClick();
    again.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals(0, transfer.dl.getDownloadRateLimit());
  }

  @Test
  public void anUploadLimitIsSetAndTheDialogCanBeCancelled() throws Exception {
    TransferDetailDetailsFragment tab = open(new TransferDetailDetailsFragment(), null, false);
    tickUntil(tab, () -> !text(tab, R.id.fragment_transfer_detail_details_magnet).isEmpty());

    tab.requireView()
        .findViewById(R.id.fragment_transfer_detail_details_speed_limit_upload)
        .performClick();
    settle();
    AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
    dialog
        .findViewById(R.id.dialog_preference_seekbar_with_checkbox_unlimited_checkbox)
        .performClick();
    ((SeekBar) dialog.findViewById(R.id.dialog_preference_seekbar_with_checkbox_seekbar))
        .setProgress(5_000);
    dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    // cancelled: libtorrent still has no limit (-1)
    assertEquals(-1, transfer.dl.getUploadRateLimit());

    tab.requireView()
        .findViewById(R.id.fragment_transfer_detail_details_speed_limit_upload_arrow)
        .performClick();
    settle();
    AlertDialog second = (AlertDialog) ShadowDialog.getLatestDialog();
    second
        .findViewById(R.id.dialog_preference_seekbar_with_checkbox_unlimited_checkbox)
        .performClick();
    // the slider can't go below its start: it is raised to it
    ((SeekBar) second.findViewById(R.id.dialog_preference_seekbar_with_checkbox_seekbar))
        .setProgress(10);
    second.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals(1024, transfer.dl.getUploadRateLimit());
  }

  // ----------------------------------------------------------------- peers

  @Test
  public void thePeersTabListsTheRealPeersOfTheTransfer() throws Exception {
    TransferDetailPeersFragment tab = open(new TransferDetailPeersFragment(), null, true, true);
    RecyclerView list =
        tab.requireView().findViewById(R.id.fragment_transfer_detail_peers_recyclerview);
    for (int i = 0; i < 100 && transfer.dl.getTorrentHandle().peerInfo().isEmpty(); i++) {
      Thread.sleep(100);
    }

    tickUntil(tab, () -> list.getAdapter() != null && list.getAdapter().getItemCount() == 1);

    assertEquals(
        tab.getString(R.string.n_peers, 1), text(tab, R.id.fragment_transfer_detail_peers_number));
    layout(list);
    View row = list.getChildAt(0);
    String address =
        ((TextView) row.findViewById(R.id.view_transfer_detail_peer_item_address))
            .getText()
            .toString();
    assertTrue(
        address, address.startsWith("bt://127.0.0.1:") || address.startsWith("uTP://127.0.0.1:"));
    assertFalse(
        ((TextView) row.findViewById(R.id.view_transfer_detail_peer_item_client)).getText().length()
            == 0);
    String downloaded =
        ((TextView) row.findViewById(R.id.view_transfer_detail_peer_downloaded))
            .getText()
            .toString();
    String uploaded =
        ((TextView) row.findViewById(R.id.view_transfer_detail_peer_uploaded)).getText().toString();
    // the totals are read from libtorrent when the row is bound
    assertTrue(
        downloaded, downloaded.startsWith(before(tab.getString(R.string.downloaded_n, "@"))));
    assertTrue(uploaded, uploaded.startsWith(before(tab.getString(R.string.uploaded_n, "@"))));
  }

  // ---------------------------------------------------------------- pieces

  @Test
  public void thePiecesTabCountsTheDownloadedPiecesOfTheTransfer() throws Exception {
    TransferDetailPiecesFragment tab = open(new TransferDetailPiecesFragment(), null, true);
    transfer.awaitComplete();

    int total = transfer.torrent.album.numPieces();
    tickUntil(
        tab,
        () ->
            text(tab, R.id.fragment_transfer_detail_pieces_pieces_number)
                .equals(total + "/" + total));

    assertEquals(
        UIUtils.getBytesInHuman(transfer.torrent.album.pieceSizeForReq(0)),
        text(tab, R.id.fragment_transfer_detail_pieces_piece_size_number));
  }

  @Test
  public void thePiecesTabOfATransferWithoutPeersShowsNoneDownloaded() throws Exception {
    TransferDetailPiecesFragment tab = open(new TransferDetailPiecesFragment(), null, false);
    int total = transfer.torrent.album.numPieces();

    tickUntil(
        tab,
        () -> text(tab, R.id.fragment_transfer_detail_pieces_pieces_number).equals("0/" + total));
  }

  // ----------------------------------------------------------------- files

  @Test
  public void theFilesTabListsTheFilesOfTheTorrentWithTheirProgress() throws Exception {
    TransferDetailFilesFragment tab = open(new TransferDetailFilesFragment(), null, true);
    transfer.awaitComplete();
    RecyclerView list =
        tab.requireView().findViewById(R.id.fragment_transfer_detail_files_recycler_view);

    tickUntil(tab, () -> list.getAdapter() != null && list.getAdapter().getItemCount() == 2);

    assertEquals(
        tab.getString(R.string.n_files, 2),
        text(tab, R.id.fragment_transfer_detail_files_file_number));
    layout(list);
    assertEquals(2, list.getChildCount());
    String names = "";
    for (int i = 0; i < list.getChildCount(); i++) {
      names +=
          ((TextView)
                  list.getChildAt(i).findViewById(R.id.fragment_transfer_detail_files_file_name))
              .getText();
    }
    assertTrue(names, names.contains("one.mp3") && names.contains("two.txt"));
    TestEngine.await(
        () -> {
          tab.onTime();
          shadowOf(Looper.getMainLooper()).idle();
          layout(list);
          for (int i = 0; i < list.getChildCount(); i++) {
            String percentage =
                ((TextView)
                        list.getChildAt(i)
                            .findViewById(R.id.fragment_transfer_detail_files_file_percentage))
                    .getText()
                    .toString();
            if (!percentage.equals("100%")) {
              return false;
            }
          }
          return list.getChildCount() == 2;
        },
        "both files show 100%");
  }
}
