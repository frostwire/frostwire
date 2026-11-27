/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.transfers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import com.frostwire.android.gui.AndroidSearchEngines;
import com.frostwire.search.relay.MeshTorrentMetadataFetcher;
import com.frostwire.search.relay.RelaySearchWiring;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The metadata-fetch placeholder: what it reports, and what its fetch task does per link kind. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class TorrentFetcherDownloadBehaviorTest {
  private static final String HASH = "0123456789abcdef0123456789abcdef01234567";

  private static TorrentDownloadInfo info(String url) {
    TorrentDownloadInfo info = mock(TorrentDownloadInfo.class);
    when(info.getTorrentUrl()).thenReturn(url);
    return info;
  }

  private static TorrentFetcherDownload queued(
      TorrentDownloadInfo info, TorrentFetcherListener listener, List<Runnable> work) {
    return new TorrentFetcherDownload(mock(TransferManager.class), info, listener, work::add);
  }

  @Test
  public void aMetadataFetchReportsItsInfoAndNothingElse() {
    TorrentDownloadInfo info = info("https://example.invalid/a.torrent");
    when(info.getDisplayName()).thenReturn("A torrent");
    when(info.getDetailsUrl()).thenReturn("https://example.invalid/details");
    when(info.getSize()).thenReturn(99L);
    when(info.getHash()).thenReturn(HASH);
    when(info.makeMagnetUri()).thenReturn("magnet:?xt=urn:btih:" + HASH);
    TorrentFetcherDownload fetch = queued(info, null, new ArrayList<>());
    assertEquals("A torrent", fetch.getDisplayName());
    assertEquals("A torrent", fetch.getName());
    assertEquals(99, fetch.getSize());
    assertEquals(HASH, fetch.getInfoHash());
    assertEquals("magnet:?xt=urn:btih:" + HASH, fetch.magnetUri());
    assertEquals("https://example.invalid/a.torrent", fetch.getTorrentUri());
    assertEquals(info, fetch.getTorrentDownloadInfo());
    assertNull(fetch.getTorrentFetcherListener());
    assertNotNull(fetch.getCreated());
    assertEquals(TransferState.DOWNLOADING_TORRENT, fetch.getState());
    assertTrue(fetch.isDownloading());
    assertFalse(fetch.isComplete());
    assertFalse(fetch.isSeeding());
    assertFalse(fetch.isPaused());
    assertFalse(fetch.isFinished());
    assertTrue(fetch.getItems().isEmpty());
    assertNull(fetch.getSavePath());
    assertNull(fetch.getContentSavePath());
    assertNull(fetch.previewFile());
    assertEquals("torrent", fetch.getPredominantFileExtension());
    assertEquals("", fetch.getPeers());
    assertEquals("", fetch.getSeeds());
    assertEquals(0, fetch.getProgress());
    assertEquals(0, fetch.getBytesReceived());
    assertEquals(0, fetch.getBytesSent());
    assertEquals(0, fetch.getDownloadSpeed());
    assertEquals(0, fetch.getUploadSpeed());
    assertEquals(0, fetch.getETA());
    assertEquals(0, fetch.getConnectedPeers());
    assertEquals(0, fetch.getTotalPeers());
    assertEquals(0, fetch.getConnectedSeeds());
    assertEquals(0, fetch.getTotalSeeds());
    fetch.pause();
    fetch.resume();
  }

  @Test
  public void aNamelessFetchIsNamedByItsDetailsPage() {
    TorrentDownloadInfo info = info("https://example.invalid/a.torrent");
    when(info.getDisplayName()).thenReturn("");
    when(info.getDetailsUrl()).thenReturn("https://example.invalid/details");
    assertEquals(
        "https://example.invalid/details", queued(info, null, new ArrayList<>()).getName());
  }

  @Test
  public void fetchesAreEqualWhenTheirLinksMatchIgnoringCase() {
    TorrentFetcherDownload a =
        queued(info("HTTPS://Example.invalid/A.torrent"), null, new ArrayList<>());
    TorrentFetcherDownload b =
        queued(info("https://example.invalid/a.TORRENT"), null, new ArrayList<>());
    TorrentFetcherDownload c =
        queued(info("https://example.invalid/c.torrent"), null, new ArrayList<>());
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, c);
    assertNotEquals(a, "a string");
    TorrentFetcherDownload noUrl = queued(info(null), null, new ArrayList<>());
    assertEquals(0, noUrl.hashCode());
  }

  @Test
  public void aFullFetchQueueMakesTheTransferAnError() {
    TorrentFetcherDownload fetch =
        new TorrentFetcherDownload(
            mock(TransferManager.class),
            info("https://example.invalid/a.torrent"),
            null,
            task -> {
              throw new RejectedExecutionException("full");
            });
    assertEquals(TransferState.ERROR, fetch.getState());
  }

  @Test
  public void metadataFetchedOverHttpIsHandedToTheListener() throws Exception {
    List<Runnable> work = new ArrayList<>();
    TorrentFetcherListener listener = mock(TorrentFetcherListener.class);
    HttpClient client = mock(HttpClient.class);
    byte[] data = {1, 2, 3};
    when(client.getBytes("https://example.invalid/a.torrent", 30000, "https://ref.invalid/"))
        .thenReturn(data);
    TorrentDownloadInfo info = info("https://example.invalid/a.torrent");
    when(info.getReferrerUrl()).thenReturn("https://ref.invalid/");
    TorrentFetcherDownload fetch = queued(info, listener, work);
    try (MockedStatic<HttpClientFactory> http = mockStatic(HttpClientFactory.class)) {
      http.when(() -> HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD))
          .thenReturn(client);
      work.get(0).run();
    }
    verify(listener)
        .onTorrentInfoFetched(eq(data), eq("https://example.invalid/a.torrent"), eq(fetch.tokenId));
    assertEquals(TransferState.DOWNLOADING_TORRENT, fetch.getState());
  }

  @Test
  public void aFetchThatReturnsNothingOrFailsEndsInError() throws Exception {
    HttpClient client = mock(HttpClient.class);
    when(client.getBytes(any(), org.mockito.ArgumentMatchers.anyInt(), any())).thenReturn(null);
    List<Runnable> work = new ArrayList<>();
    TorrentFetcherDownload empty = queued(info("https://example.invalid/a.torrent"), null, work);
    List<Runnable> work2 = new ArrayList<>();
    TorrentFetcherDownload failing = queued(info("https://example.invalid/b.torrent"), null, work2);
    try (MockedStatic<HttpClientFactory> http = mockStatic(HttpClientFactory.class)) {
      http.when(() -> HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD))
          .thenReturn(client);
      work.get(0).run();
      assertEquals(TransferState.ERROR, empty.getState());
      when(client.getBytes(any(), org.mockito.ArgumentMatchers.anyInt(), any()))
          .thenThrow(new IllegalStateException("offline"));
      work2.get(0).run();
      assertEquals(TransferState.ERROR, failing.getState());
    }
  }

  @Test
  public void metadataThatCannotBeStartedWithoutTheEngineEndsInError() throws Exception {
    // the engine (and bdecode) need the native library: starting fails and the placeholder stays
    // visible as an error instead of vanishing as if it had worked
    HttpClient client = mock(HttpClient.class);
    when(client.getBytes(any(), org.mockito.ArgumentMatchers.anyInt(), any()))
        .thenReturn(new byte[] {1, 2, 3});
    List<Runnable> work = new ArrayList<>();
    TorrentFetcherDownload fetch = queued(info("https://example.invalid/a.torrent"), null, work);
    try (MockedStatic<HttpClientFactory> http = mockStatic(HttpClientFactory.class)) {
      http.when(() -> HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD))
          .thenReturn(client);
      work.get(0).run();
    }
    assertEquals(TransferState.ERROR, fetch.getState());
  }

  @Test
  public void aCanceledFetchDoesNothingWhenItsTaskRuns() {
    List<Runnable> work = new ArrayList<>();
    TorrentFetcherListener listener = mock(TorrentFetcherListener.class);
    TorrentFetcherDownload fetch =
        queued(info("https://example.invalid/a.torrent"), listener, work);
    fetch.remove(true, true);
    assertEquals(TransferState.CANCELED, fetch.getState());
    work.get(0).run();
    verify(listener, never()).onTorrentInfoFetched(any(), any(), anyLong());
  }

  @Test
  public void linksThatAreNeitherHttpNorMagnetAreFetchedByTheEngineAndFailWithoutIt() {
    List<Runnable> work = new ArrayList<>();
    TorrentFetcherDownload fetch = queued(info("test:something"), null, work);
    work.get(0).run();
    assertEquals(TransferState.ERROR, fetch.getState());
  }

  @Test
  public void aMagnetWithoutAHolderIsAddedDirectlyAndFailsWithoutTheEngine() {
    List<Runnable> work = new ArrayList<>();
    TorrentFetcherDownload fetch = queued(info("magnet:?xt=urn:btih:" + HASH), null, work);
    work.get(0).run();
    assertEquals(TransferState.ERROR, fetch.getState());
  }

  private static String holderMagnet() {
    String pub = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    return "magnet:?xt=urn:btih:" + HASH + "&x.hp=" + pub;
  }

  @Test
  public void aMagnetWithAHolderTriesTheMeshFirstThenFallsBackToTheDirectAdd() {
    RelaySearchWiring wiring = mock(RelaySearchWiring.class);
    when(wiring.searchTransport())
        .thenReturn(mock(com.frostwire.search.relay.DistributedSearchTransport.class));
    when(wiring.identity()).thenReturn(mock(com.frostwire.search.relay.IdentityKeys.class));
    List<Runnable> work = new ArrayList<>();
    TorrentDownloadInfo info = info(holderMagnet());
    when(info.getHash()).thenReturn(HASH);
    TorrentFetcherDownload fetch = queued(info, null, work);
    try (MockedStatic<AndroidSearchEngines> engines = mockStatic(AndroidSearchEngines.class);
        MockedStatic<MeshTorrentMetadataFetcher> mesh =
            mockStatic(MeshTorrentMetadataFetcher.class)) {
      engines.when(AndroidSearchEngines::distributed).thenReturn(wiring);
      mesh.when(() -> MeshTorrentMetadataFetcher.fetch(any(), any(), any(), any(), anyLong()))
          .thenReturn(null);
      work.get(0).run();
      mesh.verify(() -> MeshTorrentMetadataFetcher.fetch(any(), any(), any(), any(), anyLong()));
    }
    // the mesh had nothing, the direct add needs the engine
    assertEquals(TransferState.ERROR, fetch.getState());
  }

  @Test
  public void metadataFromTheMeshThatCannotBeStartedLeavesAnErrorPlaceholder() {
    RelaySearchWiring wiring = mock(RelaySearchWiring.class);
    when(wiring.searchTransport())
        .thenReturn(mock(com.frostwire.search.relay.DistributedSearchTransport.class));
    when(wiring.identity()).thenReturn(mock(com.frostwire.search.relay.IdentityKeys.class));
    List<Runnable> work = new ArrayList<>();
    TorrentDownloadInfo info = info(holderMagnet());
    when(info.getHash()).thenReturn(HASH);
    TorrentFetcherDownload fetch = queued(info, null, work);
    try (MockedStatic<AndroidSearchEngines> engines = mockStatic(AndroidSearchEngines.class);
        MockedStatic<MeshTorrentMetadataFetcher> mesh =
            mockStatic(MeshTorrentMetadataFetcher.class)) {
      engines.when(AndroidSearchEngines::distributed).thenReturn(wiring);
      mesh.when(() -> MeshTorrentMetadataFetcher.fetch(any(), any(), any(), any(), anyLong()))
          .thenReturn(new byte[] {1, 2, 3});
      mesh.when(() -> MeshTorrentMetadataFetcher.matchesInfoHash(any(), any())).thenReturn(true);
      work.get(0).run();
    }
    assertEquals(TransferState.ERROR, fetch.getState());
  }

  @Test
  public void aMeshFetchThatThrowsOrIsUnavailableFallsBack() {
    // no relay wiring at all
    RelaySearchWiring unwired = mock(RelaySearchWiring.class);
    List<Runnable> work = new ArrayList<>();
    TorrentDownloadInfo info = info(holderMagnet());
    when(info.getHash()).thenReturn(HASH);
    TorrentFetcherDownload noTransport = queued(info, null, work);
    // a hash that is not 40 digits
    List<Runnable> work2 = new ArrayList<>();
    TorrentDownloadInfo shortHash = info(holderMagnet());
    when(shortHash.getHash()).thenReturn("abc");
    TorrentFetcherDownload badHash = queued(shortHash, null, work2);
    // a wiring call that throws
    List<Runnable> work3 = new ArrayList<>();
    TorrentDownloadInfo throwing = info(holderMagnet());
    when(throwing.getHash()).thenReturn(HASH);
    TorrentFetcherDownload broken = queued(throwing, null, work3);
    try (MockedStatic<AndroidSearchEngines> engines = mockStatic(AndroidSearchEngines.class)) {
      engines.when(AndroidSearchEngines::distributed).thenReturn(unwired);
      work.get(0).run();
      work2.get(0).run();
      engines.when(AndroidSearchEngines::distributed).thenThrow(new IllegalStateException("down"));
      work3.get(0).run();
    }
    assertEquals(TransferState.ERROR, noTransport.getState());
    assertEquals(TransferState.ERROR, badHash.getState());
    assertEquals(TransferState.ERROR, broken.getState());
  }

  @Test
  public void aFetchRemovedWhileTheMeshWasBusyStartsNothing() {
    RelaySearchWiring wiring = mock(RelaySearchWiring.class);
    when(wiring.searchTransport())
        .thenReturn(mock(com.frostwire.search.relay.DistributedSearchTransport.class));
    when(wiring.identity()).thenReturn(mock(com.frostwire.search.relay.IdentityKeys.class));
    List<Runnable> work = new ArrayList<>();
    TorrentDownloadInfo info = info(holderMagnet());
    when(info.getHash()).thenReturn(HASH);
    TorrentFetcherDownload fetch = queued(info, null, work);
    try (MockedStatic<AndroidSearchEngines> engines = mockStatic(AndroidSearchEngines.class);
        MockedStatic<MeshTorrentMetadataFetcher> mesh =
            mockStatic(MeshTorrentMetadataFetcher.class)) {
      engines.when(AndroidSearchEngines::distributed).thenReturn(wiring);
      mesh.when(() -> MeshTorrentMetadataFetcher.fetch(any(), any(), any(), any(), anyLong()))
          .thenAnswer(
              call -> {
                fetch.remove(false);
                return new byte[] {1, 2, 3};
              });
      work.get(0).run();
    }
    assertEquals(TransferState.CANCELED, fetch.getState());
  }
}
