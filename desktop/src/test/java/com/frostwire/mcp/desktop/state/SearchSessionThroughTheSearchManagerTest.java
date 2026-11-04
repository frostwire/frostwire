/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.mcp.MCPServer;
import com.frostwire.mcp.desktop.EngineContext;
import com.frostwire.mcp.desktop.notifications.MCPNotificationBridge;
import com.frostwire.mcp.desktop.tools.search.SearchResultsTool;
import com.frostwire.mcp.desktop.tools.search.SearchStatusTool;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An MCP search runs through the SearchManager like any other: its results and its end must reach
 * the MCP search session, where frostwire_search_status and frostwire_search_results read them, and
 * the session may only end once every performer has been handed over and has finished. The MCP
 * server's notification bridge is installed as the running app has it; the performers answer from
 * memory, as an engine's performers would from the web.
 */
class SearchSessionThroughTheSearchManagerTest {

  private static final long WAIT_MS = 10_000;

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
  void aSearchCompletesWithTheResultsItsPerformersFound() throws InterruptedException {
    SearchSession session =
        SearchSessionManager.instance()
            .startSearch(
                "ubuntu",
                token ->
                    List.of(
                        new MemoryPerformer(
                            token,
                            List.of(
                                new Result("Ubuntu 24.04 desktop"), new Result("Ubuntu server")))));

    JsonObject status = waitUntilComplete(session.getToken());

    assertEquals("complete", status.get("status").getAsString(), status.toString());
    assertEquals(2, status.get("resultCount").getAsInt());
    JsonArray results =
        new SearchResultsTool().execute(token(session.getToken())).getAsJsonArray("results");
    assertEquals(
        "Ubuntu 24.04 desktop", results.get(0).getAsJsonObject().get("displayName").getAsString());
  }

  @Test
  void aSearchDoesNotEndWhileAPerformerIsStillBeingHandedOver() throws InterruptedException {
    CountDownLatch quickOneAnswered = new CountDownLatch(1);
    CountDownLatch releaseSlowOne = new CountDownLatch(1);
    MemoryPerformer[] quick = new MemoryPerformer[1];
    MemoryPerformer[] slow = new MemoryPerformer[1];
    SearchSession session =
        SearchSessionManager.instance()
            .startSearch(
                "linux",
                token -> {
                  quick[0] =
                      new MemoryPerformer(token, List.of(new Result("quick"))) {
                        @Override
                        public void perform() {
                          super.perform();
                          quickOneAnswered.countDown();
                        }
                      };
                  // Handed over only after the quick one has answered and its task has ended,
                  // when the SearchManager has nothing left to run for the search.
                  slow[0] =
                      new MemoryPerformer(token, List.of(new Result("slow"))) {
                        @Override
                        public void setListener(SearchListener listener) {
                          awaitQuietly(quickOneAnswered);
                          sleepQuietly(200);
                          super.setListener(listener);
                        }

                        @Override
                        public void perform() {
                          awaitQuietly(releaseSlowOne);
                          super.perform();
                        }
                      };
                  return List.of(quick[0], slow[0]);
                });

    JsonObject running = new SearchStatusTool().execute(token(session.getToken()));
    assertEquals("running", running.get("status").getAsString(), "the slow performer is working");
    assertEquals(1, running.get("resultCount").getAsInt());
    assertFalse(session.isComplete());

    releaseSlowOne.countDown();
    JsonObject done = waitUntilComplete(session.getToken());
    assertEquals("complete", done.get("status").getAsString(), done.toString());
    assertEquals(2, done.get("resultCount").getAsInt(), "no result came after the search ended");
  }

  @Test
  void aSearchWhosePerformersAllFinishWhileBeingHandedOverStillEnds() throws InterruptedException {
    List<ISearchPerformer> performers = new ArrayList<>();
    SearchSession session =
        SearchSessionManager.instance()
            .startSearch(
                "fast",
                token -> {
                  for (int i = 0; i < 3; i++) {
                    performers.add(new MemoryPerformer(token, List.of(new Result("r" + i))));
                  }
                  return performers;
                });

    JsonObject done = waitUntilComplete(session.getToken());

    assertEquals("complete", done.get("status").getAsString(), done.toString());
    assertEquals(3, done.get("resultCount").getAsInt());
  }

  @Test
  void aSearchWithNothingToRunIsCompleteAtOnce() {
    SearchSession session =
        SearchSessionManager.instance().startSearch("nothing", token -> List.of());
    assertTrue(session.isComplete());
    assertEquals(
        "complete",
        new SearchStatusTool().execute(token(session.getToken())).get("status").getAsString());
  }

  private static JsonObject waitUntilComplete(long token) throws InterruptedException {
    JsonObject args = token(token);
    JsonObject status = new SearchStatusTool().execute(args);
    long deadline = System.currentTimeMillis() + WAIT_MS;
    while (!"complete".equals(status.get("status").getAsString())
        && System.currentTimeMillis() < deadline) {
      Thread.sleep(20);
      status = new SearchStatusTool().execute(args);
    }
    return status;
  }

  private static JsonObject token(long token) {
    JsonObject args = new JsonObject();
    args.addProperty("token", String.valueOf(token));
    return args;
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(WAIT_MS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static void sleepQuietly(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Reports its results once, like a search engine page, to whatever listener it was given. */
  private static class MemoryPerformer implements ISearchPerformer {
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
