/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.transfers.TransferState;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Date;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TransferDetailDetailsFragmentTest {
  private static final String HASH = "0123456789abcdef0123456789abcdef01234567";
  private static final String MAGNET = "magnet:?xt=urn:btih:" + HASH;

  private static UIBittorrentDownload download() {
    UIBittorrentDownload d = mock(UIBittorrentDownload.class);
    when(d.getInfoHash()).thenReturn(HASH);
    when(d.getDisplayName()).thenReturn("A Torrent");
    when(d.getState()).thenReturn(TransferState.DOWNLOADING);
    when(d.getSavePath()).thenReturn(new File("/storage/downloads/A Torrent"));
    when(d.getSize()).thenReturn(2048L);
    when(d.getCachedFileCount()).thenReturn(3);
    when(d.getCachedMagnetUri()).thenReturn(MAGNET);
    when(d.getCreated()).thenReturn(new Date(1_700_000_000_000L));
    when(d.getCachedTorrentComment()).thenReturn("a comment");
    when(d.getCachedDownloadRateLimit()).thenReturn(10240);
    when(d.getCachedUploadRateLimit()).thenReturn(0);
    return d;
  }

  private static String text(TransferDetailDetailsFragment f, int id) {
    return ((TextView) f.requireView().findViewById(id)).getText().toString();
  }

  private static String clip() {
    Context app = ApplicationProvider.getApplicationContext();
    ClipboardManager cm = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData data = cm.getPrimaryClip();
    return data == null ? null : data.getItemAt(0).getText().toString();
  }

  private static TransferDetailDetailsFragment launch(UIBittorrentDownload d) {
    TransferDetailDetailsFragment fragment = new TransferDetailDetailsFragment();
    FragmentTestHost.launch(fragment);
    fragment.bindToTransfer(d);
    fragment.onTime();
    return fragment;
  }

  @Test
  public void aFreshTabHasEmptyFields() {
    TransferDetailDetailsFragment fragment = new TransferDetailDetailsFragment();
    FragmentTestHost.launch(fragment);
    assertEquals("", text(fragment, R.id.fragment_transfer_detail_details_storage_path));
    assertEquals("", text(fragment, R.id.fragment_transfer_detail_details_hash));
    fragment.onTime();
    assertEquals("", text(fragment, R.id.fragment_transfer_detail_details_hash));
  }

  @Test
  public void theTransfersDetailsAreShown() {
    TransferDetailDetailsFragment fragment = launch(download());
    assertEquals(
        "/storage/downloads/A Torrent",
        text(fragment, R.id.fragment_transfer_detail_details_storage_path));
    assertEquals(
        UIUtils.getBytesInHuman(2048),
        text(fragment, R.id.fragment_transfer_detail_details_total_size));
    assertEquals("3", text(fragment, R.id.fragment_transfer_detail_details_files_number));
    assertEquals(HASH, text(fragment, R.id.fragment_transfer_detail_details_hash));
    assertEquals(MAGNET, text(fragment, R.id.fragment_transfer_detail_details_magnet));
    assertEquals("a comment", text(fragment, R.id.fragment_transfer_detail_details_comment));
    assertTrue(text(fragment, R.id.fragment_transfer_detail_details_created_on).contains("2023"));
    assertEquals(
        UIUtils.getBytesInHuman(10240) + "/s",
        text(fragment, R.id.fragment_transfer_detail_details_speed_limit_download));
    assertEquals(
        fragment.getString(R.string.unlimited),
        text(fragment, R.id.fragment_transfer_detail_details_speed_limit_upload));
    // not sequential: its row stays hidden
    assertEquals(
        View.GONE,
        fragment
            .requireView()
            .findViewById(R.id.fragment_transfer_detail_sequential_download_linearlayout)
            .getVisibility());
  }

  @Test
  public void aSequentialTransferShowsItsRowAndATransferWithoutCommentHidesTheCommentRow() {
    UIBittorrentDownload d = download();
    when(d.isSequentialDownloadCached()).thenReturn(true);
    when(d.getCachedTorrentComment()).thenReturn("");
    when(d.getCachedMagnetUri()).thenReturn("");
    when(d.getCachedFileCount()).thenReturn(-1);
    when(d.getCachedDownloadRateLimit()).thenReturn(-1);
    TransferDetailDetailsFragment fragment = launch(d);

    assertEquals(
        View.VISIBLE,
        fragment
            .requireView()
            .findViewById(R.id.fragment_transfer_detail_sequential_download_linearlayout)
            .getVisibility());
    assertEquals(
        View.GONE,
        fragment
            .requireView()
            .findViewById(R.id.fragment_transfer_detail_details_comment_container)
            .getVisibility());
    assertEquals("0", text(fragment, R.id.fragment_transfer_detail_details_files_number));
    assertEquals("", text(fragment, R.id.fragment_transfer_detail_details_magnet));
    assertEquals(
        fragment.getString(R.string.unlimited),
        text(fragment, R.id.fragment_transfer_detail_details_speed_limit_download));
  }

  @Test
  public void theHashAndMagnetCopyToTheClipboard() {
    TransferDetailDetailsFragment fragment = launch(download());
    fragment.requireView().findViewById(R.id.fragment_transfer_detail_details_hash).performClick();
    assertEquals(HASH, clip());

    fragment
        .requireView()
        .findViewById(R.id.fragment_transfer_detail_details_magnet_copy_button)
        .performClick();
    assertEquals(MAGNET, clip());

    fragment
        .requireView()
        .findViewById(R.id.fragment_transfer_detail_details_hash_copy_button)
        .performClick();
    assertEquals(HASH, clip());
  }

  @Test
  public void aMagnetThatIsGoneIsNotCopied() {
    UIBittorrentDownload d = download();
    TransferDetailDetailsFragment fragment = launch(d);
    // the magnet disappears after it was shown: copying it does nothing
    when(d.getCachedMagnetUri()).thenReturn(null);
    ((ClipboardManager)
            ApplicationProvider.getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE))
        .setPrimaryClip(ClipData.newPlainText("x", "before"));
    fragment
        .requireView()
        .findViewById(R.id.fragment_transfer_detail_details_magnet)
        .performClick();
    assertEquals("before", clip());
  }

  @Test
  public void theSpeedLimitViewsAreWiredToTheirDialog() {
    // the dialog reads the current limit from libtorrent, which a unit test has none of: the
    // listener is the one wired to the four views
    TransferDetailDetailsFragment fragment = launch(download());
    assertTrue(
        fragment
            .requireView()
            .findViewById(R.id.fragment_transfer_detail_details_speed_limit_download_arrow)
            .hasOnClickListeners());
    assertTrue(
        fragment
            .requireView()
            .findViewById(R.id.fragment_transfer_detail_details_speed_limit_upload)
            .hasOnClickListeners());
  }

  @Test
  public void theSpeedLimitDialogMovesTheSeekbarAndShowsTheValue() {
    TransferDetailDetailsFragment fragment = new TransferDetailDetailsFragment();
    ActivityController<FragmentTestHost> host = FragmentTestHost.launch(fragment);
    TransferDetailDetailsFragment.SpeedLimitDialog dialog =
        new TransferDetailDetailsFragment.SpeedLimitDialog()
            .init(
                null,
                TransferDetailDetailsFragment.SpeedLimitDialog.Direction.Download,
                new WeakReference<>(fragment));
    android.os.Bundle args = new android.os.Bundle();
    args.putInt("startRange", 1024);
    args.putInt("endRange", 5242880);
    args.putInt("defaultValue", 0);
    args.putBoolean("isByteRate", true);
    args.putBoolean("supportsUnlimited", true);
    args.putInt("unlimitedValue", 0);
    args.putInt("currentValue", 20480);
    dialog.setArguments(args);
    dialog.show(host.get().getSupportFragmentManager(), "SpeedLimitDialog");
    host.get().getSupportFragmentManager().executePendingTransactions();

    android.app.Dialog shown = dialog.getDialog();
    assertNotNull(shown);
    SeekBar seekBar = shown.findViewById(R.id.dialog_preference_seekbar_with_checkbox_seekbar);
    TextView current =
        shown.findViewById(R.id.dialog_preference_seekbar_with_checkbox_current_value_textview);
    CheckBox unlimited =
        shown.findViewById(R.id.dialog_preference_seekbar_with_checkbox_unlimited_checkbox);
    assertEquals(20480, seekBar.getProgress());
    assertEquals(UIUtils.getBytesInHuman(20480) + "/s", current.getText().toString());

    // below the minimum the seekbar goes back to it
    seekBar.setProgress(10);
    assertEquals(1024, seekBar.getProgress());

    // unlimited
    unlimited.performClick();
    assertTrue(unlimited.isChecked());
    assertEquals(fragment.getString(R.string.unlimited), current.getText().toString());
    assertEquals(seekBar.getMax(), seekBar.getProgress());
    unlimited.performClick();
    assertEquals(false, unlimited.isChecked());

    // either button closes it without a transfer to change
    dialog.onDialogClosed(true);
    dialog.onDialogClosed(false);
  }
}
