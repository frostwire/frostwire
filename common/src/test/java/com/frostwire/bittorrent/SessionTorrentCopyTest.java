/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.SessionParams;
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.TorrentBuilder;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.swig.create_flags_t;
import com.frostwire.jlibtorrent.swig.settings_pack;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * FrostWire restores transfers from the session copies of their .torrent files, which it writes
 * from the torrent's handle. libtorrent's torrent_handle::torrent_file() drops the v2 piece layers,
 * and re-encoding a v2-only torrent without them throws, so its copy was never written and the
 * transfer vanished after a restart (GitHub #1383). Checked here on a real local session (127.0.0.1
 * only; no DHT, LSD, UPnP or NAT-PMP) for v1-only, v2-only and hybrid torrents.
 */
@Timeout(60)
class SessionTorrentCopyTest {

  @TempDir Path dir;
  private SessionManager session;

  @BeforeEach
  void startSession() {
    SettingsPack sp = new SettingsPack();
    sp.setString(settings_pack.string_types.listen_interfaces.swigValue(), "127.0.0.1:0");
    sp.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), false);
    sp.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), false);
    sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), false);
    sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), false);
    session = new SessionManager(false);
    session.start(new SessionParams(sp));
  }

  @AfterEach
  void stopSession() {
    session.stop();
  }

  @Test
  void aV2OnlyTorrentsSessionCopyIsWritten() throws Exception {
    assertCopyRoundTrips("v2only", TorrentBuilder.V2_ONLY);
  }

  @Test
  void aHybridTorrentsSessionCopyIsWritten() throws Exception {
    assertCopyRoundTrips("hybrid", null);
  }

  @Test
  void aV1OnlyTorrentsSessionCopyIsWritten() throws Exception {
    assertCopyRoundTrips("v1only", TorrentBuilder.V1_ONLY);
  }

  private void assertCopyRoundTrips(String name, create_flags_t flags) throws Exception {
    Path data = Files.createDirectories(dir.resolve(name));
    Path content = data.resolve(name + ".bin");
    byte[] bytes = new byte[96 * 1024];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) (i * 31 + name.length());
    }
    Files.write(content, bytes);
    TorrentBuilder builder = new TorrentBuilder().path(content.toFile());
    if (flags != null) {
      builder.flags(flags);
    }
    TorrentInfo original = TorrentInfo.bdecode(builder.generate().entry().bencode());

    session.download(original, data.toFile());
    TorrentHandle th = awaitHandle(original);

    byte[] copy = BTEngine.sessionTorrentBytes(th);

    assertNotNull(copy, "the session copy of a " + name + " torrent must be written");
    TorrentInfo restored = TorrentInfo.bdecode(copy);
    assertEquals(v1Hex(original), v1Hex(restored));
    assertEquals(v2Hex(original), v2Hex(restored));
    assertEquals(original.numFiles(), restored.numFiles());
  }

  /** A v2-only torrent has no v1 info hash. */
  private static String v1Hex(TorrentInfo ti) {
    return ti.infoHashV1() != null ? ti.infoHashV1().toHex() : null;
  }

  /** A v1-only torrent has no v2 info hash. */
  private static String v2Hex(TorrentInfo ti) {
    return ti.infoHashV2() != null ? ti.infoHashV2().toHex() : null;
  }

  private TorrentHandle awaitHandle(TorrentInfo ti) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    TorrentHandle th = session.find(ti);
    while ((th == null || !th.isValid() || th.torrentFile() == null)
        && System.nanoTime() < deadline) {
      Thread.sleep(20);
      th = session.find(ti);
    }
    assertNotNull(th, "the torrent was added");
    return th;
  }
}
