/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.obs;

import com.frostwire.mcp.MCPTool;
import com.frostwire.search.relay.event.IceBridgeEvent;
import com.frostwire.search.relay.event.IceBridgeEventLog;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;

/**
 * MCP tool to query the in-memory IceBridge / distributed-search event log. Useful for inspecting a
 * live desktop over MCP without attaching a debugger.
 */
public class LogsQueryTool implements MCPTool {

  static final int DEFAULT_LIMIT = 100;
  static final int MAX_LIMIT = 1000;

  @Override
  public String name() {
    return "frostwire_logs_query";
  }

  @Override
  public String description() {
    return "Query the in-memory IceBridge/distributed-search event log, newest first. All filters are optional: level, category (csv), q (message substring), peer (pubkey substring), since (epoch ms) and limit (1-1000, default 100).";
  }

  @Override
  public JsonObject inputSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    JsonObject properties = new JsonObject();

    JsonObject level = new JsonObject();
    level.addProperty("type", "string");
    level.addProperty(
        "description", "Minimum level: DEBUG, INFO, WARN or ERROR (default DEBUG = all).");
    properties.add("level", level);

    JsonObject category = new JsonObject();
    category.addProperty("type", "string");
    category.addProperty(
        "description",
        "Comma-separated categories: SEARCH, FORWARD, DIGEST, PEER, REGISTRY, RELAY, METRICS, ERROR, GENERAL.");
    properties.add("category", category);

    JsonObject q = new JsonObject();
    q.addProperty("type", "string");
    q.addProperty("description", "Case-insensitive substring match over the event message.");
    properties.add("q", q);

    JsonObject peer = new JsonObject();
    peer.addProperty("type", "string");
    peer.addProperty(
        "description", "Case-insensitive substring match over the peer public key hex.");
    properties.add("peer", peer);

    JsonObject since = new JsonObject();
    since.addProperty("type", "integer");
    since.addProperty(
        "description", "Only events at or after this wall-clock time in epoch milliseconds.");
    properties.add("since", since);

    JsonObject limit = new JsonObject();
    limit.addProperty("type", "integer");
    limit.addProperty("description", "Maximum results, clamped to 1-1000 (default 100).");
    properties.add("limit", limit);

    schema.add("properties", properties);
    return schema;
  }

  @Override
  public JsonObject execute(JsonObject arguments) {
    JsonObject out = new JsonObject();
    try {
      IceBridgeEventLog log = IceBridgeEventLog.instance();
      IceBridgeEvent.Level minLevel = IceBridgeEventLog.parseLevel(optString(arguments, "level"));
      Set<IceBridgeEvent.Category> categories =
          IceBridgeEventLog.parseCategories(optString(arguments, "category"));
      String text = optString(arguments, "q");
      String peer = optString(arguments, "peer");
      long since = optLong(arguments, "since", 0L);
      int limit = clampLimit(optLong(arguments, "limit", DEFAULT_LIMIT));
      List<IceBridgeEvent> events = log.query(minLevel, categories, text, peer, since, limit);
      out.addProperty("count", events.size());
      out.add("events", toJsonArray(events));
    } catch (Throwable t) {
      out.addProperty("error", String.valueOf(t));
    }
    return out;
  }

  static JsonArray toJsonArray(List<IceBridgeEvent> events) {
    JsonArray array = new JsonArray();
    if (events != null) {
      for (IceBridgeEvent event : events) {
        if (event != null) {
          array.add(toJson(event));
        }
      }
    }
    return array;
  }

  static JsonObject toJson(IceBridgeEvent event) {
    JsonObject json = new JsonObject();
    json.addProperty("ts", event.timestampMs());
    json.addProperty("level", String.valueOf(event.level()));
    json.addProperty("category", String.valueOf(event.category()));
    json.addProperty("peer", event.shortPeerPub());
    json.addProperty("message", event.message());
    return json;
  }

  static int clampLimit(long limit) {
    if (limit < 1) {
      return 1;
    }
    return (int) Math.min(limit, MAX_LIMIT);
  }

  static String optString(JsonObject arguments, String key) {
    if (arguments == null || !arguments.has(key)) {
      return null;
    }
    try {
      if (arguments.get(key).isJsonNull()) {
        return null;
      }
      return arguments.get(key).getAsString();
    } catch (Throwable t) {
      return null;
    }
  }

  static long optLong(JsonObject arguments, String key, long fallback) {
    if (arguments == null || !arguments.has(key)) {
      return fallback;
    }
    try {
      if (arguments.get(key).isJsonNull()) {
        return fallback;
      }
      return arguments.get(key).getAsLong();
    } catch (Throwable t) {
      return fallback;
    }
  }
}
