/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.transfers;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.frostwire.transfers.Transfer;
import java.util.Arrays;
import org.junit.Test;
import org.mockito.MockedStatic;

/** Files to ignore while scanning: only torrent transfers contribute any. */
public class TransfersTest {

  @Test
  public void transfersThatAreNotTorrentsContributeNoIgnorableFiles() {
    TransferManager manager = mock(TransferManager.class);
    Transfer http = mock(Transfer.class);
    Transfer other = mock(Transfer.class);
    when(manager.getTransfers()).thenReturn(Arrays.asList(http, other));
    try (MockedStatic<TransferManager> statics = mockStatic(TransferManager.class)) {
      statics.when(TransferManager::instance).thenReturn(manager);
      assertTrue(Transfers.getSkipedFiles().isEmpty());
      assertTrue(Transfers.getIncompleteFiles().isEmpty());
      assertTrue(Transfers.getIgnorableFiles().isEmpty());
    }
  }

  @Test
  public void noTransfersMeansNothingToIgnore() {
    TransferManager manager = mock(TransferManager.class);
    when(manager.getTransfers()).thenReturn(java.util.Collections.emptyList());
    try (MockedStatic<TransferManager> statics = mockStatic(TransferManager.class)) {
      statics.when(TransferManager::instance).thenReturn(manager);
      assertTrue(Transfers.getIgnorableFiles().isEmpty());
    }
  }
}
