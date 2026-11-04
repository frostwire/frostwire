/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import static com.frostwire.bittorrent.LocalEngine.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.Vectors;
import com.frostwire.jlibtorrent.swig.entry;
import com.frostwire.jlibtorrent.swig.settings_pack;
import com.frostwire.search.torrent.TorrentCrawledSearchResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * The engine's session lifecycle: the settings it starts with, the session state it saves to
 * settings.dat and loads on the next start, and what it reports to its listener.
 */
@Timeout(120)
class BTEngineSessionTest {

  @TempDir Path dir;
  private LocalEngine local;

  @AfterEach
  void stopEngine() {
    if (local != null) {
      local.close();
    }
  }

  @Test
  void startingAndStoppingIsReportedToTheListener() throws Exception {
    local = LocalEngine.start(dir);

    await(() -> local.events.started.get() == 1, "started was reported");
    local.engine.stop();

    assertEquals(1, local.events.stopped.get());
    assertFalse(local.engine.isRunning());
  }

  @Test
  void theListenerCanBeReplacedAndCleared() throws Exception {
    local = LocalEngine.create(dir);
    BTEngineAdapter adapter = new BTEngineAdapter();

    local.engine.setListener(adapter);
    assertSame(adapter, local.engine.getListener());
    local.engine.start();
    local.engine.stop();
    local.engine.setListener(null);
    local.engine.start();

    assertNull(local.engine.getListener());
    assertTrue(local.engine.isRunning());
    assertEquals(0, local.events.started.get(), "the replaced listener heard nothing");
  }

  @Test
  void aListenerNeedNotHandleDownloadsAndThereMayBeNone() throws Exception {
    local = LocalEngine.start(dir);
    TorrentInfo first = album("first");
    TorrentInfo second = album("second");
    File content = dir.resolve("content").toFile(); // has the data: they check and finish

    local.engine.setListener(new BTEngineAdapter());
    local.engine.download(first, content, null, null);
    awaitFinished(first);
    local.engine.setListener(null);
    local.engine.download(second, content, null, null);
    awaitFinished(second);

    assertNull(local.engine.getListener());
    assertTrue(local.events.added.isEmpty(), "the replaced listener heard nothing");
  }

  @Test
  void theSessionIdentifiesItselfAsThisFrostWireVersion() throws Exception {
    local = LocalEngine.start(dir);

    SettingsPack sp = local.engine.settings();

    String userAgent = sp.getString(settings_pack.string_types.user_agent.swigValue());
    assertTrue(userAgent.startsWith("FrostWire/6.14.2 libtorrent/"), userAgent);
    String fingerprint = sp.getString(settings_pack.string_types.peer_fingerprint.swigValue());
    assertTrue(fingerprint.startsWith("-FW6E21-"), fingerprint);
    assertEquals("127.0.0.1:0", local.engine.listenInterfaces());
    assertFalse(sp.getBoolean(settings_pack.bool_types.enable_dht.swigValue()));
    assertEquals(2000, sp.getInteger(settings_pack.int_types.active_limit.swigValue()));
    assertEquals(5000, sp.getInteger(settings_pack.int_types.alert_queue_size.swigValue()));
    assertTrue(BTEngine.dhtBootstrapNodes().contains("router.bittorrent.com:6881"));
    assertEquals(
        BTEngine.dhtBootstrapNodes(),
        sp.getString(settings_pack.string_types.dht_bootstrap_nodes.swigValue()));
    assertEquals("", sp.getString(settings_pack.string_types.i2p_hostname.swigValue()));
  }

  @Test
  void theContextsNetworkSettingsReachTheSession() throws Exception {
    local = LocalEngine.create(dir);
    local.ctx.natpmpGateway = "192.168.50.1";
    local.ctx.natpmpLeaseDuration = 1800;
    local.ctx.allowMultipleConnectionsPerPid = true;
    local.ctx.optimizeMemory = true;

    local.engine.start();

    SettingsPack sp = local.engine.settings();
    assertEquals("192.168.50.1", sp.natpmpGateway());
    assertEquals(1800, sp.natpmpLeaseDuration());
    assertTrue(sp.allowMultipleConnectionsPerPid());
    assertFalse(sp.getBoolean(settings_pack.bool_types.enable_ip_notifier.swigValue()));
  }

  @Test
  void aNullNatPmpGatewayMeansAutoDetect() {
    BTContext ctx = new BTContext();
    ctx.natpmpGateway = null;
    SettingsPack sp = new SettingsPack();
    sp.natpmpGateway("10.0.0.1");

    BTEngine.applyContextSettings(sp, ctx);

    assertEquals("", sp.natpmpGateway());
    assertEquals(3600, sp.natpmpLeaseDuration());
    assertFalse(sp.allowMultipleConnectionsPerPid());
  }

  @Test
  void i2pIsConfiguredWhenEnabled() throws Exception {
    local = LocalEngine.create(dir);
    local.ctx.i2pEnabled = true;
    local.ctx.i2pPort = 1; // nothing listens there, and ports under 1024 are filtered anyway
    local.ctx.i2pAllowMixed = true;
    local.ctx.i2pInboundQuantity = 2;
    local.ctx.i2pOutboundQuantity = 4;
    local.ctx.i2pInboundLength = 1;
    local.ctx.i2pOutboundLength = 5;

    local.engine.start();

    SettingsPack sp = local.engine.settings();
    assertEquals("127.0.0.1", sp.getString(settings_pack.string_types.i2p_hostname.swigValue()));
    assertEquals(1, sp.getInteger(settings_pack.int_types.i2p_port.swigValue()));
    assertTrue(sp.getBoolean(settings_pack.bool_types.allow_i2p_mixed.swigValue()));
    assertEquals(2, sp.getInteger(settings_pack.int_types.i2p_inbound_quantity.swigValue()));
    assertEquals(4, sp.getInteger(settings_pack.int_types.i2p_outbound_quantity.swigValue()));
    assertEquals(1, sp.getInteger(settings_pack.int_types.i2p_inbound_length.swigValue()));
    assertEquals(5, sp.getInteger(settings_pack.int_types.i2p_outbound_length.swigValue()));
  }

  @Test
  void i2pWithoutAHostnameStaysOff() throws Exception {
    local = LocalEngine.create(dir);
    local.ctx.i2pEnabled = true;
    local.ctx.i2pHostname = "";

    local.engine.start();

    assertEquals(
        "", local.engine.settings().getString(settings_pack.string_types.i2p_hostname.swigValue()));
  }

  @Test
  void changedSettingsAreSavedAndLoadedOnTheNextStart() throws Exception {
    local = LocalEngine.start(dir);
    File settings = local.engine.settingsFile();
    Files.delete(settings.toPath());

    local.engine.applySettings(new SettingsPack().downloadRateLimit(123_000));

    await(settings::isFile, "the settings were saved");
    assertEquals("1.2.0.6", stateVersion(settings));
    local.restart();
    assertEquals(123_000, local.engine.downloadRateLimit());
  }

  @Test
  void stoppingSavesTheSessionState() throws Exception {
    local = LocalEngine.start(dir);
    local.engine.downloadRateLimit(456_000);
    File settings = local.engine.settingsFile();

    local.engine.stop();

    assertEquals("1.2.0.6", stateVersion(settings));
    local.engine.start();
    assertEquals(456_000, local.engine.downloadRateLimit());
  }

  @Test
  void stateSavedByAnIncompatibleVersionIsIgnored() throws Exception {
    local = LocalEngine.create(dir);
    File settings = local.engine.settingsFile();
    LocalEngine.writeSettings(
        settings, LocalEngine.localOnlySettings().downloadRateLimit(789_000), "1.1.0.0");

    local.engine.start();

    assertEquals(0, local.engine.downloadRateLimit());
    assertEquals(10, local.engine.settings().activeDownloads(), "the defaults were used");
  }

  @Test
  void unreadableStateStartsWithTheDefaults() throws Exception {
    local = LocalEngine.create(dir);
    Files.write(local.engine.settingsFile().toPath(), new byte[] {'d', '9', ':'});
    local.ctx.optimizeMemory = true;

    local.engine.start();

    SettingsPack sp = local.engine.settings();
    assertTrue(local.engine.isRunning());
    assertEquals(4, sp.activeDownloads(), "the low-memory defaults were used");
    assertEquals(4, sp.activeSeeds());
    assertEquals(200, sp.connectionsLimit());
    assertEquals(200, sp.maxPeerlistSize());
  }

  @Test
  void withoutSavedStateTheSessionStartsWithTheDefaults() throws Exception {
    local = LocalEngine.create(dir);
    Files.delete(local.engine.settingsFile().toPath());

    local.engine.start();

    assertEquals(10, local.engine.settings().activeDownloads());
    assertEquals(10, local.engine.settings().activeSeeds());
  }

  @Test
  void revertingTheConfigurationRestoresTheDefaults() throws Exception {
    local = LocalEngine.start(dir);
    local.engine.applySettings(new SettingsPack().activeDownloads(2).activeSeeds(3));
    assertEquals(2, local.engine.settings().activeDownloads());

    local.engine.revertToDefaultConfiguration();

    assertEquals(10, local.engine.settings().activeDownloads());
    assertEquals(10, local.engine.settings().activeSeeds());
  }

  @Test
  void pausingIsVisibleWithoutAskingTheSession() throws Exception {
    local = LocalEngine.start(dir);

    local.engine.pause();
    assertTrue(local.engine.isPausedCached());
    assertTrue(local.engine.isPaused());

    local.engine.resume();
    assertFalse(local.engine.isPausedCached());
    assertFalse(local.engine.isPaused());
  }

  @Test
  void theSavedStateIsTaggedWithItsVersion() throws Exception {
    local = LocalEngine.start(dir);

    byte[] state = local.engine.saveState();

    assertNotNull(state);
    entry e = entry.bdecode(Vectors.bytes2byte_vector(state));
    assertEquals("1.2.0.6", e.dict().get("state_version").string());
  }

  @Test
  void anEngineWithoutASessionDoesNothing() throws Exception {
    local = LocalEngine.create(dir);
    BTEngine engine = local.engine;
    File settings = engine.settingsFile();
    Files.delete(settings.toPath());
    File dataDir = local.ctx.dataDir;

    assertNull(engine.saveState());
    engine.revertToDefaultConfiguration();
    engine.moveStorage(dir.resolve("elsewhere").toFile());
    engine.restoreDownloads();
    engine.download(dir.resolve("missing.torrent").toFile(), null, null);
    engine.download("magnet:?xt=urn:btih:" + "a".repeat(40), null, null);
    engine.download((TorrentInfo) null, null, null, null);
    engine.download((TorrentCrawledSearchResult) null, null);

    assertSame(dataDir, local.ctx.dataDir, "the data folder did not move");
    assertFalse(settings.exists(), "no state was saved");
    assertEquals(0, local.events.added.size());
    assertNull(engine.getExternalIp());
  }

  @Test
  void movingTheStorageChangesTheDataFolder() throws Exception {
    local = LocalEngine.start(dir);
    File elsewhere = Files.createDirectories(dir.resolve("elsewhere")).toFile();

    local.engine.moveStorage(elsewhere);

    assertEquals(elsewhere, local.ctx.dataDir);
  }

  @Test
  void theEngineIsReadyOnceItsContextIsSetUp() throws Exception {
    BTContext saved = BTEngine.ctx;
    try {
      BTEngine.ctx = null;
      assertNull(BTEngine.getInstanceIfReady(), "not ready before startup sets up the context");

      BTEngine.ctx = new BTContext();
      BTEngine ready = BTEngine.getInstanceIfReady();

      assertNotNull(ready);
      assertSame(ready, BTEngine.getInstance());
      assertSame(ready, BTEngine.getInstanceIfReady(), "always the same engine");
    } finally {
      BTEngine.ctx = saved;
    }
  }

  private void awaitFinished(TorrentInfo ti) {
    await(
        () -> {
          var th = local.engine.find(ti);
          return th != null && th.isValid() && th.status(true).isFinished();
        },
        ti.name() + " finished");
  }

  private TorrentInfo album(String name) throws Exception {
    Path folder = dir.resolve("content").resolve(name);
    LocalEngine.content(folder.resolve("one.mp3"), 64 * 1024, name.hashCode());
    return LocalEngine.torrent(folder.toFile(), null);
  }

  private static String stateVersion(File settings) throws Exception {
    entry e = entry.bdecode(Vectors.bytes2byte_vector(Files.readAllBytes(settings.toPath())));
    return e.dict().get("state_version").string();
  }
}
