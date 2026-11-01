/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.SessionParams;
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.TorrentBuilder;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.frostwire.jlibtorrent.alerts.TorrentAlert;
import com.frostwire.jlibtorrent.swig.settings_pack;
import com.frostwire.jlibtorrent.swig.torrent_handle;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Checks, against a real libtorrent session, the handle semantics {@link TorrentAlertRouter} relies
 * on and {@link TorrentAlertRouterTest}'s fake handles model (GitHub #1441).
 *
 * <ul>
 *   <li>A live torrent's handle has a non-zero id().
 *   <li>The TORRENT_REMOVED alert's handle still op_eq()s the handle taken before removal, so the
 *       router delivers it to that download.
 *   <li>Once the torrent object is gone, id() is 0 and the router still finds the download by
 *       comparing handles.
 * </ul>
 *
 * <p>The session listens on 127.0.0.1 only, with DHT, local discovery, UPnP and NAT-PMP off.
 */
@Timeout(60)
class TorrentAlertRouterNativeTest {

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
  void aRemovedTorrentsAlertsStillReachItsDownload(@TempDir Path dir) throws Exception {
    TorrentInfo ti = createTorrent(dir);
    File saveDir = Files.createDirectories(dir.resolve("save")).toFile();
    TorrentAlertRouter<torrent_handle> router =
        new TorrentAlertRouter<>(TorrentAlertRouter.NATIVE_HANDLES);
    Counting download = new Counting();
    CountDownLatch removed = new CountDownLatch(1);
    AtomicBoolean removedAlertMatches = new AtomicBoolean();
    AtomicLong removedAlertId = new AtomicLong(-1);

    SessionManager session = new SessionManager(false);
    session.start(new SessionParams(localOnlySettings()));
    try {
      session.download(ti, saveDir);
      TorrentHandle th = awaitHandle(session, ti);
      torrent_handle registered = th.swig();
      long liveId = TorrentAlertRouter.NATIVE_HANDLES.id(registered);
      assertNotEquals(0, liveId, "a live torrent's handle has an id");

      router.add(registered, download);
      session.addListener(
          new AlertListener() {
            @Override
            public int[] types() {
              return new int[] {AlertType.TORRENT_REMOVED.swig()};
            }

            @Override
            public void alert(Alert<?> alert) {
              torrent_handle handle = ((TorrentAlert<?>) alert).swig().getHandle();
              removedAlertId.set(TorrentAlertRouter.NATIVE_HANDLES.id(handle));
              removedAlertMatches.set(TorrentAlertRouter.NATIVE_HANDLES.same(registered, handle));
              router.dispatch(handle, alert);
              removed.countDown();
            }
          });

      session.remove(th);
      assertTrue(removed.await(30, TimeUnit.SECONDS), "TORRENT_REMOVED arrived");
      assertTrue(
          removedAlertMatches.get(), "the removed alert's handle compares equal to the old one");
      assertEquals(1, download.alerts.get(), "the router delivered TORRENT_REMOVED");
      assertTrue(
          removedAlertId.get() == 0 || removedAlertId.get() == liveId,
          "the alert's handle has the torrent's id, or 0 if it was already gone");

      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
      while (TorrentAlertRouter.NATIVE_HANDLES.id(registered) != 0
          && System.nanoTime() < deadline) {
        Thread.sleep(20);
      }
      assertEquals(0, TorrentAlertRouter.NATIVE_HANDLES.id(registered), "id() is 0 once gone");

      router.dispatch(registered, null);
      assertEquals(2, download.alerts.get(), "with id 0 the router still finds the download");
    } finally {
      session.stop();
    }
  }

  private static SettingsPack localOnlySettings() {
    SettingsPack sp = new SettingsPack();
    sp.setString(settings_pack.string_types.listen_interfaces.swigValue(), "127.0.0.1:0");
    sp.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), false);
    sp.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), false);
    sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), false);
    sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), false);
    return sp;
  }

  private static TorrentInfo createTorrent(Path dir) throws Exception {
    Path content = dir.resolve("content.bin");
    byte[] bytes = new byte[64 * 1024];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) i;
    }
    Files.write(content, bytes);
    TorrentBuilder.Result result = new TorrentBuilder().path(content.toFile()).generate();
    return TorrentInfo.bdecode(result.entry().bencode());
  }

  private static TorrentHandle awaitHandle(SessionManager session, TorrentInfo ti)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    TorrentHandle th = session.find(ti);
    while ((th == null || !th.isValid()) && System.nanoTime() < deadline) {
      Thread.sleep(20);
      th = session.find(ti);
    }
    assertNotNull(th, "the torrent was added");
    return th;
  }
}
