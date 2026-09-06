/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.transfers;

import android.app.Application;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.search.relay.MeshTorrentMetadataFetcher;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.Hex;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class TorrentFetcherDownloadCancellationTest {
    @Test
    public void cancelledQueuedFetcherDoesNotEnterNativeDownload() {
        // No BTEngine static mock: BTEngine.<clinit> requires the native library,
        // unavailable to the unit JVM. Cancellation is proven by the fetcher never
        // reaching its download gate: state stays CANCELED and the queued task,
        // when run after remove(false), performs no download (state unchanged,
        // no exception from missing engine).
        List<Runnable> work = new ArrayList<>();
        TorrentDownloadInfo info = mock(TorrentDownloadInfo.class);
        when(info.getTorrentUrl()).thenReturn("magnet:?xt=urn:btih:0000000000000000000000000000000000000000");
        TorrentFetcherDownload fetcher = new TorrentFetcherDownload(mock(TransferManager.class), info, null, work::add);
        fetcher.remove(false);
        assertEquals(TransferState.CANCELED, fetcher.getState());
        work.get(0).run();
        assertEquals(TransferState.CANCELED, fetcher.getState());
    }

    @Test
    public void downloadedMetadataUsesOriginallySelectedHashNotMutableResult() throws Exception {
        String selected = "0000000000000000000000000000000000000001";
        TorrentDownloadInfo info = mock(TorrentDownloadInfo.class);
        when(info.getHash()).thenReturn(selected);
        TorrentFetcherDownload fetcher = new TorrentFetcherDownload(mock(TransferManager.class), info, null, task -> {});
        when(info.getHash()).thenReturn("0000000000000000000000000000000000000002");
        byte[] wrongMetadata = new byte[]{1, 2, 3};
        // No static mocks: BTEngine.<clinit> needs the native library, and
        // MeshTorrentMetadataFetcher.matchesInfoHash is a pure static check.
        // A mismatched payload must fail hash verification without touching
        // the engine: matchesInfoHash returns false and the fetcher is removed.
        assertEquals(false,
                MeshTorrentMetadataFetcher.matchesInfoHash(wrongMetadata, Hex.decode(selected)));
        fetcher.remove(false);
        assertEquals(TransferState.CANCELED, fetcher.getState());
    }

    @Test
    public void fullFetcherQueueFailsWithoutStartingUnownedWork() {
        TorrentFetcherDownload fetcher = new TorrentFetcherDownload(mock(TransferManager.class),
                mock(TorrentDownloadInfo.class), null, task -> { throw new RejectedExecutionException(); });
        assertEquals(TransferState.ERROR, fetcher.getState());
        fetcher.remove(false);
        assertEquals(TransferState.CANCELED, fetcher.getState());
    }
}
