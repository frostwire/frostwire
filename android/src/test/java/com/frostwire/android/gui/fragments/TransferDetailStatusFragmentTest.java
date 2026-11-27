/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.util.SparseArray;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.views.AbstractTransferDetailFragment;
import com.frostwire.transfers.TransferState;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * BTDownload can't be loaded off a device (its static initializer needs libtorrent's native
 * library), so what reads it (the status rows) can't run here; the header every detail tab shows
 * and the tab bookkeeping can.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TransferDetailStatusFragmentTest {

  private static UIBittorrentDownload download(TransferState state) {
    UIBittorrentDownload d = mock(UIBittorrentDownload.class);
    when(d.getInfoHash()).thenReturn("0123456789abcdef0123456789abcdef01234567");
    when(d.getDisplayName()).thenReturn("Some Torrent");
    when(d.getState()).thenReturn(state);
    when(d.getProgress()).thenReturn(42);
    when(d.getBytesSent()).thenReturn(500L);
    when(d.getBytesReceived()).thenReturn(1000L);
    return d;
  }

  private static String text(TransferDetailStatusFragment f, int id) {
    return ((TextView) f.requireView().findViewById(id)).getText().toString();
  }

  @Test
  public void aFreshTabHasEmptyFields() {
    TransferDetailStatusFragment fragment = new TransferDetailStatusFragment();
    FragmentTestHost.launch(fragment);
    assertEquals("", text(fragment, R.id.fragment_transfer_detail_status_completion));
    assertEquals("", text(fragment, R.id.fragment_transfer_detail_status_time_left));
    assertEquals("", text(fragment, R.id.fragment_transfer_detail_status_peers));
    assertEquals("", text(fragment, R.id.fragment_transfer_detail_status_seeding_time));
    // with no transfer a timer tick changes nothing
    fragment.onTime();
    assertEquals("", text(fragment, R.id.fragment_transfer_detail_status_completion));
  }

  @Test
  public void theHeaderShowsTheTransferWhenTheTabResumes() {
    TransferDetailStatusFragment fragment = new TransferDetailStatusFragment();
    ActivityController<FragmentTestHost> controller = FragmentTestHost.launch(fragment);
    fragment.bindToTransfer(download(TransferState.DOWNLOADING));
    controller.pause().resume();

    assertEquals("Some Torrent", text(fragment, R.id.view_transfer_detail_progress_title));
    assertEquals(
        fragment.getString(R.string.azureus_manager_item_downloading),
        text(fragment, R.id.view_transfer_detail_progress_status));
    ProgressBar progress =
        fragment.requireView().findViewById(R.id.view_transfer_detail_progress_progress);
    assertEquals(0, progress.getProgress());
  }

  @Test
  public void bindingTheSameTransferAgainKeepsIt() {
    TransferDetailStatusFragment fragment = new TransferDetailStatusFragment();
    SparseArray<String> titles = new SparseArray<>();
    titles.put(R.string.status, "Status");
    UIBittorrentDownload first = download(TransferState.DOWNLOADING);
    fragment.init(null, titles, first);
    assertEquals("Status", fragment.getTabTitle());
    // a null transfer is ignored
    fragment.bindToTransfer(null);
    fragment.bindToTransfer(first);
    assertEquals("Status", fragment.getTabTitle());
  }

  @Test
  public void theShareRatioIsASentOverReceivedPercentage() {
    UIBittorrentDownload d = download(TransferState.DOWNLOADING);
    assertEquals("50.0%", AbstractTransferDetailFragment.getShareRatio(d));
    when(d.getBytesReceived()).thenReturn(-1L);
    assertEquals("0%", AbstractTransferDetailFragment.getShareRatio(d));
  }
}
