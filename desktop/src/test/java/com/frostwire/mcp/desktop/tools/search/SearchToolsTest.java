/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.licenses.Licenses;
import com.frostwire.mcp.desktop.adapters.SearchAdapter;
import com.frostwire.mcp.desktop.state.SearchSession;
import com.frostwire.mcp.desktop.state.SearchSessionManager;
import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.HttpSearchResult;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.search.SearchEngine;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The MCP search tools: starting a search, polling its status, paging, filtering and sorting its
 * results, cancelling it, and listing and toggling the engines. Searches are started on an engine
 * id that matches no engine, so no performer runs and nothing goes to the network; results are fed
 * to the session through the manager's search listener, as the performers would.
 */
class SearchToolsTest {

  private static final String NO_ENGINE = "NO_SUCH_ENGINE_ID";

  @Test
  void searchStartsASessionAndReturnsItsToken() {
    JsonObject started = new SearchTool().execute(search("ubuntu iso"));

    assertEquals("started", started.get("status").getAsString());
    assertEquals("ubuntu iso", started.get("keywords").getAsString());
    SearchSession session = session(started);
    assertEquals("ubuntu iso", session.getKeywords());
    assertTrue(session.getResults().isEmpty());
    assertFalse(session.isComplete());
    assertTrue(session.getCreatedAt() <= System.currentTimeMillis());

    JsonObject other = new SearchTool().execute(search("debian"));
    assertTrue(
        Long.parseLong(other.get("token").getAsString())
            > Long.parseLong(started.get("token").getAsString()),
        "every search gets a new token");
  }

  @Test
  void statusReportsRunningCompleteAndErrorStates() {
    JsonObject started = new SearchTool().execute(search("linux"));
    String token = started.get("token").getAsString();
    long id = Long.parseLong(token);
    SearchSessionManager manager = SearchSessionManager.instance();

    JsonObject running = new SearchStatusTool().execute(token(token));
    assertEquals("running", running.get("status").getAsString());
    assertEquals(0, running.get("resultCount").getAsInt());
    assertEquals("linux", running.get("keywords").getAsString());

    manager.onResults(id, List.of(torrent("a", 1, 1, "tpb", 1)));
    manager.onStopped(id);
    JsonObject complete = new SearchStatusTool().execute(token(token));
    assertEquals("complete", complete.get("status").getAsString(), "no performer is left running");
    assertEquals(1, complete.get("resultCount").getAsInt());

    manager.onError(id, new SearchError(503, "engine unavailable"));
    JsonObject failed = new SearchStatusTool().execute(token(token));
    assertEquals("error", failed.get("status").getAsString());
    assertEquals("engine unavailable", failed.get("error").getAsString());
  }

  @Test
  void toolsReportUnknownTokens() {
    String unknown = String.valueOf(Long.MAX_VALUE);

    JsonObject status = new SearchStatusTool().execute(token(unknown));
    assertEquals("not_found", status.get("status").getAsString());
    assertEquals("No search session found for token " + unknown, status.get("error").getAsString());

    JsonObject results = new SearchResultsTool().execute(token(unknown));
    assertEquals(
        "No search session found for token " + unknown, results.get("error").getAsString());
    assertFalse(results.has("results"));

    JsonObject cancel = new SearchCancelTool().execute(token(unknown));
    assertFalse(cancel.get("cancelled").getAsBoolean());
    assertEquals("No search session found for token " + unknown, cancel.get("error").getAsString());
  }

  @Test
  void resultsCanBeFilteredByTypeSizeSeedsAndSource() {
    String token = searchWith(sampleResults());

    assertEquals(
        List.of("ubuntu.iso", "debian.iso"), names(results(token, filter("fileType", "torrent"))));
    assertEquals(
        List.of("song.mp3", "video.mp4"), names(results(token, filter("fileType", "cloud"))));

    JsonObject sized = new JsonObject();
    sized.addProperty("minSize", 1_600_000);
    sized.addProperty("maxSize", 4_000_000);
    assertEquals(
        List.of("ubuntu.iso", "song.mp3", "page"),
        names(results(token, filter(sized))),
        "a result without a size is not filtered by size");

    JsonObject seeded = new JsonObject();
    seeded.addProperty("minSeeds", 100);
    assertEquals(
        List.of("ubuntu.iso", "song.mp3", "video.mp4", "page"),
        names(results(token, filter(seeded))),
        "the seed filter only applies to torrents");

    assertEquals(List.of("ubuntu.iso"), names(results(token, filter("source", "TPB"))));
  }

  @Test
  void resultsCanBeSortedBySeedsSizeDateAndName() {
    String token = searchWith(sampleResults());

    assertEquals(
        List.of("ubuntu.iso", "debian.iso", "song.mp3", "video.mp4", "page"),
        names(results(token, sortBy("seeds"))));
    assertEquals(
        List.of("video.mp4", "ubuntu.iso", "song.mp3", "debian.iso", "page"),
        names(results(token, sortBy("size"))),
        "largest first; a result without a size counts as 0");
    assertEquals(
        List.of("page", "video.mp4", "song.mp3", "debian.iso", "ubuntu.iso"),
        names(results(token, sortBy("creationTime"))),
        "newest first");
    assertEquals(
        List.of("debian.iso", "page", "song.mp3", "ubuntu.iso", "video.mp4"),
        names(results(token, sortBy("displayName"))));
    assertEquals(
        List.of("ubuntu.iso", "debian.iso", "song.mp3", "video.mp4", "page"),
        names(results(token, sortBy("relevance"))),
        "an unknown sort keeps arrival order");
  }

  @Test
  void resultsArePagedWithTotalsAndHasMore() {
    String token = searchWith(sampleResults());

    JsonObject args = token(token);
    args.addProperty("offset", 1);
    args.addProperty("limit", 2);
    JsonObject page = new SearchResultsTool().execute(args);
    assertEquals(List.of("debian.iso", "song.mp3"), names(page));
    assertEquals(5, page.get("totalResults").getAsInt());
    assertEquals(1, page.get("offset").getAsInt());
    assertEquals(2, page.get("limit").getAsInt());
    assertTrue(page.get("hasMore").getAsBoolean());

    args.addProperty("offset", 99);
    JsonObject past = new SearchResultsTool().execute(args);
    assertEquals(0, past.getAsJsonArray("results").size());
    assertEquals(5, past.get("offset").getAsInt(), "the offset is clamped to the result count");
    assertFalse(past.get("hasMore").getAsBoolean());

    JsonObject all = new SearchResultsTool().execute(token(token));
    assertEquals(5, all.getAsJsonArray("results").size());
    assertEquals(50, all.get("limit").getAsInt(), "default page size");
    assertFalse(all.get("hasMore").getAsBoolean());
  }

  @Test
  void resultsDescribeTorrentAndCloudFiles() {
    TorrentSearchResult torrent = torrent("ubuntu.iso", 3_000_000, 120, "TPB", 10);
    HttpSearchResult cloud = cloud("song.mp3", 2_000_000, "SoundCloud", 30);
    String token = searchWith(List.of(torrent, cloud, plain("page", 50)));

    JsonArray results = new SearchResultsTool().execute(token(token)).getAsJsonArray("results");

    JsonObject t = results.get(0).getAsJsonObject();
    assertEquals("torrent", t.get("fileType").getAsString());
    assertEquals("ubuntu.iso", t.get("filename").getAsString());
    assertEquals(3_000_000, t.get("size").getAsLong());
    assertEquals(120, t.get("seeds").getAsInt());
    assertEquals("hash-ubuntu.iso", t.get("hash").getAsString());
    assertEquals("https://t/ubuntu.iso.torrent", t.get("torrentUrl").getAsString());
    assertEquals("https://d/ubuntu.iso", t.get("detailsUrl").getAsString());
    assertEquals("TPB", t.get("source").getAsString());
    assertEquals(10, t.get("creationTime").getAsLong());
    assertEquals(Licenses.APACHE.getName(), t.get("license").getAsString());

    JsonObject c = results.get(1).getAsJsonObject();
    assertEquals("cloud", c.get("fileType").getAsString());
    assertEquals("https://dl/song.mp3", c.get("downloadUrl").getAsString());
    assertEquals(2_000_000, c.get("size").getAsLong());
    assertFalse(c.has("seeds"));

    JsonObject p = results.get(2).getAsJsonObject();
    assertFalse(p.has("fileType"), "neither a torrent nor a cloud file");
    assertEquals("", p.get("detailsUrl").getAsString(), "missing urls are empty strings");
    assertEquals("", p.get("thumbnailUrl").getAsString());
    assertEquals("", p.get("license").getAsString());
  }

  @Test
  void searchAdapterUsesEmptyStringsForMissingTorrentFields() {
    FakeTorrent bare = new FakeTorrent("bare", 1, 0, "x", 0);
    bare.hash = null;
    bare.torrentUrl = null;
    JsonObject json = SearchAdapter.toMCPJson(bare);
    assertEquals("", json.get("hash").getAsString());
    assertEquals("", json.get("torrentUrl").getAsString());

    FakeCloud noUrl = new FakeCloud("c", 1, "x", 0);
    noUrl.downloadUrl = null;
    assertEquals("", SearchAdapter.toMCPJson(noUrl).get("downloadUrl").getAsString());
  }

  @Test
  void cancelStopsEveryPerformerAndCompletesTheSession() {
    FakePerformer first = new FakePerformer();
    FakePerformer second = new FakePerformer();
    SearchSession session = new SearchSession(7, "k", List.of(first, second));
    assertFalse(session.allPerformersStopped());
    first.stop();
    assertFalse(session.allPerformersStopped(), "one performer is still running");

    session.cancel();

    assertTrue(second.isStopped());
    assertTrue(session.allPerformersStopped());
    assertTrue(session.isComplete());
    assertEquals(7, session.getToken());
    assertNull(session.getError());
  }

  @Test
  void cancelToolCancelsTheSession() {
    JsonObject started = new SearchTool().execute(search("cancel me"));
    String token = started.get("token").getAsString();

    JsonObject cancelled = new SearchCancelTool().execute(token(token));

    assertTrue(cancelled.get("cancelled").getAsBoolean());
    assertEquals(token, cancelled.get("token").getAsString());
    assertTrue(session(started).isComplete());
  }

  @Test
  void managerIgnoresEventsForUnknownTokensAndCleanupKeepsFreshSessions() {
    SearchSessionManager manager = SearchSessionManager.instance();
    long unknown = Long.MAX_VALUE - 1;
    manager.onResults(unknown, List.of(plain("x", 1)));
    manager.onStopped(unknown);
    manager.onError(unknown, new SearchError(1, "x"));
    assertNull(manager.getSession(unknown));

    JsonObject started = new SearchTool().execute(search("fresh"));
    SearchSession session = session(started);
    session.setComplete();
    manager.cleanup();
    assertSame(session, manager.getSession(session.getToken()), "sessions live for ten minutes");
  }

  @Test
  void enginesAreListedWithTheirTypeAndState() {
    JsonArray engines = new SearchEnginesTool().execute(new JsonObject()).getAsJsonArray("engines");
    assertEquals(SearchEngine.getEngines().size(), engines.size());

    Set<String> ids = new HashSet<>();
    for (int i = 0; i < engines.size(); i++) {
      JsonObject engine = engines.get(i).getAsJsonObject();
      String id = engine.get("id").getAsString();
      ids.add(id);
      SearchEngine real = SearchEngine.getEngines().get(i);
      assertEquals(real.getId().name(), id);
      assertEquals(real.getName(), engine.get("name").getAsString());
      assertEquals(real.isEnabled(), engine.get("enabled").getAsBoolean());
      String expectedType =
          switch (id) {
            case "SOUNDCLOUD_ID", "INTERNET_ARCHIVE_ID", "YT_ID", "TELLURIDE_ID" -> "cloud";
            case "FROSTCLICK_ID" -> "direct";
            default -> "torrent";
          };
      assertEquals(expectedType, engine.get("type").getAsString(), id);
    }
    assertTrue(ids.contains("TPB_ID"));
  }

  @Test
  void toggleEnablesAndDisablesAnEngine() {
    SearchEngine engine = SearchEngine.getEngines().get(0);
    boolean original = engine.getEnabledSetting().getValue();
    try {
      JsonObject args = new JsonObject();
      args.addProperty("engineId", engine.getId().name());
      args.addProperty("enabled", !original);

      JsonObject toggled = new SearchEngineToggleTool().execute(args);

      assertEquals(engine.getId().name(), toggled.get("engineId").getAsString());
      assertEquals(engine.getName(), toggled.get("name").getAsString());
      assertEquals(!original, engine.getEnabledSetting().getValue());
      assertEquals(engine.isEnabled(), toggled.get("enabled").getAsBoolean());
    } finally {
      engine.getEnabledSetting().setValue(original);
    }
  }

  @Test
  void toggleReportsAnUnknownEngine() {
    JsonObject args = new JsonObject();
    args.addProperty("engineId", NO_ENGINE);
    args.addProperty("enabled", true);

    JsonObject out = new SearchEngineToggleTool().execute(args);

    assertEquals("Engine not found: " + NO_ENGINE, out.get("error").getAsString());
    assertFalse(out.get("enabled").getAsBoolean());
  }

  @Test
  void everySearchToolDeclaresItsRequiredArguments() {
    assertRequired(new SearchTool().inputSchema(), "keywords");
    assertRequired(new SearchStatusTool().inputSchema(), "token");
    assertRequired(new SearchResultsTool().inputSchema(), "token");
    assertRequired(new SearchCancelTool().inputSchema(), "token");
    assertRequired(new SearchEngineToggleTool().inputSchema(), "engineId", "enabled");
    assertEquals("object", new SearchEnginesTool().inputSchema().get("type").getAsString());
    assertNotNull(new SearchEnginesTool().description());
  }

  private static void assertRequired(JsonObject schema, String... names) {
    JsonArray required = schema.getAsJsonArray("required");
    assertEquals(names.length, required.size());
    for (int i = 0; i < names.length; i++) {
      assertEquals(names[i], required.get(i).getAsString());
      assertTrue(schema.getAsJsonObject("properties").has(names[i]), names[i]);
    }
  }

  /** Torrents, cloud files and a plain result, in arrival order. */
  private static List<SearchResult> sampleResults() {
    return List.of(
        torrent("ubuntu.iso", 3_000_000, 120, "TPB", 10),
        torrent("debian.iso", 1_500_000, 80, "Nyaa", 20),
        cloud("song.mp3", 2_000_000, "SoundCloud", 30),
        cloud("video.mp4", 9_000_000, "Archive.org", 40),
        plain("page", 50));
  }

  private static String searchWith(List<? extends SearchResult> results) {
    JsonObject started = new SearchTool().execute(search("results"));
    String token = started.get("token").getAsString();
    SearchSessionManager.instance().onResults(Long.parseLong(token), results);
    return token;
  }

  private static JsonObject results(String token, JsonObject extra) {
    JsonObject args = token(token);
    for (String key : extra.keySet()) {
      args.add(key, extra.get(key));
    }
    return new SearchResultsTool().execute(args);
  }

  private static JsonObject filter(String key, String value) {
    JsonObject filter = new JsonObject();
    filter.addProperty(key, value);
    return filter(filter);
  }

  private static JsonObject filter(JsonObject filter) {
    JsonObject extra = new JsonObject();
    extra.add("filter", filter);
    return extra;
  }

  private static JsonObject sortBy(String field) {
    JsonObject extra = new JsonObject();
    extra.addProperty("sortBy", field);
    return extra;
  }

  private static List<String> names(JsonObject page) {
    List<String> names = new ArrayList<>();
    for (var element : page.getAsJsonArray("results")) {
      names.add(element.getAsJsonObject().get("displayName").getAsString());
    }
    return names;
  }

  private static JsonObject search(String keywords) {
    JsonObject args = new JsonObject();
    args.addProperty("keywords", keywords);
    JsonArray engines = new JsonArray();
    engines.add(NO_ENGINE);
    args.add("engines", engines);
    return args;
  }

  private static JsonObject token(String token) {
    JsonObject args = new JsonObject();
    args.addProperty("token", token);
    return args;
  }

  private static SearchSession session(JsonObject started) {
    return SearchSessionManager.instance()
        .getSession(Long.parseLong(started.get("token").getAsString()));
  }

  private static FakeTorrent torrent(String name, long size, int seeds, String source, long time) {
    return new FakeTorrent(name, size, seeds, source, time);
  }

  private static FakeCloud cloud(String name, long size, String source, long time) {
    return new FakeCloud(name, size, source, time);
  }

  private static SearchResult plain(String name, long time) {
    return new FakeResult(name, "Web", time);
  }

  private static class FakeResult implements SearchResult {
    final String name;
    final String source;
    final long time;

    FakeResult(String name, String source, long time) {
      this.name = name;
      this.source = source;
      this.time = time;
    }

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
      return time;
    }

    @Override
    public String getSource() {
      return source;
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

  private static final class FakeTorrent extends FakeResult implements TorrentSearchResult {
    final long size;
    final int seeds;
    String hash;
    String torrentUrl;

    FakeTorrent(String name, long size, int seeds, String source, long time) {
      super(name, source, time);
      this.size = size;
      this.seeds = seeds;
      this.hash = "hash-" + name;
      this.torrentUrl = "https://t/" + name + ".torrent";
    }

    @Override
    public String getDetailsUrl() {
      return "https://d/" + name;
    }

    @Override
    public com.frostwire.licenses.License getLicense() {
      return Licenses.APACHE;
    }

    @Override
    public String getFilename() {
      return name;
    }

    @Override
    public long getSize() {
      return size;
    }

    @Override
    public String getTorrentUrl() {
      return torrentUrl;
    }

    @Override
    public String getReferrerUrl() {
      return null;
    }

    @Override
    public int getSeeds() {
      return seeds;
    }

    @Override
    public String getHash() {
      return hash;
    }
  }

  private static final class FakeCloud extends FakeResult implements HttpSearchResult {
    final long size;
    String downloadUrl;

    FakeCloud(String name, long size, String source, long time) {
      super(name, source, time);
      this.size = size;
      this.downloadUrl = "https://dl/" + name;
    }

    @Override
    public String getFilename() {
      return name;
    }

    @Override
    public long getSize() {
      return size;
    }

    @Override
    public String getDownloadUrl() {
      return downloadUrl;
    }
  }

  private static final class FakePerformer implements ISearchPerformer {
    private boolean stopped;
    private SearchListener listener;

    @Override
    public long getToken() {
      return 7;
    }

    @Override
    public void perform() {}

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
}
