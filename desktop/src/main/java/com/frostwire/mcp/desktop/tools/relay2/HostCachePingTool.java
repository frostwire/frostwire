/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.relay2;

import com.frostwire.mcp.MCPTool;
import com.frostwire.search.relay.icebridge.IceBridgeHostCache;
import com.frostwire.util.Logger;
import com.google.gson.JsonObject;

/**
 * MCP tool that triggers a TCP identity ping of every known IceBridge host.
 *
 * <p>{@link IceBridgeHostCache#refreshPings()} blocks while it pings each host, so it is run on a
 * daemon background thread and this tool returns immediately with {@code started}. Callers should
 * re-run {@code frostwire_hostcache_list} afterwards to observe updated ping timestamps.
 */
public final class HostCachePingTool implements MCPTool {

  private static final Logger LOG = Logger.getLogger(HostCachePingTool.class);

  @Override
  public String name() {
    return "frostwire_hostcache_ping";
  }

  @Override
  public String description() {
    return "Start a background TCP identity ping of all known IceBridge relay hosts. Returns"
        + " started=true immediately; re-list the host cache to observe results.";
  }

  @Override
  public JsonObject inputSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.add("properties", new JsonObject());
    return schema;
  }

  @Override
  public JsonObject execute(JsonObject arguments) {
    JsonObject out = new JsonObject();
    try {
      IceBridgeHostCache cache = IceBridgeHostCache.getInstance();
      if (cache == null) {
        out.addProperty("error", "IceBridge host cache is not available");
        return out;
      }

      int known = cache.getAll().size();
      Thread worker =
          new Thread(
              () -> {
                try {
                  cache.refreshPings();
                } catch (Throwable t) {
                  LOG.warn("IceBridge host cache background ping failed", t);
                }
              },
              "icebridge-hostcache-ping");
      worker.setDaemon(true);
      worker.start();

      out.addProperty("started", true);
      out.addProperty("known_hosts", known);
      out.addProperty(
          "note",
          "Ping runs in the background; call frostwire_hostcache_list again after a few seconds to"
              + " see updated last_success_ms and failures.");
    } catch (Throwable t) {
      out.addProperty("error", t.toString());
    }
    return out;
  }
}
