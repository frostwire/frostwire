/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.alerts.Alert;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Every download registered its own session listener, so jlibtorrent chained them N deep and
 * removing one threw StackOverflowError with thousands of transfers (Icebase #5276af11e10a, GitHub
 * #1441). The engine now routes alerts to downloads through one listener and this router.
 */
class TorrentAlertRouterTest {

  /** A native handle stand-in: an id that is 0 once the torrent is gone, and its torrent. */
  private record FakeHandle(long id, Object torrent) {}

  private static final TorrentAlertRouter.Handles<FakeHandle> HANDLES =
      new TorrentAlertRouter.Handles<>() {
        @Override
        public long id(FakeHandle handle) {
          return handle.id();
        }

        @Override
        public boolean same(FakeHandle a, FakeHandle b) {
          return a.torrent() == b.torrent();
        }
      };

  private static final class Counting implements AlertListener {
    final AtomicInteger alerts = new AtomicInteger();

    @Override
    public int[] types() {
      return null;
    }

    @Override
    public void alert(Alert<?> alert) {
      alerts.incrementAndGet();
    }
  }

  @Test
  void eachAlertReachesOnlyItsOwnDownload() {
    TorrentAlertRouter<FakeHandle> router = new TorrentAlertRouter<>(HANDLES);
    List<FakeHandle> handles = new ArrayList<>();
    List<Counting> listeners = new ArrayList<>();
    for (int i = 1; i <= 3; i++) {
      FakeHandle handle = new FakeHandle(i, new Object());
      Counting listener = new Counting();
      handles.add(handle);
      listeners.add(listener);
      router.add(handle, listener);
    }

    router.dispatch(new FakeHandle(2, handles.get(1).torrent()), null);

    assertEquals(0, listeners.get(0).alerts.get());
    assertEquals(1, listeners.get(1).alerts.get());
    assertEquals(0, listeners.get(2).alerts.get());
  }

  @Test
  void anAlertForARemovedTorrentStillReachesItsDownload() {
    TorrentAlertRouter<FakeHandle> router = new TorrentAlertRouter<>(HANDLES);
    Object torrent = new Object();
    Counting listener = new Counting();
    router.add(new FakeHandle(7, torrent), listener);

    // libtorrent's torrent_handle::id() is 0 once the torrent object is gone (TORRENT_REMOVED).
    router.dispatch(new FakeHandle(0, torrent), null);

    assertEquals(1, listener.alerts.get());
  }

  @Test
  void aReusedIdNeverSendsAnAlertToAnotherDownload() {
    TorrentAlertRouter<FakeHandle> router = new TorrentAlertRouter<>(HANDLES);
    Object oldTorrent = new Object();
    Object newTorrent = new Object();
    Counting oldListener = new Counting();
    Counting newListener = new Counting();
    // The id is the torrent's address: a new torrent can reuse a removed one's before its
    // download unregisters.
    router.add(new FakeHandle(7, oldTorrent), oldListener);
    router.add(new FakeHandle(7, newTorrent), newListener);

    router.dispatch(new FakeHandle(0, oldTorrent), null);
    router.dispatch(new FakeHandle(7, newTorrent), null);
    router.dispatch(new FakeHandle(7, new Object()), null);

    assertEquals(1, oldListener.alerts.get());
    assertEquals(1, newListener.alerts.get());

    router.remove(oldListener);
    router.dispatch(new FakeHandle(7, newTorrent), null);
    assertEquals(2, newListener.alerts.get(), "removing the old download keeps the new one routed");
  }

  @Test
  void removedDownloadsGetNoMoreAlerts() {
    TorrentAlertRouter<FakeHandle> router = new TorrentAlertRouter<>(HANDLES);
    Object torrent = new Object();
    Counting listener = new Counting();
    router.add(new FakeHandle(3, torrent), listener);

    router.remove(listener);
    router.remove(listener);
    router.dispatch(new FakeHandle(3, torrent), null);
    router.dispatch(new FakeHandle(0, torrent), null);

    assertEquals(0, listener.alerts.get());
    assertEquals(0, router.size());
  }

  @Test
  void tensOfThousandsOfDownloadsAddDispatchAndRemove() {
    TorrentAlertRouter<FakeHandle> router = new TorrentAlertRouter<>(HANDLES);
    int downloads = 50_000;
    List<FakeHandle> handles = new ArrayList<>(downloads);
    List<Counting> listeners = new ArrayList<>(downloads);
    for (int i = 1; i <= downloads; i++) {
      FakeHandle handle = new FakeHandle(i, new Object());
      Counting listener = new Counting();
      handles.add(handle);
      listeners.add(listener);
      router.add(handle, listener);
    }

    router.dispatch(handles.get(downloads - 1), null);
    router.remove(listeners.get(0));
    router.remove(listeners.get(downloads / 2));

    assertEquals(1, listeners.get(downloads - 1).alerts.get());
    assertEquals(downloads - 2, router.size());
  }

  @Test
  void downloadsRegisterWithTheEngineRouterNotAsSessionListeners() throws Exception {
    String source =
        Files.readString(
                Path.of(System.getProperty("user.dir"))
                    .resolve("../common/src/main/java/com/frostwire/bittorrent/BTDownload.java"),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    assertFalse(
        source.contains("engine.addListener(") || source.contains("engine.removeListener("),
        "a session listener per download chains jlibtorrent's listeners N deep");
    assertTrue(source.contains("engine.addDownloadListener(th,innerListener);"));
  }
}
