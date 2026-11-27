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
import android.app.Dialog;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.RealTransfer;
import com.frostwire.jlibtorrent.AnnounceEntry;
import com.frostwire.jlibtorrent.TorrentHandle;
import java.util.ArrayList;
import java.util.List;
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
 * The trackers tab over a real transfer: the trackers come from the torrent's handle, and adding,
 * editing and removing one changes the trackers of the real torrent.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TransferDetailTrackersFragmentRealTest {

  private static final String FIRST = "http://127.0.0.1:9/announce";
  private static final String SECOND = "udp://127.0.0.1:9/announce";

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private RealTransfer transfer;

  @After
  public void tearDown() {
    if (transfer != null) {
      transfer.close();
    }
  }

  private TransferDetailTrackersFragment open(String... trackers) throws Exception {
    transfer = RealTransfer.start(tmp.newFolder("root").toPath(), null, false);
    TorrentHandle th = transfer.dl.getTorrentHandle();
    for (String tracker : trackers) {
      th.addTracker(new AnnounceEntry(tracker));
    }
    TransferDetailTrackersFragment fragment = new TransferDetailTrackersFragment();
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

  private static RecyclerView list(TransferDetailTrackersFragment f) {
    return f.requireView()
        .findViewById(R.id.fragment_transfer_detail_trackers_address_recyclerview);
  }

  private static void layout(RecyclerView list) {
    list.measure(
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY));
    list.layout(0, 0, 1080, 4000);
  }

  private static int trackerCount(TransferDetailTrackersFragment f) {
    return list(f).getAdapter() == null ? -1 : list(f).getAdapter().getItemCount();
  }

  private List<String> trackersOfTheTorrent() {
    List<String> urls = new ArrayList<>();
    for (AnnounceEntry entry : transfer.dl.getTorrentHandle().trackers()) {
      urls.add(entry.url());
    }
    // libtorrent keeps them in its own order
    java.util.Collections.sort(urls);
    return urls;
  }

  private static String rowAddress(TransferDetailTrackersFragment f, int index) {
    layout(list(f));
    return ((TextView)
            list(f).getChildAt(index).findViewById(R.id.view_transfer_detail_tracker_address))
        .getText()
        .toString();
  }

  private static View rowOf(TransferDetailTrackersFragment f, String url) {
    layout(list(f));
    RecyclerView list = list(f);
    for (int i = 0; i < list.getChildCount(); i++) {
      View row = list.getChildAt(i);
      TextView address = row.findViewById(R.id.view_transfer_detail_tracker_address);
      if (url.equals(address.getText().toString())) {
        return row;
      }
    }
    throw new AssertionError("no row for " + url);
  }

  private static Dialog shownDialog() throws Exception {
    settle();
    Dialog dialog = ShadowDialog.getLatestDialog();
    assertTrue("a dialog is showing", dialog != null && dialog.isShowing());
    return dialog;
  }

  private void waitForTrackers(TransferDetailTrackersFragment f, List<String> expected)
      throws Exception {
    List<String> sorted = new ArrayList<>(expected);
    java.util.Collections.sort(sorted);
    for (int i = 0; i < 100 && !trackersOfTheTorrent().equals(sorted); i++) {
      Thread.sleep(50);
    }
    assertEquals(sorted, trackersOfTheTorrent());
    FragmentTestHost.settleUntil(() -> trackerCount(f) == expected.size());
  }

  @Test
  public void theTrackersOfTheTorrentAreListedAndDhtAndLsdAreReported() throws Exception {
    TransferDetailTrackersFragment fragment = open(FIRST, SECOND);

    FragmentTestHost.settleUntil(() -> trackerCount(fragment) == 2);
    assertEquals(2, trackerCount(fragment));
    List<String> shown = List.of(rowAddress(fragment, 0), rowAddress(fragment, 1));
    assertTrue(shown.toString(), shown.containsAll(List.of(FIRST, SECOND)));
    // the session is local only: no DHT and no local discovery
    // (what libtorrent says about this torrent, which the tab just passes on)
    String dht =
        fragment.getString(transfer.dl.isAnnouncingToDht() ? R.string.working : R.string.disabled);
    String lsd =
        fragment.getString(transfer.dl.isAnnouncingToLsd() ? R.string.working : R.string.disabled);
    assertEquals(
        dht,
        ((TextView)
                fragment
                    .requireView()
                    .findViewById(R.id.fragment_transfer_detail_trackers_dht_status))
            .getText()
            .toString());
    assertEquals(
        lsd,
        ((TextView)
                fragment
                    .requireView()
                    .findViewById(R.id.fragment_transfer_detail_trackers_lsd_status))
            .getText()
            .toString());
    // every timer tick reads the trackers again
    fragment.onTime();
    settle();
    assertEquals(2, trackerCount(fragment));
  }

  @Test
  public void aValidTrackerTheUserTypesIsAddedToTheTorrent() throws Exception {
    TransferDetailTrackersFragment fragment = open(FIRST);
    FragmentTestHost.settleUntil(() -> trackerCount(fragment) == 1);

    fragment
        .requireView()
        .findViewById(R.id.fragment_transfer_detail_trackers_add_tracker_button)
        .performClick();
    Dialog dialog = shownDialog();
    ((EditText) dialog.findViewById(R.id.dialog_default_input_text)).setText("  " + SECOND + "  ");
    dialog.findViewById(R.id.dialog_default_input_button_yes).performClick();

    waitForTrackers(fragment, List.of(FIRST, SECOND));
  }

  @Test
  public void anInvalidTrackerIsRefusedAndACancelledDialogChangesNothing() throws Exception {
    TransferDetailTrackersFragment fragment = open(FIRST);
    FragmentTestHost.settleUntil(() -> trackerCount(fragment) == 1);
    View add =
        fragment
            .requireView()
            .findViewById(R.id.fragment_transfer_detail_trackers_add_tracker_button);

    add.performClick();
    Dialog dialog = shownDialog();
    ((EditText) dialog.findViewById(R.id.dialog_default_input_text)).setText("not a tracker");
    dialog.findViewById(R.id.dialog_default_input_button_yes).performClick();
    settle();
    assertEquals(List.of(FIRST), trackersOfTheTorrent());

    add.performClick();
    dialog = shownDialog();
    ((EditText) dialog.findViewById(R.id.dialog_default_input_text)).setText(SECOND);
    dialog.findViewById(R.id.dialog_default_input_button_no).performClick();
    settle();
    assertEquals(List.of(FIRST), trackersOfTheTorrent());
  }

  @Test
  public void editingATrackerReplacesItInTheTorrent() throws Exception {
    TransferDetailTrackersFragment fragment = open(FIRST, SECOND);
    FragmentTestHost.settleUntil(() -> trackerCount(fragment) == 2);
    layout(list(fragment));

    rowOf(fragment, FIRST)
        .findViewById(R.id.view_transfer_detail_tracker_edit_button)
        .performClick();
    Dialog dialog = shownDialog();
    EditText input = dialog.findViewById(R.id.dialog_default_input_text);
    assertEquals(FIRST, input.getText().toString());
    input.setText("http://127.0.0.1:10/announce");
    dialog.findViewById(R.id.dialog_default_input_button_yes).performClick();

    waitForTrackers(fragment, List.of("http://127.0.0.1:10/announce", SECOND));
  }

  @Test
  public void editingATrackerToAnInvalidUrlKeepsIt() throws Exception {
    TransferDetailTrackersFragment fragment = open(FIRST);
    FragmentTestHost.settleUntil(() -> trackerCount(fragment) == 1);
    layout(list(fragment));

    rowOf(fragment, FIRST)
        .findViewById(R.id.view_transfer_detail_tracker_edit_button)
        .performClick();
    Dialog dialog = shownDialog();
    ((EditText) dialog.findViewById(R.id.dialog_default_input_text)).setText("ftp://nope");
    dialog.findViewById(R.id.dialog_default_input_button_yes).performClick();
    settle();

    assertEquals(List.of(FIRST), trackersOfTheTorrent());
  }

  @Test
  public void removingATrackerAsksFirstAndThenTakesItOutOfTheTorrent() throws Exception {
    TransferDetailTrackersFragment fragment = open(FIRST, SECOND);
    FragmentTestHost.settleUntil(() -> trackerCount(fragment) == 2);
    layout(list(fragment));

    rowOf(fragment, FIRST)
        .findViewById(R.id.view_transfer_detail_tracker_remove_button)
        .performClick();
    Dialog dialog = shownDialog();
    // saying no leaves the trackers alone
    dialog.findViewById(R.id.dialog_default_button_no).performClick();
    settle();
    assertEquals(List.of(FIRST, SECOND), trackersOfTheTorrent());

    rowOf(fragment, FIRST)
        .findViewById(R.id.view_transfer_detail_tracker_remove_button)
        .performClick();
    dialog = shownDialog();
    dialog.findViewById(R.id.dialog_default_button_yes).performClick();

    waitForTrackers(fragment, List.of(SECOND));
    assertFalse(trackersOfTheTorrent().contains(FIRST));
  }
}
