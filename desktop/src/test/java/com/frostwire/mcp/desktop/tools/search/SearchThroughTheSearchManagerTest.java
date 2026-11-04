/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.frostwire.mcp.MCPServer;
import com.frostwire.mcp.desktop.EngineContext;
import com.frostwire.mcp.desktop.notifications.MCPNotificationBridge;
import com.frostwire.mcp.transport.MCPTransport;
import com.frostwire.mcp.transport.MCPTransportHandler;
import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchManager;
import com.frostwire.search.SearchResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A search started over MCP runs through the SearchManager like any other: its results and its end
 * must reach the MCP search session, where frostwire_search_status and frostwire_search_results
 * read them. The MCP server's notification bridge is installed as the running app has it, and the
 * search's work is a performer that answers from memory, handed to the SearchManager under the
 * session's token, as an engine's performer would be.
 */
class SearchThroughTheSearchManagerTest {

  @TempDir Path dir;

  private EngineContext engine;
  private SearchListener previousListener;

  @BeforeEach
  void installTheBridge() {
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
  }

  @Test
  void aSearchCompletesWithTheResultsItsEnginesFound() throws InterruptedException {
    JsonObject args = new JsonObject();
    args.addProperty("keywords", "ubuntu");
    JsonArray engines = new JsonArray();
    engines.add("NO_SUCH_ENGINE_ID");
    args.add("engines", engines);
    String token = new SearchTool().execute(args).get("token").getAsString();
    SearchManager.getInstance()
        .perform(
            new MemoryPerformer(
                Long.parseLong(token),
                List.of(new Result("Ubuntu 24.04 desktop"), new Result("Ubuntu server"))));

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
    assertEquals(
        "Ubuntu 24.04 desktop", results.get(0).getAsJsonObject().get("displayName").getAsString());
  }

  /** Reports its results once, like a search engine page, to whatever listener it was given. */
  private static final class MemoryPerformer implements ISearchPerformer {
    private final long token;
    private final List<SearchResult> results;
    private volatile SearchListener listener;
    private volatile boolean stopped;

    MemoryPerformer(long token, List<SearchResult> results) {
      this.token = token;
      this.results = results;
    }

    @Override
    public long getToken() {
      return token;
    }

    @Override
    public void perform() {
      listener.onResults(token, results);
    }

    @Override
    public void crawl(CrawlableSearchResult sr) {}

    @Override
    public void stop() {
      stopped = true;
    }

    @Override
    public boolean isStopped() {
      return stopped;
    }

    @Override
    public SearchListener getListener() {
      return listener;
    }

    @Override
    public void setListener(SearchListener listener) {
      this.listener = listener;
    }

    @Override
    public boolean isDDOSProtectionActive() {
      return false;
    }

    @Override
    public boolean isCrawler() {
      return false;
    }
  }

  private record Result(String name) implements SearchResult {
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
      return "memory";
    }

    @Override
    public com.frostwire.licenses.License getLicense() {
      return null;
    }

    @Override
    public String getThumbnailUrl() {
      return null;
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
