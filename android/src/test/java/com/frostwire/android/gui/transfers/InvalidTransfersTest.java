/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.transfers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.frostwire.android.R;
import com.frostwire.frostclick.Slide;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.TorrentCrawledSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.transfers.TransferState;
import org.junit.Test;

/** Placeholder transfers returned when a download is refused, and the small search-result infos. */
public class InvalidTransfersTest {

  @Test
  public void invalidDownloadReportsItsReasonAndIsInert() {
    SearchResult sr = mock(SearchResult.class);
    InvalidDownload d = new InvalidDownload(R.string.cloud_download_aborted_on_mobile_data, sr);
    assertEquals(R.string.cloud_download_aborted_on_mobile_data, d.getReasonResId());
    assertSame(sr, d.getSearchResult());
    assertEquals(TransferState.UNKNOWN, d.getState());
    assertFalse(d.isComplete());
    assertFalse(d.isDownloading());
    assertEquals(0, d.getProgress());
    assertEquals(0, d.getSize());
    assertEquals(0, d.getBytesReceived());
    assertEquals(0, d.getBytesSent());
    assertEquals(0, d.getDownloadSpeed());
    assertEquals(0, d.getUploadSpeed());
    assertEquals(0, d.getETA());
    assertNull(d.getDisplayName());
    assertNull(d.getName());
    assertNull(d.getCreated());
    assertNull(d.getItems());
    assertNull(d.getSavePath());
    assertNull(d.previewFile());
    d.remove(true);
  }

  @Test
  public void defaultInvalidDownloadSaysTypeNotSupported() {
    InvalidDownload d = new InvalidDownload();
    assertEquals(R.string.download_type_not_supported, d.getReasonResId());
    assertNull(d.getSearchResult());
    assertTrue(new ExistingDownload() instanceof InvalidTransfer);
  }

  @Test
  public void invalidBittorrentDownloadIsInert() {
    SearchResult sr = mock(SearchResult.class);
    InvalidBittorrentDownload d =
        new InvalidBittorrentDownload(R.string.cannot_start_engine_without_vpn, sr);
    assertEquals(R.string.cannot_start_engine_without_vpn, d.getReasonResId());
    assertSame(sr, d.getSearchResult());
    assertEquals(TransferState.UNKNOWN, d.getState());
    assertEquals("torrent", d.getPredominantFileExtension());
    assertTrue(d.getItems().isEmpty());
    assertFalse(d.isComplete());
    assertFalse(d.isDownloading());
    assertFalse(d.isSeeding());
    assertFalse(d.isPaused());
    assertFalse(d.isFinished());
    assertEquals(0, d.getConnectedPeers());
    assertEquals(0, d.getTotalPeers());
    assertEquals(0, d.getConnectedSeeds());
    assertEquals(0, d.getTotalSeeds());
    assertEquals(0, d.getProgress());
    assertEquals(0, d.getSize());
    assertEquals(0, d.getBytesReceived());
    assertEquals(0, d.getBytesSent());
    assertEquals(0, d.getDownloadSpeed());
    assertEquals(0, d.getUploadSpeed());
    assertEquals(0, d.getETA());
    assertNull(d.getSavePath());
    assertNull(d.getContentSavePath());
    assertNull(d.getDisplayName());
    assertNull(d.getName());
    assertNull(d.getCreated());
    assertNull(d.magnetUri());
    assertNull(d.getInfoHash());
    assertNull(d.previewFile());
    d.pause();
    d.resume();
    d.remove(true);
    d.remove(true, true);
  }

  @Test
  public void slideSearchResultExposesTheSlide() {
    Slide slide = new Slide();
    slide.title = "A title";
    slide.size = 1234;
    slide.httpDownloadURL = "https://example.invalid/a.zip";
    slide.uncompress = true;
    HttpSlideSearchResult r = new HttpSlideSearchResult(slide);
    assertEquals("A title", r.getDisplayName());
    assertEquals(1234, r.getSize());
    assertEquals("https://example.invalid/a.zip", r.getHttpUrl());
    assertTrue(r.isCompressed());
    assertSame(slide, r.slide());
    assertNull(r.getFilename());
    assertNull(r.getSource());
    assertNull(r.getDetailsUrl());
    assertEquals(0, r.getCreationTime());
  }

  @Test
  public void torrentSearchResultInfoDelegatesToTheResult() {
    TorrentSearchResult sr = mock(TorrentSearchResult.class);
    when(sr.getTorrentUrl()).thenReturn("https://example.invalid/x.torrent");
    when(sr.getDetailsUrl()).thenReturn("https://example.invalid/x");
    when(sr.getDisplayName()).thenReturn("x");
    when(sr.getSize()).thenReturn(99L);
    when(sr.getHash()).thenReturn("abc");
    when(sr.getReferrerUrl()).thenReturn("https://ref.invalid/");
    byte[] pub = {1, 2};
    when(sr.getDistributedSourcePeerPub()).thenReturn(pub);
    TorrentSearchResultInfo info = new TorrentSearchResultInfo(sr);
    assertEquals("https://example.invalid/x.torrent", info.getTorrentUrl());
    assertEquals("https://example.invalid/x", info.getDetailsUrl());
    assertEquals("x", info.getDisplayName());
    assertEquals(99, info.getSize());
    assertEquals("abc", info.getHash());
    assertEquals("https://ref.invalid/", info.getReferrerUrl());
    assertSame(pub, info.getDistributedSourcePeerPub());
    assertNull(info.makeMagnetUri());
    assertNull(info.getRelativePath());
    assertEquals("other", new TorrentSearchResultInfo(sr, "other").getReferrerUrl());
  }

  @Test
  public void crawledResultInfoExposesTheFilePath() {
    TorrentCrawledSearchResult sr = mock(TorrentCrawledSearchResult.class);
    when(sr.getFilePath()).thenReturn("dir/file.mp3");
    assertEquals("dir/file.mp3", new TorrentSearchResultInfo(sr, null).getRelativePath());
  }

  @Test
  public void torrentUrlInfoNamesAndCopiesPeerKey() {
    assertEquals(
        "a.torrent", new TorrentUrlInfo("https://x.invalid/dir/a.torrent").getDisplayName());
    assertEquals(
        "Given", new TorrentUrlInfo("https://x.invalid/a.torrent", "Given").getDisplayName());
    assertEquals(
        "a.torrent", new TorrentUrlInfo("https://x.invalid/a.torrent", "").getDisplayName());
    byte[] key = {5, 6};
    TorrentUrlInfo info = new TorrentUrlInfo("https://x.invalid/a.torrent", "n", key);
    key[0] = 9;
    assertEquals(5, info.getDistributedSourcePeerPub()[0]);
    assertNull(new TorrentUrlInfo("u").getDistributedSourcePeerPub());
    assertEquals(-1, info.getSize());
    assertNull(info.getDetailsUrl());
    assertNull(info.makeMagnetUri());
    assertNull(info.getRelativePath());
    assertNull(info.getReferrerUrl());
    assertEquals("https://x.invalid/a.torrent", info.getTorrentUrl());
  }
}
