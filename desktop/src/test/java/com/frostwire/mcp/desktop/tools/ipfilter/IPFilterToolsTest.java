/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.ipfilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.frostwire.mcp.desktop.EngineContext;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPRange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.limewire.util.CommonUtils;

/**
 * The MCP IP filter tools list, add, clear and import blocked ranges. The Swing table is replaced
 * by a list (it can't be built without a display), the engine has startup's context but no session,
 * and the home folder, where the filter is saved as ip_filter.db, is a temp folder.
 */
class IPFilterToolsTest {

  private static final String BLOCKLIST =
      String.join(
          "\n",
          "# a PeerGuardian list",
          "",
          "Bad Corp:192.0.2.0-192.0.2.255",
          "Worse Corp:198.51.100.10-198.51.100.20",
          "Broken:not-an-ip-1.2.3.4",
          "garbage line");

  @TempDir Path home;

  private final List<IPRange> table = new ArrayList<>();
  private final StandInTable standIn = new StandInTable();
  private String originalHome;
  private EngineContext engine;
  private File settingsDir;

  @BeforeEach
  void setUp() throws IOException {
    originalHome = System.getProperty("user.home");
    System.setProperty("user.home", home.toString());
    settingsDir = CommonUtils.getUserSettingsDir();
    if (!settingsDir.toPath().startsWith(home)) {
      System.setProperty("user.home", originalHome);
      assumeTrue(false, "the settings folder is fixed for this JVM: " + settingsDir);
    }
    Files.createDirectories(settingsDir.toPath());
    engine = EngineContext.install(home.resolve("engine").toFile());
    IPFilterTableAccess.setStandIn(standIn);
  }

  @AfterEach
  void tearDown() {
    IPFilterTableAccess.setStandIn(null);
    if (engine != null) {
      engine.close();
    }
    System.setProperty("user.home", originalHome);
  }

  @Test
  void listPagesTheRanges() {
    for (int i = 0; i < 5; i++) {
      table.add(new IPRange("range " + i, "10.0.0." + i, "10.0.0." + (i + 100)));
    }

    JsonObject all = new IPFilterListTool().execute(new JsonObject());
    assertEquals(5, all.get("total").getAsInt());
    JsonObject first = all.getAsJsonArray("ranges").get(0).getAsJsonObject();
    assertEquals("10.0.0.0", first.get("start").getAsString());
    assertEquals("10.0.0.100", first.get("end").getAsString());
    assertEquals("range 0", first.get("description").getAsString());

    JsonObject page = new JsonObject();
    page.addProperty("offset", 3);
    page.addProperty("limit", 10);
    JsonArray tail = new IPFilterListTool().execute(page).getAsJsonArray("ranges");
    assertEquals(2, tail.size());
    assertEquals("range 3", tail.get(0).getAsJsonObject().get("description").getAsString());

    page.addProperty("offset", -1);
    page.addProperty("limit", 0);
    assertEquals(
        1, new IPFilterListTool().execute(page).getAsJsonArray("ranges").size(), "limit 1");
  }

  @Test
  void listReportsATableThatCannotBeRead() {
    standIn.failure = new IllegalStateException("no table");

    JsonObject out = new IPFilterListTool().execute(new JsonObject());

    assertEquals("Failed to list IP filter ranges: no table", out.get("error").getAsString());
    assertEquals(0, out.get("total").getAsInt());
    assertEquals(0, out.getAsJsonArray("ranges").size());
  }

  @Test
  void addBlocksAValidRange() {
    JsonObject args = range("203.0.113.1", "203.0.113.9");
    args.addProperty("description", "spammer");

    JsonObject out = new IPFilterAddTool().execute(args);

    assertTrue(out.get("added").getAsBoolean());
    assertEquals("203.0.113.1", out.get("start").getAsString());
    assertEquals("203.0.113.9", out.get("end").getAsString());
    assertEquals("spammer", out.get("description").getAsString());
    assertEquals(1, table.size());
    assertEquals("spammer", table.get(0).description());
    assertEquals("203.0.113.9", table.get(0).endAddress());

    JsonObject noDescription = range("2001:db8::1", "2001:db8::ff");
    noDescription.add("description", new JsonObject());
    assertEquals("", new IPFilterAddTool().execute(noDescription).get("description").getAsString());
  }

  @Test
  void addRejectsInvalidAddressesAndReportsTableFailures() {
    assertEquals(
        "Invalid start IP address: 300.1.1.1",
        new IPFilterAddTool().execute(range("300.1.1.1", "1.1.1.1")).get("error").getAsString());
    assertEquals(
        "Invalid end IP address: nope",
        new IPFilterAddTool().execute(range("1.1.1.1", "nope")).get("error").getAsString());
    assertTrue(table.isEmpty());

    standIn.failure = new IllegalStateException("table gone");
    String error =
        new IPFilterAddTool().execute(range("1.1.1.1", "1.1.1.2")).get("error").getAsString();
    assertTrue(error.startsWith("Failed to add IP filter range: "), error);
  }

  @Test
  void clearEmptiesTheTableAndTheSavedFilter() throws IOException {
    table.add(new IPRange("x", "10.0.0.1", "10.0.0.2"));
    File saved = new File(settingsDir, "ip_filter.db");
    Files.write(saved.toPath(), new byte[] {1, 2, 3});

    JsonObject out = new IPFilterClearTool().execute(new JsonObject());

    assertTrue(out.get("cleared").getAsBoolean());
    assertTrue(table.isEmpty());
    assertTrue(saved.exists(), "an empty filter file is left");
    assertEquals(0, saved.length());
  }

  @Test
  void clearReportsATableThatCannotBeCleared() {
    standIn.failure = new IllegalStateException("busy");
    JsonObject out = new IPFilterClearTool().execute(new JsonObject());
    assertFalse(out.get("cleared").getAsBoolean());
    assertEquals("Failed to clear IP filter table: busy", out.get("error").getAsString());
  }

  @Test
  void importReadsAPeerGuardianFileAndSavesTheFilter() throws IOException {
    table.add(new IPRange("existing", "10.0.0.1", "10.0.0.1"));
    Path list = home.resolve("list.p2p");
    Files.writeString(list, BLOCKLIST);

    JsonObject out = new IPFilterImportTool().execute(path(list));

    assertEquals(2, out.get("imported").getAsInt(), "comments, blanks and bad lines are skipped");
    assertEquals(3, table.size());
    assertEquals("Bad Corp", table.get(1).description());
    List<IPRange> saved = readSavedFilter();
    assertEquals(3, saved.size(), "the whole table is saved, not just the import");
    assertEquals("198.51.100.20", saved.get(2).endAddress());
  }

  @Test
  void importDecompressesAGzippedListAndRemovesTheCopy() throws IOException {
    Path list = home.resolve("list.p2p.gz");
    Files.write(list, gzip(BLOCKLIST));

    JsonObject out = new IPFilterImportTool().execute(path(list));

    assertEquals(2, out.get("imported").getAsInt());
    assertFalse(new File(settingsDir, "mcp_blocklist_decompressed.temp").exists());
    assertTrue(Files.exists(list), "the user's own file is kept");
  }

  @Test
  void importDownloadsAListFromAUrlAndRemovesTheDownload() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    byte[] body = gzip(BLOCKLIST);
    server.createContext(
        "/list.gz",
        exchange -> {
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    server.start();
    try {
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      JsonObject args = new JsonObject();
      args.addProperty("url", base + "/list.gz");

      assertEquals(2, new IPFilterImportTool().execute(args).get("imported").getAsInt());
      assertFalse(new File(settingsDir, "mcp_blocklist_download.temp").exists());

      args.addProperty("url", base + "/missing.gz");
      JsonObject missing = new IPFilterImportTool().execute(args);
      assertTrue(
          missing.get("error").getAsString().startsWith("Failed to download blocklist: "),
          missing.toString());
      assertEquals(0, missing.get("imported").getAsInt());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void importNeedsAReadableSourceWithValidRanges() throws IOException {
    JsonObject none = new IPFilterImportTool().execute(new JsonObject());
    assertEquals("Either 'url' or 'path' must be provided", none.get("error").getAsString());
    assertEquals(0, none.get("imported").getAsInt());

    Path missing = home.resolve("missing.p2p");
    assertEquals(
        "File not found or not readable: " + missing,
        new IPFilterImportTool().execute(path(missing)).get("error").getAsString());

    Path useless = home.resolve("useless.p2p");
    Files.writeString(useless, "# nothing here\n");
    assertEquals(0, new IPFilterImportTool().execute(path(useless)).get("imported").getAsInt());
    assertTrue(table.isEmpty());

    standIn.failure = new IllegalStateException("table gone");
    Path list = home.resolve("list.p2p");
    Files.writeString(list, BLOCKLIST);
    JsonObject failed = new IPFilterImportTool().execute(path(list));
    assertTrue(failed.get("error").getAsString().startsWith("Import failed: "), failed.toString());
    assertEquals(0, failed.get("imported").getAsInt());
  }

  @Test
  void toolsDeclareTheirArguments() {
    JsonArray required = new IPFilterAddTool().inputSchema().getAsJsonArray("required");
    assertEquals(2, required.size());
    assertTrue(new IPFilterImportTool().inputSchema().getAsJsonObject("properties").has("url"));
    assertEquals("object", new IPFilterListTool().inputSchema().get("type").getAsString());
    assertEquals("object", new IPFilterClearTool().inputSchema().get("type").getAsString());
  }

  private List<IPRange> readSavedFilter() throws IOException {
    List<IPRange> ranges = new ArrayList<>();
    try (InputStream in = new FileInputStream(new File(settingsDir, "ip_filter.db"))) {
      while (in.available() > 0) {
        ranges.add(IPRange.readObjectFrom(in));
      }
    }
    return ranges;
  }

  private static JsonObject range(String start, String end) {
    JsonObject args = new JsonObject();
    args.addProperty("start", start);
    args.addProperty("end", end);
    return args;
  }

  private static JsonObject path(Path path) {
    JsonObject args = new JsonObject();
    args.addProperty("path", path.toString());
    return args;
  }

  private static byte[] gzip(String text) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
      gz.write(text.getBytes(StandardCharsets.UTF_8));
    }
    return bytes.toByteArray();
  }

  /** The table as a list; set {@code failure} to make every operation fail. */
  private final class StandInTable implements IPFilterTableAccess.Table {
    RuntimeException failure;

    @Override
    public List<IPRange> snapshot() {
      fail();
      return new ArrayList<>(table);
    }

    @Override
    public void add(List<IPRange> ranges) {
      fail();
      table.addAll(ranges);
    }

    @Override
    public void clear() {
      fail();
      table.clear();
    }

    private void fail() {
      if (failure != null) {
        throw failure;
      }
    }
  }
}
