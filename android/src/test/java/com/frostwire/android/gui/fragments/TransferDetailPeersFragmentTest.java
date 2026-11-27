/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Looper;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.OffMainQuery;
import com.frostwire.transfers.TransferState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * The peers are read from libtorrent off the main thread, which a unit test can't load: the query
 * that reads them is replaced by one that answers with the test's peers.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@SuppressWarnings({"unchecked", "rawtypes"})
public class TransferDetailPeersFragmentTest {
  private MockedStatic<OffMainQuery> queries;
  private final List<Object> peers = new ArrayList<>();
  private int queriesRun;

  @Before
  public void setUp() {
    OffMainQuery query = mock(OffMainQuery.class);
    doAnswer(
            invocation -> {
              queriesRun++;
              ((Consumer) invocation.getArgument(1)).accept(new ArrayList<>(peers));
              return null;
            })
        .when(query)
        .run(any(Supplier.class), any(Consumer.class));
    queries = mockStatic(OffMainQuery.class);
    queries.when(() -> OffMainQuery.on(any())).thenReturn(query);
  }

  @After
  public void tearDown() {
    queries.close();
  }

  private static UIBittorrentDownload download() {
    UIBittorrentDownload d = mock(UIBittorrentDownload.class);
    when(d.getInfoHash()).thenReturn("0123456789abcdef0123456789abcdef01234567");
    when(d.getDisplayName()).thenReturn("A Torrent");
    when(d.getState()).thenReturn(TransferState.DOWNLOADING);
    return d;
  }

  private static RecyclerView list(TransferDetailPeersFragment f) {
    return f.requireView().findViewById(R.id.fragment_transfer_detail_peers_recyclerview);
  }

  @Test
  public void withoutATransferNothingIsShown() {
    TransferDetailPeersFragment fragment = new TransferDetailPeersFragment();
    FragmentTestHost.launch(fragment);
    fragment.onTime();
    assertEquals(0, queriesRun);
  }

  @Test
  public void aTransferWithNoPeersShowsZeroPeers() throws Exception {
    TransferDetailPeersFragment fragment = new TransferDetailPeersFragment();
    ActivityController<FragmentTestHost> host = FragmentTestHost.launch(fragment);
    fragment.bindToTransfer(download());
    host.pause().resume();
    shadowOf(Looper.getMainLooper()).idle();
    FragmentTestHost.settleUntil(() -> queriesRun > 0);

    assertEquals(
        fragment.getString(R.string.n_peers, 0),
        ((TextView) fragment.requireView().findViewById(R.id.fragment_transfer_detail_peers_number))
            .getText()
            .toString());
    assertEquals(0, list(fragment).getAdapter().getItemCount());
    // every timer tick reads the peers again
    int before = queriesRun;
    fragment.onTime();
    assertEquals(before + 1, queriesRun);
  }
}
