/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.transfers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.app.Application;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class TorrentFetcherDownloadFailureTest {
  @Test
  public void rejectedMetadataRemainsVisibleAsErrorInsteadOfRemovingTransfer() throws Exception {
    exerciseRejectedMetadata(null);
  }

  @Test
  public void rejectedMetadataDoesNotNotifySelectionListenerAsSuccess() throws Exception {
    TorrentFetcherListener listener = mock(TorrentFetcherListener.class);
    exerciseRejectedMetadata(listener);
    verifyNoInteractions(listener);
  }

  private void exerciseRejectedMetadata(TorrentFetcherListener listener) throws Exception {
    String uri = "https://example.invalid/selected.torrent";
    TorrentDownloadInfo info = mock(TorrentDownloadInfo.class);
    when(info.getTorrentUrl()).thenReturn(uri);
    when(info.getHash()).thenReturn("d6ce91299d15e8dac37591868de2d1fefabd3e16");
    HttpClient client = mock(HttpClient.class);
    byte[] differentTorrent =
        "d4:infod6:lengthi1e4:name1:x12:piece lengthi16384e6:pieces20:00000000000000000000ee"
            .getBytes(StandardCharsets.US_ASCII);
    when(client.getBytes(uri, 30000, null)).thenReturn(differentTorrent);
    TransferManager manager = mock(TransferManager.class);
    List<Runnable> work = new ArrayList<>();
    try (MockedStatic<HttpClientFactory> http = mockStatic(HttpClientFactory.class)) {
      http.when(() -> HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD))
          .thenReturn(client);
      TorrentFetcherDownload transfer =
          new TorrentFetcherDownload(manager, info, listener, work::add);
      work.get(0).run();
      assertEquals(TransferState.ERROR, transfer.getState());
      verifyNoInteractions(manager);
    }
  }
}
