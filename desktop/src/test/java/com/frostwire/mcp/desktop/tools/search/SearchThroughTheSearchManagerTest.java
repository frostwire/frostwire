/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.mcp.MCPServer;
import com.frostwire.mcp.desktop.EngineContext;
import com.frostwire.mcp.desktop.notifications.MCPNotificationBridge;
import com.frostwire.mcp.transport.MCPTransport;
import com.frostwire.mcp.transport.MCPTransportHandler;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchManager;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.LocalSharedTorrent;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.search.SearchEngine;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A search started over MCP runs through the SearchManager like any other: its results and its end
 * must reach the MCP search session, where frostwire_search_status and frostwire_search_results
 * read them. The search runs on the local index engine, which answers from memory, with the MCP
 * server's notification bridge installed as the running app has it.
 */
class SearchThroughTheSearchManagerTest {

  @TempDir Path dir;

  private SearchEngine local;
  private LocalIndex previousIndex;
  private boolean localEnabled;
  private boolean includeInactive;
  private EngineContext engine;
  private SearchListener previousListener;

  @BeforeEach
  void installTheLocalEngineAndTheBridge() {
    local =
        SearchEngine.getEngines().stream()
            .filter(e -> e.getId() == SearchEngine.SearchEngineID.LOCAL_ID)
            .findFirst()
            .orElseThrow();
    previousIndex = local.getLocalIndex();
    localEnabled = SearchEnginesSettings.LOCAL_SEARCH_ENABLED.getValue();
    includeInactive = SearchEnginesSettings.LOCAL_SEARCH_INCLUDE_INACTIVE.getValue();
    SearchEnginesSettings.LOCAL_SEARCH_ENABLED.setValue(true);
    // Shared torrents count without a live transfer, so no transfer table is consulted.
    SearchEnginesSettings.LOCAL_SEARCH_INCLUDE_INACTIVE.setValue(true);
    local.setLocalIndex(
        new NamedIndex(List.of(torrent("Ubuntu 24.04 desktop", 1), torrent("Ubuntu server", 2))));

    engine = EngineContext.install(dir.toFile());
    previousListener = SearchManager.getInstance().getListener();
    MCPServer server = new MCPServer("test");
    server.setTransport(new SilentTransport());
    MCPNotificationBridge.instance().setServer(server);
  }

  @AfterEach
  void restore() {
    MCPNotificationBridge.instance().setServer(null);
    SearchManager.getInstance().setListener(previousListener);
    engine.close();
    local.setLocalIndex(previousIndex);
    SearchEnginesSettings.LOCAL_SEARCH_ENABLED.setValue(localEnabled);
    SearchEnginesSettings.LOCAL_SEARCH_INCLUDE_INACTIVE.setValue(includeInactive);
  }

  @Test
  void aSearchCompletesWithTheResultsItsEnginesFound() throws InterruptedException {
    JsonObject args = new JsonObject();
    args.addProperty("keywords", "ubuntu");
    JsonArray engines = new JsonArray();
    engines.add("LOCAL_ID");
    args.add("engines", engines);
    String token = new SearchTool().execute(args).get("token").getAsString();

    JsonObject tokenArgs = new JsonObject();
    tokenArgs.addProperty("token", token);
    JsonObject status = new SearchStatusTool().execute(tokenArgs);
    long deadline = System.currentTimeMillis() + 10_000;
    while (!"complete".equals(status.get("status").getAsString())
        && System.currentTimeMillis() < deadline) {
      Thread.sleep(20);
      status = new SearchStatusTool().execute(tokenArgs);
    }

    assertEquals("complete", status.get("status").getAsString(), status.toString());
    assertEquals(2, status.get("resultCount").getAsInt());
    JsonArray results = new SearchResultsTool().execute(tokenArgs).getAsJsonArray("results");
    assertEquals(2, results.size());
    assertTrue(
        results.get(0).getAsJsonObject().get("displayName").getAsString().startsWith("Ubuntu"));
  }

  private static LocalSharedTorrent torrent(String name, int seed) {
    byte[] hash = new byte[20];
    Arrays.fill(hash, (byte) seed);
    byte[] pub = new byte[32];
    Arrays.fill(pub, (byte) 9);
    return new LocalSharedTorrent.Builder()
        .infoHash(hash)
        .name(name)
        .sizeBytes(1_000)
        .fileCount(1)
        .publisherNodeId(new byte[20])
        .publisherEd25519Pub(pub)
        .addedAt(1)
        .lastSeenAt(1)
        .build();
  }

  /** Torrents whose names contain the query. */
  private record NamedIndex(List<LocalSharedTorrent> torrents) implements LocalIndex {
    @Override
    public List<LocalSharedTorrent> search(String query, int limit) {
      return torrents.stream()
          .filter(t -> t.name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
          .limit(limit)
          .toList();
    }

    @Override
    public void upsert(LocalSharedTorrent torrent) {}

    @Override
    public void delete(String infoHashHex) {}

    @Override
    public Optional<LocalSharedTorrent> get(String infoHashHex) {
      return Optional.empty();
    }

    @Override
    public void markPublished(String infoHashHex, long timestamp) {}

    @Override
    public List<String> needsRepublish(long nowSec, long thresholdSec) {
      return List.of();
    }

    @Override
    public void updateLastSeen(String infoHashHex, long ts) {}

    @Override
    public int size() {
      return torrents.size();
    }
  }

  private static final class SilentTransport implements MCPTransport {
    @Override
    public void start(MCPTransportHandler handler) {}

    @Override
    public void stop() {}

    @Override
    public void sendNotification(JsonObject notification) {}

    @Override
    public boolean isRunning() {
      return true;
    }
  }
}
