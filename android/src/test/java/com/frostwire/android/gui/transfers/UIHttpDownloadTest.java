/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.transfers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.android.util.TorrentUtils;
import com.frostwire.frostclick.Slide;
import com.frostwire.platform.FileSystem;
import com.frostwire.platform.Platforms;
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.HttpSearchResult;
import com.frostwire.search.telluride.DashMux;
import com.frostwire.search.telluride.TellurideSearchResult;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.http.HttpClient;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class UIHttpDownloadTest {
  private File dir;
  private FileSystem fs;
  private Engine engine;
  private TransferManager manager;
  private MockedStatic<Platforms> platforms;
  private MockedStatic<Engine> engineStatic;
  private MockedStatic<TorrentUtils> torrentUtils;
  private MockedStatic<SystemUtils> system;
  private final List<Runnable> posted = new ArrayList<>();

  @Before
  public void setUp() throws IOException {
    dir = Files.createTempDirectory("uihttp").toFile();
    fs = mock(FileSystem.class);
    when(fs.isDirectory(any())).thenReturn(true);
    when(fs.copy(any(), any())).thenReturn(true);
    when(fs.delete(any())).thenReturn(true);
    platforms = mockStatic(Platforms.class);
    platforms.when(Platforms::data).thenReturn(new File(dir, "data"));
    platforms.when(Platforms::temp).thenReturn(new File(dir, "temp"));
    platforms.when(Platforms::fileSystem).thenReturn(fs);
    engine = mock(Engine.class);
    engineStatic = mockStatic(Engine.class);
    engineStatic.when(Engine::instance).thenReturn(engine);
    torrentUtils = mockStatic(TorrentUtils.class);
    system = mockStatic(SystemUtils.class);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            call -> {
              posted.add(call.getArgument(1));
              return null;
            });
    manager = mock(TransferManager.class);
  }

  @After
  public void tearDown() {
    system.close();
    torrentUtils.close();
    engineStatic.close();
    platforms.close();
    deleteRecursively(dir);
  }

  @Test
  public void searchResultConstructorCopiesTheResultIntoTheTransfer() {
    HttpSearchResult sr = mock(HttpSearchResult.class);
    when(sr.getDownloadUrl()).thenReturn("https://example.invalid/a.mp3");
    when(sr.getFilename()).thenReturn("a.mp3");
    when(sr.getDisplayName()).thenReturn("A song");
    when(sr.getSize()).thenReturn(77L);
    UIHttpDownload d = new UIHttpDownload(manager, sr);
    assertEquals("https://example.invalid/a.mp3", d.getDownloadUrl());
    assertEquals("A song", d.getDisplayName());
    assertEquals(77, d.getSize());
    assertEquals(TransferState.WAITING, d.getState());
    assertEquals("a.mp3", d.getSavePath().getName());
  }

  @Test
  public void compositeConstructorUsesTheHttpUrlOrNone() {
    CompositeFileSearchResult sr = mock(CompositeFileSearchResult.class);
    when(sr.getHttpDownloadUrl()).thenReturn(Optional.of("https://example.invalid/b.mp4"));
    when(sr.getFilename()).thenReturn("b.mp4");
    when(sr.getDisplayName()).thenReturn("A video");
    when(sr.getSize()).thenReturn(5L);
    UIHttpDownload d = new UIHttpDownload(manager, sr);
    assertEquals("https://example.invalid/b.mp4", d.getDownloadUrl());
    when(sr.getHttpDownloadUrl()).thenReturn(Optional.empty());
    assertEquals(null, new UIHttpDownload(manager, sr).getDownloadUrl());
  }

  @Test
  public void slideConstructorPrefersTheHttpUrlOverTheTorrent() {
    Slide slide = new Slide();
    slide.title = "Slide";
    slide.size = 9;
    slide.httpDownloadURL = "https://example.invalid/dir/file.zip";
    slide.torrent = "https://example.invalid/file.torrent";
    UIHttpDownload http = new UIHttpDownload(manager, slide);
    assertEquals("https://example.invalid/dir/file.zip", http.getDownloadUrl());
    assertEquals("file.zip", http.getSavePath().getName());
    slide.httpDownloadURL = null;
    UIHttpDownload torrent = new UIHttpDownload(manager, slide);
    assertEquals("https://example.invalid/file.torrent", torrent.getDownloadUrl());
    assertEquals("file.torrent", torrent.getSavePath().getName());
  }

  @Test
  public void removingACancelledTransferUnregistersItFromTheManager() {
    HttpSearchResult sr = plainResult("c.mp3", null);
    UIHttpDownload d = new UIHttpDownload(manager, sr);
    d.remove(false);
    assertEquals(TransferState.CANCELED, d.getState());
    assertTrue(d.isComplete());
    verify(manager).remove(d);
  }

  @Test
  public void completingATransferAnnouncesAndSeedsIt() {
    UIHttpDownload d = new UIHttpDownload(manager, plainResult("d.mp3", null));
    d.moveAndComplete(new File(dir, "temp/d.mp3"), d.getSavePath());
    assertEquals(TransferState.COMPLETE, d.getState());
    verify(manager).incrementDownloadsToReview();
    verify(engine).notifyDownloadFinished("d.mp3 title", d.getSavePath());
    torrentUtils.verify(
        () ->
            TorrentUtils.seedFinishedHttpDownloadIfEnabled(
                eq(d.getSavePath()), eq("d.mp3 title"), anyByte(), eq(manager), eq(d)));
    assertEquals("no thumbnail, no sidecar download", 0, posted.size());
  }

  @Test
  public void aFailedCopyDoesNotAnnounceTheDownload() {
    when(fs.copy(any(), any())).thenReturn(false);
    UIHttpDownload d = new UIHttpDownload(manager, plainResult("e.mp3", null));
    d.moveAndComplete(new File(dir, "temp/e.mp3"), d.getSavePath());
    assertEquals(TransferState.ERROR_MOVING_INCOMPLETE, d.getState());
    verify(manager, never()).incrementDownloadsToReview();
  }

  @Test
  public void mediaScanRunsOnNewerAndroidWhenAContextExists() {
    system.when(SystemUtils::hasAndroid11OrNewer).thenReturn(true);
    system.when(SystemUtils::getApplicationContext).thenReturn(null);
    UIHttpDownload d = new UIHttpDownload(manager, plainResult("f.mp3", null));
    d.moveAndComplete(new File(dir, "temp/f.mp3"), d.getSavePath());
    assertEquals(TransferState.COMPLETE, d.getState());
    assertEquals("no context: the scan and the sidecar are skipped", 0, posted.size());
  }

  @Test
  public void aThumbnailIsSavedNextToTheFileAsAHiddenSidecar() throws Exception {
    byte[] art = "art-bytes".getBytes(StandardCharsets.UTF_8);
    ServerSocket server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
    Thread serverThread =
        new Thread(
            () -> {
              try {
                while (!server.isClosed()) {
                  try (Socket socket = server.accept()) {
                    java.io.InputStream in = socket.getInputStream();
                    byte[] buf = new byte[4096];
                    int n = in.read(buf);
                    String head = n > 0 ? new String(buf, 0, n, StandardCharsets.US_ASCII) : "";
                    if (!head.contains("\r\n\r\n")) {
                      continue;
                    }
                    java.io.OutputStream out = socket.getOutputStream();
                    out.write(
                        ("HTTP/1.1 200 OK\r\nContent-Length: "
                                + art.length
                                + "\r\nConnection: close\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    out.write(art);
                    out.flush();
                  }
                }
              } catch (IOException closed) {
                // server closed by the test
              }
            });
    serverThread.setDaemon(true);
    serverThread.start();
    try {
      String thumb = "http://127.0.0.1:" + server.getLocalPort() + "/art.jpg";
      File saveDir = new File(dir, "data");
      saveDir.mkdirs();
      UIHttpDownload d = new UIHttpDownload(manager, plainResult("g.mp3", thumb));
      d.moveAndComplete(new File(dir, "temp/g.mp3"), d.getSavePath());
      assertEquals(1, posted.size());
      posted.get(0).run();
      File sidecar = new File(d.getSavePath().getParent(), ".g.mp3.art");
      assertTrue(sidecar.exists());
      assertEquals(
          "art-bytes", new String(Files.readAllBytes(sidecar.toPath()), StandardCharsets.UTF_8));
      // a second run finds the sidecar and leaves it alone
      Files.write(sidecar.toPath(), new byte[] {1});
      posted.get(0).run();
      assertEquals(1, sidecar.length());
    } finally {
      server.close();
    }
  }

  @Test
  public void anUnreachableThumbnailIsIgnored() {
    UIHttpDownload d =
        new UIHttpDownload(manager, plainResult("h.mp3", "http://127.0.0.1:1/missing.jpg"));
    d.moveAndComplete(new File(dir, "temp/h.mp3"), d.getSavePath());
    posted.get(0).run();
    assertFalse(new File(d.getSavePath().getParent(), ".h.mp3.art").exists());
    // and an empty url does not even queue the work
    posted.clear();
    UIHttpDownload empty = new UIHttpDownload(manager, plainResult("i.mp3", ""));
    empty.moveAndComplete(new File(dir, "temp/i.mp3"), empty.getSavePath());
    assertEquals(0, posted.size());
  }

  @Test
  public void aSilentDashVideoGetsItsAudioMuxedIn() throws Exception {
    TellurideSearchResult sr = telluride("v.mp4");
    UIHttpDownload d = new UIHttpDownload(manager, sr);
    File video = d.getSavePath();
    video.getParentFile().mkdirs();
    Files.write(video.toPath(), new byte[] {1});
    HttpClient client = mock(HttpClient.class);
    try (MockedStatic<HttpClientFactory> http = mockStatic(HttpClientFactory.class);
        MockedStatic<DashMux> mux = mockStatic(DashMux.class)) {
      http.when(() -> HttpClientFactory.newInstance(HttpClientFactory.HttpContext.DOWNLOAD))
          .thenReturn(client);
      mux.when(() -> DashMux.muxIfSupported(any(), any(), any(), any(), any()))
          .thenAnswer(
              call -> {
                Files.write(((File) call.getArgument(4)).toPath(), new byte[] {9, 9});
                return true;
              });
      d.moveAndComplete(new File(dir, "temp/v.mp4"), video);
    }
    verify(client).save(eq("https://example.invalid/audio"), any(File.class), eq(false), any());
    assertEquals("merged file replaced the video", 2, video.length());
    assertEquals(TransferState.COMPLETE, d.getState());
    verify(engine).notifyDownloadFinished(any(), eq(video));
  }

  @Test
  public void anUnsupportedMuxKeepsTheSilentVideo() throws Exception {
    UIHttpDownload d = new UIHttpDownload(manager, telluride("w.mp4"));
    File video = d.getSavePath();
    video.getParentFile().mkdirs();
    Files.write(video.toPath(), new byte[] {1});
    try (MockedStatic<HttpClientFactory> http = mockStatic(HttpClientFactory.class);
        MockedStatic<DashMux> mux = mockStatic(DashMux.class)) {
      http.when(() -> HttpClientFactory.newInstance(HttpClientFactory.HttpContext.DOWNLOAD))
          .thenReturn(mock(HttpClient.class));
      mux.when(() -> DashMux.muxIfSupported(any(), any(), any(), any(), any())).thenReturn(false);
      d.moveAndComplete(new File(dir, "temp/w.mp4"), video);
    }
    assertEquals(1, video.length());
    assertEquals(TransferState.COMPLETE, d.getState());
  }

  @Test
  public void aFailingAudioFetchIsRetriedThreeTimesThenGivenUp() throws Exception {
    UIHttpDownload d = new UIHttpDownload(manager, telluride("x.mp4"));
    File video = d.getSavePath();
    video.getParentFile().mkdirs();
    Files.write(video.toPath(), new byte[] {1});
    HttpClient client = mock(HttpClient.class);
    org.mockito.Mockito.doThrow(new IOException("throttled"))
        .when(client)
        .save(any(), any(File.class), org.mockito.ArgumentMatchers.anyBoolean(), any());
    try (MockedStatic<HttpClientFactory> http = mockStatic(HttpClientFactory.class)) {
      http.when(() -> HttpClientFactory.newInstance(HttpClientFactory.HttpContext.DOWNLOAD))
          .thenReturn(client);
      d.moveAndComplete(new File(dir, "temp/x.mp4"), video);
    }
    verify(client, org.mockito.Mockito.times(3))
        .save(any(), any(File.class), org.mockito.ArgumentMatchers.anyBoolean(), any());
    verify(client).save(any(), any(File.class), eq(false), any());
    assertEquals(1, video.length());
    assertEquals(TransferState.COMPLETE, d.getState());
  }

  @Test
  public void noMuxHappensWithoutAnAudioSiblingOrWithoutTheVideoFile() throws Exception {
    // video-only result but the downloaded file is missing
    UIHttpDownload missing = new UIHttpDownload(manager, telluride("y.mp4"));
    try (MockedStatic<HttpClientFactory> http = mockStatic(HttpClientFactory.class)) {
      missing.moveAndComplete(new File(dir, "temp/y.mp4"), missing.getSavePath());
      http.verifyNoInteractions();
    }
    // Telluride result that does not need muxing
    TellurideSearchResult plain = mock(TellurideSearchResult.class);
    when(plain.needsAudioMux()).thenReturn(false);
    when(plain.getFilename()).thenReturn("z.mp4");
    when(plain.getDisplayName()).thenReturn("z");
    when(plain.getHttpHeaders()).thenReturn(Collections.singletonMap("k", "v"));
    UIHttpDownload d = new UIHttpDownload(manager, plain);
    File video = d.getSavePath();
    video.getParentFile().mkdirs();
    Files.write(video.toPath(), new byte[] {1});
    try (MockedStatic<HttpClientFactory> http = mockStatic(HttpClientFactory.class)) {
      d.moveAndComplete(new File(dir, "temp/z.mp4"), video);
      http.verifyNoInteractions();
    }
  }

  private TellurideSearchResult telluride(String filename) {
    TellurideSearchResult sr = mock(TellurideSearchResult.class);
    Map<String, String> headers = new HashMap<>();
    headers.put("Referer", "https://example.invalid/");
    when(sr.needsAudioMux()).thenReturn(true);
    when(sr.getMuxAudioUrl()).thenReturn("https://example.invalid/audio");
    when(sr.getMuxAudioExt()).thenReturn("m4a");
    when(sr.getMuxAudioHeaders()).thenReturn(headers);
    when(sr.getFilename()).thenReturn(filename);
    when(sr.getDisplayName()).thenReturn(filename + " title");
    when(sr.getHttpHeaders()).thenReturn(headers);
    return sr;
  }

  private static HttpSearchResult plainResult(String filename, String thumbnail) {
    HttpSearchResult sr = mock(HttpSearchResult.class);
    when(sr.getDownloadUrl()).thenReturn("https://example.invalid/" + filename);
    when(sr.getFilename()).thenReturn(filename);
    when(sr.getDisplayName()).thenReturn(filename + " title");
    when(sr.getThumbnailUrl()).thenReturn(thumbnail);
    return sr;
  }

  private static void deleteRecursively(File f) {
    File[] children = f.listFiles();
    if (children != null) {
      for (File c : children) {
        deleteRecursively(c);
      }
    }
    f.delete();
  }
}
