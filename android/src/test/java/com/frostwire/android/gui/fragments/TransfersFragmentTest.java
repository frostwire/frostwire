/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.view.View;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.transfers.Transfer;
import com.frostwire.transfers.TransferState;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TransfersFragmentTest {
  private MockedStatic<TransferManager> transferManagers;
  private MockedStatic<NetworkManager> networkManagers;
  private MockedStatic<ConfigurationManager> configurations;
  private TransferManager transferManager;
  private NetworkManager networkManager;
  private ConfigurationManager configuration;
  private final List<Transfer> transfers = new ArrayList<>();

  @Before
  public void setUp() {
    transferManager = mock(TransferManager.class);
    networkManager = mock(NetworkManager.class);
    configuration = mock(ConfigurationManager.class);
    transferManagers = mockStatic(TransferManager.class);
    networkManagers = mockStatic(NetworkManager.class);
    configurations = mockStatic(ConfigurationManager.class);
    transferManagers.when(TransferManager::instance).thenReturn(transferManager);
    networkManagers.when(NetworkManager::instance).thenReturn(networkManager);
    configurations.when(ConfigurationManager::instance).thenReturn(configuration);
    when(transferManager.getTransfers()).thenReturn(transfers);
    when(configuration.isSeedFinishedTorrents()).thenReturn(true);
  }

  @After
  public void tearDown() {
    transferManagers.close();
    networkManagers.close();
    configurations.close();
  }

  private static Transfer transfer(TransferState state, long created) {
    Transfer t = mock(Transfer.class);
    when(t.getState()).thenReturn(state);
    when(t.getCreated()).thenReturn(new Date(created));
    return t;
  }

  private static String text(TransfersFragment f, int id) {
    return ((TextView) f.requireView().findViewById(id)).getText().toString();
  }

  @Test
  public void theHeaderIsTitledTransfers() {
    TransfersFragment fragment = new TestTransfersFragment();
    ActivityController<FragmentTestHost> host = FragmentTestHost.launch(fragment);
    View header = fragment.getHeader(host.get());
    assertEquals(
        fragment.getString(R.string.transfers),
        ((TextView) header.findViewById(R.id.view_transfers_header_text_title))
            .getText()
            .toString());
  }

  @Test
  public void startsOnTheAllTabWithNoNoSeedsNotice() {
    TransfersFragment fragment = new TestTransfersFragment();
    FragmentTestHost.launch(fragment);
    assertEquals(
        0,
        ((com.google.android.material.tabs.TabLayout)
                fragment.requireView().findViewById(R.id.fragment_transfers_layout_tab_layout))
            .getSelectedTabPosition());
    assertEquals(
        View.GONE,
        fragment.requireView().findViewById(R.id.fragment_transfers_no_seeds_view).getVisibility());
  }

  /** libtorrent's native library isn't loadable here: the engine is "not started". */
  public static class TestTransfersFragment extends TransfersFragment {
    volatile boolean engineStarted;

    @Override
    boolean isBittorrentEngineStarted() {
      return engineStarted;
    }
  }
}
