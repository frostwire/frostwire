/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.mcp.MCPJsonRpc;
import com.frostwire.mcp.MCPServer;
import com.frostwire.mcp.desktop.adapters.TestTransfers;
import com.frostwire.mcp.desktop.adapters.TestTransfers.Download;
import com.frostwire.mcp.desktop.transport.TlsConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.limegroup.gnutella.settings.ConnectionSettings;
import com.limegroup.gnutella.util.FrostWireUtils;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The server FrostWire exposes to AI agents: the tools and resources it registers, and what each
 * resource reads. The server is built but never started, so requests go straight to it.
 */
class FrostWireMCPServerFactoryTest {

  @TempDir Path dir;

  private MCPServer server;
  private EngineContext engine;

  @BeforeEach
  void createServer() {
    // The settings and engine resources read the engine, which needs startup's context.
    engine = EngineContext.install(dir.toFile());
    server = FrostWireMCPServerFactory.createServer("127.0.0.1", 0, new TlsConfig());
    JsonObject init = server.handleRequest(MCPJsonRpc.request(0, "initialize", null));
    assertEquals(
        FrostWireUtils.getFrostWireVersion(),
        init.getAsJsonObject("result").getAsJsonObject("serverInfo").get("version").getAsString());
  }

  @AfterEach
  void cleanUp() {
    TestTransfers.reset();
    engine.close();
  }

  @Test
  void everyToolHasAUniqueNameADescriptionAndAValidSchema() {
    JsonArray tools = result("tools/list", null).getAsJsonArray("tools");

    assertEquals(56, tools.size());
    Set<String> names = new HashSet<>();
    for (JsonElement element : tools) {
      JsonObject tool = element.getAsJsonObject();
      String name = tool.get("name").getAsString();
      assertTrue(names.add(name), "duplicate tool " + name);
      assertTrue(name.startsWith("frostwire_"), name);
      assertFalse(tool.get("description").getAsString().isBlank(), name);
      JsonObject schema = tool.getAsJsonObject("inputSchema");
      assertEquals("object", schema.get("type").getAsString(), name);
      if (schema.has("required")) {
        JsonObject properties = schema.getAsJsonObject("properties");
        for (JsonElement required : schema.getAsJsonArray("required")) {
          assertTrue(
              properties.has(required.getAsString()),
              name + " requires an undeclared " + required.getAsString());
        }
      }
    }
    for (String expected :
        List.of(
            "frostwire_search",
            "frostwire_transfers_list",
            "frostwire_btengine_create_torrent",
            "frostwire_settings_set",
            "frostwire_mcp_control")) {
      assertTrue(names.contains(expected), expected);
    }
  }

  @Test
  void resourcesAreListedWithTheirMimeType() {
    JsonArray resources = result("resources/list", null).getAsJsonArray("resources");

    Set<String> uris = new HashSet<>();
    for (JsonElement element : resources) {
      JsonObject resource = element.getAsJsonObject();
      uris.add(resource.get("uri").getAsString());
      assertEquals("application/json", resource.get("mimeType").getAsString());
      assertFalse(resource.get("name").getAsString().isBlank());
      assertFalse(resource.get("description").getAsString().isBlank());
    }
    assertEquals(
        Set.of(
            "frostwire://transfers",
            "frostwire://transfers/detail",
            "frostwire://transfers/files",
            "frostwire://library",
            "frostwire://settings",
            "frostwire://btengine/status",
            "frostwire://vpn/status"),
        uris);
  }

  @Test
  void transferResourcesDescribeEveryTransfer() throws Exception {
    TorrentInfo torrent = TestTransfers.torrent(dir, "album", 1_000, 2_000, 3_000);
    Download album = TestTransfers.download(torrent, dir);
    album.handle().status().setNum_peers(2);
    album.handle().status().setList_seeds(5);
    album.download().refreshStatusCache();
    TestTransfers.use(List.of(album.download()));

    JsonArray transfers = JsonParser.parseString(read("frostwire://transfers")).getAsJsonArray();
    assertEquals(1, transfers.size());
    assertEquals(album.id(), transfers.get(0).getAsJsonObject().get("id").getAsString());

    JsonObject detail =
        JsonParser.parseString(read("frostwire://transfers/detail"))
            .getAsJsonArray()
            .get(0)
            .getAsJsonObject();
    assertEquals(2, detail.get("connectedPeers").getAsInt());
    assertEquals(5, detail.get("totalSeeds").getAsInt());
    assertFalse(detail.get("sequential").getAsBoolean());

    JsonObject files =
        JsonParser.parseString(read("frostwire://transfers/files"))
            .getAsJsonArray()
            .get(0)
            .getAsJsonObject();
    assertEquals(album.id(), files.get("downloadId").getAsString());
    assertEquals("album", files.get("name").getAsString());
    assertEquals(3, files.get("fileCount").getAsInt());
    assertFalse(files.get("truncated").getAsBoolean());
    assertEquals(3, files.getAsJsonArray("files").size());
  }

  @Test
  void settingsEngineVpnAndLibraryResourcesReadCurrentState() {
    JsonObject settings = JsonParser.parseString(read("frostwire://settings")).getAsJsonObject();
    assertTrue(settings.has("connection"));
    assertTrue(settings.has("sharing"));
    assertTrue(settings.getAsJsonObject("search").has("engines"));
    assertEquals(
        ConnectionSettings.VPN_DROP_PROTECTION.getValue(),
        settings.getAsJsonObject("vpn").get("dropProtectionEnabled").getAsBoolean());

    JsonObject status =
        JsonParser.parseString(read("frostwire://btengine/status")).getAsJsonObject();
    assertFalse(status.get("running").getAsBoolean(), "the engine was never started");
    assertTrue(status.has("paused"));
    assertEquals(0, status.get("totalDownload").getAsLong());
    assertEquals(0, status.get("totalUpload").getAsLong());

    JsonObject vpn = JsonParser.parseString(read("frostwire://vpn/status")).getAsJsonObject();
    assertTrue(vpn.has("vpnActive"));
    assertEquals(
        ConnectionSettings.VPN_DROP_PROTECTION.getValue(),
        vpn.get("dropProtectionEnabled").getAsBoolean());

    JsonObject library = JsonParser.parseString(read("frostwire://library")).getAsJsonObject();
    assertEquals("available", library.get("status").getAsString());
  }

  private JsonObject result(String method, JsonObject params) {
    return server.handleRequest(MCPJsonRpc.request(1, method, params)).getAsJsonObject("result");
  }

  private String read(String uri) {
    JsonObject params = new JsonObject();
    params.addProperty("uri", uri);
    JsonObject content =
        result("resources/read", params).getAsJsonArray("contents").get(0).getAsJsonObject();
    assertEquals(uri, content.get("uri").getAsString());
    return content.get("text").getAsString();
  }
}
