/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTEngineListener;
import com.frostwire.mcp.MCPServer;
import com.frostwire.mcp.desktop.EngineContext;
import com.frostwire.mcp.desktop.adapters.TestTransfers;
import com.frostwire.mcp.desktop.adapters.TestTransfers.Download;
import com.frostwire.mcp.transport.MCPTransport;
import com.frostwire.mcp.transport.MCPTransportHandler;
import com.frostwire.search.HttpSearchResult;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchManager;
import com.frostwire.search.SearchResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The bridge turns engine and search events into MCP notifications for connected agents, and passes
 * every event on to the listeners it stands in front of. It is installed on a server whose
 * transport records what it is asked to send; the engine and search listeners are put back after
 * each test.
 */
class MCPNotificationBridgeTest {

  @TempDir Path dir;

  private final MCPNotificationBridge bridge = MCPNotificationBridge.instance();
  private final List<String> engineEvents = new ArrayList<>();
  private final List<String> searchEvents = new ArrayList<>();
  private final RecordingTransport transport = new RecordingTransport();
  private EngineContext context;
  private BTEngineListener originalEngineListener;
  private SearchListener originalSearchListener;
  private BTEngine engine;

  @BeforeEach
  void installTheBridge() {
    context = EngineContext.install(dir.toFile());
    engine = BTEngine.getInstance();
    originalEngineListener = engine.getListener();
    originalSearchListener = SearchManager.getInstance().getListener();
    engine.setListener(new RecordingEngineListener());
    SearchManager.getInstance().setListener(new RecordingSearchListener());

    MCPServer server = new MCPServer("test");
    server.setTransport(transport);
    bridge.setServer(server);
  }

  @AfterEach
  void removeTheBridge() {
    bridge.setServer(null);
    engine.setListener(originalEngineListener);
    SearchManager.getInstance().setListener(originalSearchListener);
    context.close();
  }

  @Test
  void theBridgeStandsInFrontOfTheEngineAndSearchListenersUntilRemoved() {
    assertSame(bridge, engine.getListener());
    assertSame(bridge, SearchManager.getInstance().getListener());

    bridge.setServer(null);

    assertTrue(engine.getListener() instanceof RecordingEngineListener);
    assertTrue(SearchManager.getInstance().getListener() instanceof RecordingSearchListener);
  }

  @Test
  void transferEventsBecomeStateNotificationsAndArePassedOn() throws Exception {
    Download download = TestTransfers.download(TestTransfers.torrent(dir, "album", 1_000), dir);
    BTDownload dl = download.download();

    bridge.started(engine);
    bridge.downloadAdded(engine, dl);

    JsonObject added = transport.only("notifications/transfer_state_changed");
    assertEquals(download.id(), added.get("downloadId").getAsString());
    assertEquals("UNKNOWN", added.get("oldState").getAsString());
    assertEquals("STOPPED", added.get("newState").getAsString());
    assertEquals(0, added.get("progress").getAsInt());

    bridge.downloadUpdate(engine, dl);
    assertTrue(transport.sent.isEmpty(), "same state, nothing to tell");

    bridge.stopped(engine);
    bridge.downloadUpdate(engine, dl);
    JsonObject afterRestart = transport.only("notifications/transfer_state_changed");
    assertEquals("UNKNOWN", afterRestart.get("oldState").getAsString(), "stopping forgets states");

    bridge.downloadFinished(engine, dl);
    bridge.downloadAdded(engine, null);
    bridge.downloadUpdate(engine, null);
    assertTrue(transport.sent.isEmpty());

    assertEquals(
        List.of(
            "started",
            "added " + download.id(),
            "update " + download.id(),
            "stopped",
            "update " + download.id(),
            "finished " + download.id(),
            "added null",
            "update null"),
        engineEvents);
  }

  @Test
  void searchResultsSendACountAndAPreviewOfTheFirstFive() {
    List<SearchResult> results = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      results.add(new CloudResult("song" + i + ".mp3"));
    }

    bridge.onResults(42, results);

    JsonObject notification = transport.only("notifications/search_result");
    assertEquals("42", notification.get("token").getAsString());
    assertEquals(7, notification.get("resultCount").getAsInt());
    JsonArray preview = notification.getAsJsonArray("resultsPreview");
    assertEquals(5, preview.size());
    assertEquals("song0.mp3", preview.get(0).getAsJsonObject().get("displayName").getAsString());

    bridge.onResults(42, List.of());
    assertTrue(transport.sent.isEmpty(), "no results, no notification");

    bridge.onStopped(42);
    assertEquals("42", transport.only("notifications/search_completed").get("token").getAsString());

    bridge.onError(42, new SearchError(500, "boom"));
    assertTrue(transport.sent.isEmpty(), "errors are only passed on");

    assertEquals(List.of("results 7", "results 0", "stopped", "error boom"), searchEvents);
  }

  @Test
  void nothingIsSentWithoutAServer() {
    bridge.setServer(null);
    transport.sent.clear();

    bridge.onResults(1, List.of(new CloudResult("x")));
    bridge.onStopped(1);

    assertTrue(transport.sent.isEmpty());
  }

  @Test
  void notificationsCarryTheirPayloads() {
    TransferProgressNotification progress =
        new TransferProgressNotification("abc", 40, 1_000, 200, 60, 4_000, 800);
    assertEquals("notifications/transfer_progress", progress.method());
    JsonObject p = progress.payload();
    assertEquals("abc", p.get("downloadId").getAsString());
    assertEquals(40, p.get("progress").getAsInt());
    assertEquals(1_000, p.get("downloadSpeed").getAsLong());
    assertEquals(200, p.get("uploadSpeed").getAsLong());
    assertEquals(60, p.get("eta").getAsLong());
    assertEquals(4_000, p.get("downloaded").getAsLong());
    assertEquals(800, p.get("uploaded").getAsLong());

    VPNStatusNotification vpn = new VPNStatusNotification(true, false);
    assertEquals("notifications/vpn_status_changed", vpn.method());
    assertTrue(vpn.payload().get("vpnActive").getAsBoolean());
    assertFalse(vpn.payload().get("dropProtectionEnabled").getAsBoolean());

    SearchResultNotification empty = new SearchResultNotification("9", 0, new JsonArray());
    assertFalse(empty.payload().has("resultsPreview"), "an empty preview is left out");
    assertFalse(new SearchResultNotification("9", 0, null).payload().has("resultsPreview"));

    TransferStateNotification state =
        new TransferStateNotification("abc", "PAUSED", "SEEDING", 100);
    assertEquals("SEEDING", state.payload().get("newState").getAsString());
    assertEquals("notifications/search_completed", new SearchCompletedNotification("1").method());
  }

  private final class RecordingEngineListener implements BTEngineListener {
    @Override
    public void started(BTEngine engine) {
      engineEvents.add("started");
    }

    @Override
    public void stopped(BTEngine engine) {
      engineEvents.add("stopped");
    }

    @Override
    public void downloadAdded(BTEngine engine, BTDownload dl) {
      engineEvents.add("added " + (dl == null ? null : dl.getInfoHash()));
    }

    @Override
    public void downloadUpdate(BTEngine engine, BTDownload dl) {
      engineEvents.add("update " + (dl == null ? null : dl.getInfoHash()));
    }

    @Override
    public void downloadFinished(BTEngine engine, BTDownload dl) {
      engineEvents.add("finished " + dl.getInfoHash());
    }
  }

  private final class RecordingSearchListener implements SearchListener {
    @Override
    public void onResults(long token, List<? extends SearchResult> results) {
      searchEvents.add("results " + results.size());
    }

    @Override
    public void onError(long token, SearchError error) {
      searchEvents.add("error " + error.message());
    }

    @Override
    public void onStopped(long token) {
      searchEvents.add("stopped");
    }
  }

  /** Keeps the notifications the server sends. */
  private static final class RecordingTransport implements MCPTransport {
    private final List<JsonObject> sent = new ArrayList<>();

    /** The one notification sent since the last call, which must be {@code method}; its params. */
    JsonObject only(String method) {
      assertEquals(1, sent.size(), sent.toString());
      JsonObject notification = sent.remove(0);
      assertEquals(method, notification.get("method").getAsString());
      return notification.getAsJsonObject("params");
    }

    @Override
    public void start(MCPTransportHandler handler) {}

    @Override
    public void stop() {}

    @Override
    public void sendNotification(JsonObject notification) {
      sent.add(notification);
    }

    @Override
    public boolean isRunning() {
      return true;
    }
  }

  private record CloudResult(String name) implements HttpSearchResult {
    @Override
    public String getDisplayName() {
      return name;
    }

    @Override
    public String getDetailsUrl() {
      return null;
    }

    @Override
    public long getCreationTime() {
      return 0;
    }

    @Override
    public String getSource() {
      return "test";
    }

    @Override
    public com.frostwire.licenses.License getLicense() {
      return null;
    }

    @Override
    public String getThumbnailUrl() {
      return null;
    }

    @Override
    public String getFilename() {
      return name;
    }

    @Override
    public long getSize() {
      return 1;
    }

    @Override
    public String getDownloadUrl() {
      return null;
    }
  }
}
