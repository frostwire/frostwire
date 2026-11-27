/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Application;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.LocalIndex;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The production constructor: one named worker thread does the index writes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class AndroidSharedTorrentIndexerThreadTest {

  @Test
  public void withdrawalsAreWrittenByTheNamedIndexerThread() throws Exception {
    LocalIndex index = mock(LocalIndex.class);
    CountDownLatch deleted = new CountDownLatch(1);
    String[] threadName = new String[1];
    org.mockito.Mockito.doAnswer(
            call -> {
              threadName[0] = Thread.currentThread().getName();
              deleted.countDown();
              return null;
            })
        .when(index)
        .delete("abcd");
    AndroidSharedTorrentIndexer indexer =
        new AndroidSharedTorrentIndexer(index, mock(IdentityKeys.class), () -> true);
    try {
      indexer.withdraw("abcd", () -> true);
      assertTrue(deleted.await(5, TimeUnit.SECONDS));
      assertEquals("AndroidShareIndexer", threadName[0]);
    } finally {
      indexer.close();
    }
  }

  @Test
  public void aWithdrawalIsDroppedWhenTheTransferCameBack() {
    LocalIndex index = mock(LocalIndex.class);
    AndroidSharedTorrentIndexer indexer =
        new AndroidSharedTorrentIndexer(index, mock(IdentityKeys.class), () -> true);
    try {
      indexer.withdraw("came-back", () -> false);
      indexer.withdraw("marker", () -> true);
      verify(index, timeout(5000)).delete("marker");
      verify(index, never()).delete("came-back");
    } finally {
      indexer.close();
    }
  }

  @Test
  public void engineLifecycleCallbacksNeedNoEngine() {
    LocalIndex index = mock(LocalIndex.class);
    AndroidSharedTorrentIndexer indexer =
        new AndroidSharedTorrentIndexer(index, mock(IdentityKeys.class), () -> false);
    indexer.started(null);
    indexer.stopped(null);
    indexer.downloadAdded(null, null);
    indexer.downloadUpdate(null, null);
    indexer.close();
    org.mockito.Mockito.verifyNoInteractions(index);
  }
}
