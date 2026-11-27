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
import androidx.recyclerview.widget.RecyclerView;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.TransferUpdateExecutor;
import com.frostwire.android.util.SystemUtils;
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
import org.robolectric.annotation.Config;

/**
 * When the refresh executor is not available (its pool is shut down or full) the transfers list
 * refreshes through the app's downloader thread instead.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TransfersFragmentFallbackTest {
  private MockedStatic<TransferManager> transferManagers;
  private MockedStatic<NetworkManager> networkManagers;
  private MockedStatic<ConfigurationManager> configurations;
  private MockedStatic<SystemUtils> utils;
  private final List<Transfer> transfers = new ArrayList<>();

  @Before
  public void setUp() {
    TransferManager transferManager = mock(TransferManager.class);
    ConfigurationManager configuration = mock(ConfigurationManager.class);
    transferManagers = mockStatic(TransferManager.class);
    networkManagers = mockStatic(NetworkManager.class);
    configurations = mockStatic(ConfigurationManager.class);
    utils = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    transferManagers.when(TransferManager::instance).thenReturn(transferManager);
    networkManagers.when(NetworkManager::instance).thenReturn(mock(NetworkManager.class));
    configurations.when(ConfigurationManager::instance).thenReturn(configuration);
    when(transferManager.getTransfers()).thenReturn(transfers);
    // the downloader thread's work runs right away, where the mocks are
    utils
        .when(() -> SystemUtils.postToHandler(any(), any()))
        .thenAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(1)).run();
              return null;
            });
  }

  @After
  public void tearDown() {
    utils.close();
    configurations.close();
    networkManagers.close();
    transferManagers.close();
  }

  private static Transfer transfer(String name, TransferState state, long created) {
    Transfer t = mock(Transfer.class);
    when(t.getDisplayName()).thenReturn(name);
    when(t.getState()).thenReturn(state);
    when(t.getCreated()).thenReturn(new Date(created));
    return t;
  }

  private static int count(TransfersFragment fragment) {
    RecyclerView list = fragment.requireView().findViewById(R.id.fragment_transfers_list);
    return list.getAdapter() == null ? -1 : list.getAdapter().getItemCount();
  }

  @Test
  public void theListRefreshesOnTheDownloaderThreadWhenTheExecutorIsNotAvailable()
      throws Exception {
    transfers.add(transfer("downloading", TransferState.DOWNLOADING, 3));
    transfers.add(transfer("seeding", TransferState.SEEDING, 2));
    TestFragment fragment = new TestFragment();
    FragmentTestHost.launch(fragment);
    shadowOf(Looper.getMainLooper()).idle();
    // the adapter was made by the fallback
    FragmentTestHost.settleUntil(() -> count(fragment) == 2);
    assertEquals(2, count(fragment));

    // and a refresh with a new transfer shows it
    transfers.add(transfer("paused", TransferState.PAUSED, 1));
    fragment.onTime(true);
    shadowOf(Looper.getMainLooper()).idle();
    FragmentTestHost.settleUntil(() -> count(fragment) == 3);
    assertEquals(3, count(fragment));
  }

  /** Its executor never accepts work. */
  public static class TestFragment extends TransfersFragment {
    @Override
    boolean isBittorrentEngineStarted() {
      return false;
    }

    @Override
    TransferUpdateExecutor createTransferUpdateExecutor() {
      return new TransferUpdateExecutor() {
        @Override
        public boolean isAvailable() {
          return false;
        }
      };
    }
  }
}
