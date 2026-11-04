/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.mcp.desktop.EngineContext;
import com.frostwire.mcp.desktop.adapters.TestTransfers;
import com.frostwire.mcp.desktop.tools.cloud.CloudDownloadTool;
import com.frostwire.mcp.desktop.tools.cloud.CloudSearchTool;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.util.FrostWireUtils;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Starting downloads through MCP: a torrent from a file or a URL, and media from a web page through
 * Telluride. The engine has startup's context but no session, so a torrent is read and identified
 * but nothing is downloaded; .torrent URLs are served from a local HTTP server.
 */
class DownloadToolsTest {

  @TempDir Path dir;

  private EngineContext context;
  private HttpServer http;
  private TorrentInfo torrent;
  private byte[] torrentBytes;

  @BeforeEach
  void setUp() throws Exception {
    context = EngineContext.install(dir.resolve("engine").toFile());
    torrent = TestTransfers.torrent(dir, "iso", 30_000);
    torrentBytes = torrent.bencode();
    http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    http.createContext(
        "/ubuntu.torrent",
        exchange -> {
          exchange.sendResponseHeaders(200, torrentBytes.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(torrentBytes);
          }
        });
    http.start();
  }

  @AfterEach
  void tearDown() {
    http.stop(0);
    context.close();
  }

  @Test
  void aTorrentFileIsIdentifiedByItsInfoHash() throws Exception {
    Path file = dir.resolve("iso.torrent");
    Files.write(file, torrentBytes);

    JsonObject out = new DownloadTorrentTool().execute(source("FILE", file.toString()));

    assertEquals(expectedId(), out.get("downloadId").getAsString());
    assertEquals("downloading", out.get("state").getAsString());
    assertEquals("iso", out.get("name").getAsString());
  }

  @Test
  void aTorrentUrlIsFetchedAndIdentified() {
    String url = "http://127.0.0.1:" + http.getAddress().getPort() + "/ubuntu.torrent";
    JsonObject args = source("url", url);
    args.addProperty("saveDir", dir.resolve("downloads").toString());

    JsonObject out = new DownloadTorrentTool().execute(args);

    assertEquals(expectedId(), out.get("downloadId").getAsString(), out.toString());
    assertEquals("iso", out.get("name").getAsString());
  }

  @Test
  void aTorrentUrlThatServesNothingIsAnError() {
    String url = "http://127.0.0.1:" + http.getAddress().getPort() + "/missing.torrent";
    assertEquals(
        "Failed to download .torrent file from URL",
        new DownloadTorrentTool().execute(source("url", url)).get("error").getAsString(),
        "the server answers 404");
  }

  @Test
  void torrentDownloadsValidateTheirArguments() {
    DownloadTorrentTool tool = new DownloadTorrentTool();
    assertEquals(
        "source and value are required", tool.execute(new JsonObject()).get("error").getAsString());
    assertEquals(
        "source and value are required",
        tool.execute(source("file", "")).get("error").getAsString());
    assertEquals(
        "Invalid source type: ftp. Use magnet, url, or file.",
        tool.execute(source("ftp", "x")).get("error").getAsString());
    String missing = dir.resolve("missing.torrent").toString();
    assertEquals(
        "Torrent file not found or not readable: " + missing,
        tool.execute(source("file", missing)).get("error").getAsString());

    Path notATorrent = dir.resolve("notes.torrent");
    writeQuietly(notATorrent, "not bencoded");
    assertTrue(
        tool.execute(source("file", notATorrent.toString()))
            .get("error")
            .getAsString()
            .startsWith("File download failed: "));
  }

  @Test
  void webDownloadsNeedAUrl() {
    assertEquals(
        "url is required",
        new DownloadHttpTool().execute(new JsonObject()).get("error").getAsString());
    assertEquals(
        "Missing required parameter: url",
        new CloudDownloadTool().execute(new JsonObject()).get("error").getAsString());
    assertEquals(
        "Missing required parameter: url",
        new CloudDownloadTool().execute(null).get("error").getAsString());
    assertEquals(
        "url parameter must not be empty",
        new CloudDownloadTool().execute(url("")).get("error").getAsString());
    assertEquals(
        "Missing required parameter: url",
        new CloudSearchTool().execute(new JsonObject()).get("error").getAsString());
    assertEquals(
        "url parameter must not be empty",
        new CloudSearchTool().execute(url("")).get("error").getAsString());
  }

  @Test
  void webDownloadsReportAMissingTelluride() {
    // Only where Telluride isn't installed: never launch it for real from a test.
    File telluride = FrostWireUtils.getTellurideLauncherFile();
    assumeFalse(telluride != null && telluride.exists(), "Telluride is installed here");
    String page = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    assertEquals(
        "Telluride executable not found",
        new DownloadHttpTool().execute(url(page)).get("error").getAsString());
    assertEquals(
        "Telluride executable not found",
        new CloudDownloadTool().execute(url(page)).get("error").getAsString());
    assertEquals(
        "Telluride executable not found",
        new CloudSearchTool().execute(url(page)).get("error").getAsString());
  }

  private String expectedId() {
    return torrent.infoHashV1().toString().toLowerCase(Locale.ROOT);
  }

  private static JsonObject source(String source, String value) {
    JsonObject args = new JsonObject();
    args.addProperty("source", source);
    args.addProperty("value", value);
    return args;
  }

  private static JsonObject url(String url) {
    JsonObject args = new JsonObject();
    args.addProperty("url", url);
    return args;
  }

  private static void writeQuietly(Path file, String text) {
    try {
      Files.writeString(file, text);
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }
}
