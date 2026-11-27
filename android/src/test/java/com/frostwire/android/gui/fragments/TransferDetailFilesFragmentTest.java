/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.transfers.TransferItem;
import com.frostwire.transfers.TransferState;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TransferDetailFilesFragmentTest {
  private static final String HASH = "0123456789abcdef0123456789abcdef01234567";
  private static final String OTHER_HASH = "89abcdef0123456789abcdef0123456789abcdef";

  private MockedStatic<SystemUtils> utils;
  private int openJobs;

  @Before
  public void setUp() {
    utils = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    // background work (including the files' rows') runs right away, except the downloader's: the
    // tab base class looks the torrent handle up there, which needs libtorrent
    utils
        .when(() -> SystemUtils.postToHandler(any(), any()))
        .thenAnswer(
            invocation -> {
              if (invocation.getArgument(0) != SystemUtils.HandlerThreadName.DOWNLOADER) {
                ((Runnable) invocation.getArgument(1)).run();
              }
              openJobs++;
              return null;
            });
    allowBackgroundWorkOnTheTestThread(utils);
  }

  // (the background half of the rows runs on the test thread, which is the UI thread)
  private static void allowBackgroundWorkOnTheTestThread(MockedStatic<SystemUtils> utils) {
    utils
        .when(() -> SystemUtils.ensureBackgroundThreadOrCrash(any(String.class)))
        .thenAnswer(invocation -> null);
  }

  @After
  public void tearDown() {
    utils.close();
  }

  private static TransferItem item(String name, int progress, boolean complete) {
    TransferItem item = mock(TransferItem.class);
    when(item.getName()).thenReturn(name);
    when(item.getFile()).thenReturn(new File("/storage/downloads/" + name));
    when(item.getSize()).thenReturn(2048L);
    when(item.getDownloaded()).thenReturn(1024L);
    when(item.getProgress()).thenReturn(progress);
    when(item.isComplete()).thenReturn(complete);
    return item;
  }

  private static UIBittorrentDownload download(String hash, List<TransferItem> items) {
    UIBittorrentDownload d = mock(UIBittorrentDownload.class);
    when(d.getInfoHash()).thenReturn(hash);
    when(d.getDisplayName()).thenReturn("A Torrent");
    when(d.getState()).thenReturn(TransferState.DOWNLOADING);
    when(d.getSize()).thenReturn(4096L);
    when(d.getItems()).thenReturn(items);
    return d;
  }

  private static RecyclerView recycler(TransferDetailFilesFragment f) {
    return f.requireView().findViewById(R.id.fragment_transfer_detail_files_recycler_view);
  }

  private static String text(TransferDetailFilesFragment f, int id) {
    return ((TextView) f.requireView().findViewById(id)).getText().toString();
  }

  private static void layout(RecyclerView list) {
    list.measure(
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY));
    list.layout(0, 0, 1080, 4000);
  }

  private static TransferDetailFilesFragment launch() {
    TransferDetailFilesFragment fragment = new TransferDetailFilesFragment();
    FragmentTestHost.launch(fragment);
    return fragment;
  }

  private static int count(TransferDetailFilesFragment f) {
    RecyclerView.Adapter<?> adapter = recycler(f).getAdapter();
    return adapter == null ? -1 : adapter.getItemCount();
  }

  @Test
  public void aFreshTabHasNoFiles() {
    TransferDetailFilesFragment fragment = launch();
    assertEquals("", text(fragment, R.id.fragment_transfer_detail_files_file_number));
    assertEquals("", text(fragment, R.id.fragment_transfer_detail_files_size_all));
    // and nothing to refresh
    fragment.onTime();
    assertEquals(-1, count(fragment));
  }

  @Test
  public void bindingATransferLoadsItsFilesSortedByProgress() throws Exception {
    List<TransferItem> items = new ArrayList<>();
    items.add(item("low.mp3", 10, false));
    items.add(item("done.pdf", 100, true));
    items.add(item("mid.txt", 50, false));
    TransferDetailFilesFragment fragment = launch();
    fragment.bindToTransfer(download(HASH, items));
    shadowOf(Looper.getMainLooper()).idle();
    FragmentTestHost.settleUntil(() -> count(fragment) == 3);

    assertEquals(3, count(fragment));
    assertEquals(
        fragment.getString(R.string.n_files, 3),
        text(fragment, R.id.fragment_transfer_detail_files_file_number));
    assertEquals(
        UIUtils.getBytesInHuman(4096),
        text(fragment, R.id.fragment_transfer_detail_files_size_all));

    // the rows show the most progressed file first
    layout(recycler(fragment));
    assertEquals(3, recycler(fragment).getChildCount());
    View first = recycler(fragment).getChildAt(0);
    assertEquals(
        "done.pdf",
        ((TextView) first.findViewById(R.id.fragment_transfer_detail_files_file_name))
            .getText()
            .toString());
  }

  @Test
  public void aRowShowsItsProgressAndSize() throws Exception {
    List<TransferItem> items = new ArrayList<>();
    items.add(item("half.txt", 50, false));
    TransferDetailFilesFragment fragment = launch();
    fragment.bindToTransfer(download(HASH, items));
    shadowOf(Looper.getMainLooper()).idle();
    FragmentTestHost.settleUntil(() -> count(fragment) == 1);
    layout(recycler(fragment));
    shadowOf(Looper.getMainLooper()).idle();

    View row = recycler(fragment).getChildAt(0);
    assertEquals(
        "50%",
        ((TextView) row.findViewById(R.id.fragment_transfer_detail_files_file_percentage))
            .getText()
            .toString());
    assertEquals(
        UIUtils.getBytesInHuman(1024) + "/" + UIUtils.getBytesInHuman(2048),
        ((TextView) row.findViewById(R.id.fragment_transfer_detail_files_file_download_size))
            .getText()
            .toString());
    assertEquals(
        50,
        ((ProgressBar) row.findViewById(R.id.fragment_transfer_detail_files_file_progressbar))
            .getProgress());
    // not complete and with no bytes to preview: no play button
    assertEquals(
        View.GONE,
        row.findViewById(R.id.fragment_transfer_detail_files_file_play_icon).getVisibility());
  }

  @Test
  public void aCompleteFileOffersToOpenItAndTapsOpenIt() throws Exception {
    List<TransferItem> items = new ArrayList<>();
    items.add(item("report.pdf", 100, true));
    TransferDetailFilesFragment fragment = launch();
    fragment.bindToTransfer(download(HASH, items));
    shadowOf(Looper.getMainLooper()).idle();
    FragmentTestHost.settleUntil(() -> count(fragment) == 1);
    layout(recycler(fragment));
    shadowOf(Looper.getMainLooper()).idle();

    View play =
        recycler(fragment)
            .getChildAt(0)
            .findViewById(R.id.fragment_transfer_detail_files_file_play_icon);
    assertEquals(View.VISIBLE, play.getVisibility());
    int before = openJobs;
    play.performClick();
    shadowOf(Looper.getMainLooper()).idle();
    // a tap posts one job to open the file
    assertEquals(before + 1, openJobs);
  }

  @Test
  public void aTimerTickWithNewItemsReplacesTheList() throws Exception {
    List<TransferItem> items = new ArrayList<>();
    items.add(item("a.txt", 10, false));
    UIBittorrentDownload d = download(HASH, items);
    when(d.peekItems()).thenReturn(null);
    TransferDetailFilesFragment fragment = launch();
    fragment.bindToTransfer(d);
    shadowOf(Looper.getMainLooper()).idle();
    FragmentTestHost.settleUntil(() -> count(fragment) == 1);

    // the same transfer, now cached with one more file
    List<TransferItem> more = new ArrayList<>(items);
    more.add(item("b.txt", 20, false));
    when(d.peekItems()).thenReturn(more);
    fragment.onTime();
    shadowOf(Looper.getMainLooper()).idle();
    FragmentTestHost.settleUntil(() -> count(fragment) == 2);
    assertEquals(2, count(fragment));
    assertEquals(
        fragment.getString(R.string.n_files, 2),
        text(fragment, R.id.fragment_transfer_detail_files_file_number));
  }

  @Test
  public void anotherTransferStartsAFreshList() throws Exception {
    List<TransferItem> first = new ArrayList<>();
    first.add(item("a.txt", 10, false));
    TransferDetailFilesFragment fragment = launch();
    fragment.bindToTransfer(download(HASH, first));
    shadowOf(Looper.getMainLooper()).idle();
    FragmentTestHost.settleUntil(() -> count(fragment) == 1);

    List<TransferItem> second = new ArrayList<>();
    second.add(item("x.txt", 1, false));
    second.add(item("y.txt", 2, false));
    second.add(item("z.txt", 3, false));
    fragment.bindToTransfer(download(OTHER_HASH, second));
    shadowOf(Looper.getMainLooper()).idle();
    FragmentTestHost.settleUntil(() -> count(fragment) == 3);
    assertEquals(3, count(fragment));
  }

  @Test
  public void aHugeTorrentListsOnlyItsFirstPageAtFirst() throws Exception {
    List<TransferItem> items = new ArrayList<>();
    for (int i = 0; i < 1200; i++) {
      items.add(item("file" + i + ".txt", i % 100, false));
    }
    TransferDetailFilesFragment fragment = launch();
    fragment.bindToTransfer(download(HASH, items));
    shadowOf(Looper.getMainLooper()).idle();
    FragmentTestHost.settleUntil(() -> count(fragment) == 500);
    assertEquals(500, count(fragment));
    // the count line still says how many files the torrent has
    assertEquals(
        fragment.getString(R.string.n_files, 1200),
        text(fragment, R.id.fragment_transfer_detail_files_file_number));
  }
}
