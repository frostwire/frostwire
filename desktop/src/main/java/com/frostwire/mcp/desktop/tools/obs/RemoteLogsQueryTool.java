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
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.http.HttpClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP tool to query the event log of a configured remote IceBridge relay over its control HTTP API
 * ({@code GET /events}). The remote endpoint is described by {@link SearchEnginesSettings} and may
 * be protected by an {@code X-IceBridge-Token} header; the token is never logged or returned.
 */
public class RemoteLogsQueryTool implements MCPTool {

  private static final int TIMEOUT_MILLIS = 5000;
  private static final String USER_AGENT = "FrostWire-MCP";

  @Override
  public String name() {
    return "frostwire_remote_logs_query";
  }

  @Override
  public String description() {
    return "Query the event log of a configured remote IceBridge relay, newest first. All filters are optional: level, category (csv), q (message substring), peer (pubkey substring), since (epoch ms) and limit (1-1000, default 100).";
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
      boolean useRemote = SearchEnginesSettings.ICEBRIDGE_USE_REMOTE.getValue();
      String remoteUrl = trimToEmpty(SearchEnginesSettings.ICEBRIDGE_REMOTE_URL.getValue());
      if (!useRemote || remoteUrl.isEmpty()) {
        out.addProperty("error", "remote relay not configured");
        out.addProperty("use_remote", false);
        return out;
      }

      String token = trimToEmpty(SearchEnginesSettings.ICEBRIDGE_REMOTE_AUTH_TOKEN.getValue());
      String url = buildUrl(remoteUrl, arguments);

      Map<String, String> headers = new HashMap<>();
      if (!token.isEmpty()) {
        headers.put("X-IceBridge-Token", token);
      }

      HttpClient httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
      String body =
          httpClient.get(
              url, TIMEOUT_MILLIS, USER_AGENT, null, null, headers.isEmpty() ? null : headers);
      if (body == null) {
        out.addProperty("error", "remote relay request failed");
        out.addProperty("use_remote", true);
        return out;
      }

      List<IceBridgeEvent> events = parseEvents(body);
      out.addProperty("count", events.size());
      out.add("events", LogsQueryTool.toJsonArray(events));
    } catch (Throwable t) {
      out.addProperty("error", String.valueOf(t));
    }
    return out;
  }

  static String buildUrl(String remoteUrl, JsonObject arguments) {
    StringBuilder sb = new StringBuilder(remoteUrl);
    while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '/') {
      sb.setLength(sb.length() - 1);
    }
    sb.append("/events");

    List<String> params = new ArrayList<>();
    addParam(params, "level", LogsQueryTool.optString(arguments, "level"));
    addParam(params, "category", LogsQueryTool.optString(arguments, "category"));
    addParam(params, "q", LogsQueryTool.optString(arguments, "q"));
    addParam(params, "peer", LogsQueryTool.optString(arguments, "peer"));

    long since = LogsQueryTool.optLong(arguments, "since", 0L);
    if (since > 0) {
      params.add("since=" + since);
    }
    int limit =
        LogsQueryTool.clampLimit(
            LogsQueryTool.optLong(arguments, "limit", LogsQueryTool.DEFAULT_LIMIT));
    params.add("limit=" + limit);

    if (!params.isEmpty()) {
      sb.append('?').append(String.join("&", params));
    }
    return sb.toString();
  }

  private static void addParam(List<String> params, String key, String value) {
    if (value != null && !value.isBlank()) {
      params.add(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
    }
  }

  static List<IceBridgeEvent> parseEvents(String body) {
    List<IceBridgeEvent> events = new ArrayList<>();
    if (body == null || body.isBlank()) {
      return events;
    }
    JsonElement root = JsonParser.parseString(body);
    if (root == null || !root.isJsonObject()) {
      return events;
    }
    JsonElement data = root.getAsJsonObject().get("data");
    if (data == null || !data.isJsonArray()) {
      return events;
    }
    JsonArray array = data.getAsJsonArray();
    for (JsonElement element : array) {
      if (element == null || !element.isJsonObject()) {
        continue;
      }
      JsonObject event = element.getAsJsonObject();
      long ts = getLong(event, "timestampMs", getLong(event, "ts", 0L));
      String peerPub = getString(event, "peerPub", getString(event, "peer", ""));
      String message = getString(event, "message", "");
      IceBridgeEvent.Level level = IceBridgeEventLog.parseLevel(getString(event, "level", null));
      IceBridgeEvent.Category category = parseCategory(getString(event, "category", null));
      events.add(new IceBridgeEvent(ts, level, category, peerPub, message));
    }
    return events;
  }

  private static IceBridgeEvent.Category parseCategory(String name) {
    Set<IceBridgeEvent.Category> categories = IceBridgeEventLog.parseCategories(name);
    return categories.isEmpty() ? IceBridgeEvent.Category.GENERAL : categories.iterator().next();
  }

  private static String getString(JsonObject object, String key, String fallback) {
    if (object == null || !object.has(key)) {
      return fallback;
    }
    try {
      JsonElement element = object.get(key);
      if (element == null || element.isJsonNull()) {
        return fallback;
      }
      return element.getAsString();
    } catch (Throwable t) {
      return fallback;
    }
  }

  private static long getLong(JsonObject object, String key, long fallback) {
    if (object == null || !object.has(key)) {
      return fallback;
    }
    try {
      JsonElement element = object.get(key);
      if (element == null || element.isJsonNull()) {
        return fallback;
      }
      return element.getAsLong();
    } catch (Throwable t) {
      return fallback;
    }
  }

  private static String trimToEmpty(String value) {
    return value == null ? "" : value.trim();
  }
}
