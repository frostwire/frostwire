/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.relay2;

import com.frostwire.mcp.MCPTool;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.LocalSharedTorrent;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.search.SearchEngine;
import java.util.List;

/**
 * MCP tool intended to browse a remote peer's shared catalog.
 *
 * <p>The desktop has no MCP-exposed path to fetch a remote catalog yet: that requires the IceBridge
 * {@code RemoteIndexFetcher} over the relay transport. Rather than fabricate a remote fetch, this
 * tool returns this node's LOCAL shared index rows and states the limitation.
 */
public final class PeerCatalogTool implements MCPTool {

  @Override
  public String name() {
    return "frostwire_peer_catalog";
  }

  @Override
  public String description() {
    return "Browse a peer's shared catalog. Remote catalogs require the IceBridge RemoteIndexFetcher (not wired to MCP yet); this returns the local shared index capped by limit and explains the limitation.";
  }

  @Override
  public JsonObject inputSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    JsonObject props = new JsonObject();

    JsonObject pubProp = new JsonObject();
    pubProp.addProperty("type", "string");
    pubProp.addProperty(
        "description", "64-character hex ed25519 pubkey of the peer whose catalog is requested");
    props.add("pub", pubProp);

    JsonObject limitProp = new JsonObject();
    limitProp.addProperty("type", "integer");
    limitProp.addProperty("description", "Maximum rows to return (default 100, clamp 1..500)");
    props.add("limit", limitProp);

    schema.add("properties", props);

    JsonArray required = new JsonArray();
    required.add("pub");
    schema.add("required", required);
    return schema;
  }

  @Override
  public JsonObject execute(JsonObject arguments) {
    JsonObject out = new JsonObject();
    try {
      String hex = RelayToolSupport.stringArg(arguments, "pub");
      if (hex == null || hex.isEmpty()) {
        out.addProperty("error", "Missing required parameter: pub");
        return out;
      }
      try {
        RelayToolSupport.parsePub32(hex);
      } catch (IllegalArgumentException e) {
        out.addProperty("error", e.getMessage());
        return out;
      }

      int limit = RelayToolSupport.clampInt(arguments, "limit", 100, 1, 500);

      LocalIndex index = SearchEngine.getDistributedLocalIndex();
      if (index == null) {
        out.addProperty("error", "LocalIndex is not available (relay stack not wired)");
        return out;
      }

      List<LocalSharedTorrent> all = index.listAll();
      JsonArray torrents = new JsonArray();
      for (int i = 0; i < all.size() && i < limit; i++) {
        torrents.add(RelayToolSupport.torrentToJson(all.get(i)));
      }

      out.addProperty("pub", hex);
      out.addProperty("source", "local");
      out.addProperty("count", torrents.size());
      out.add("torrents", torrents);
      out.addProperty(
          "note",
          "Remote peer catalogs require the IceBridge RemoteIndexFetcher over the relay transport and are not wired to MCP yet; returning this node's local shared index instead.");
    } catch (Throwable t) {
      out.addProperty("error", t.toString());
    }
    return out;
  }
}
