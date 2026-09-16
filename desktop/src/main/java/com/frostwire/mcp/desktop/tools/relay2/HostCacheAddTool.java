/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.relay2;

import com.frostwire.mcp.MCPTool;
import com.frostwire.search.relay.icebridge.IceBridgeHostCache;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** MCP tool to add or update a known IceBridge relay host in the local host cache. */
public final class HostCacheAddTool implements MCPTool {

  @Override
  public String name() {
    return "frostwire_hostcache_add";
  }

  @Override
  public String description() {
    return "Add or update a known IceBridge relay host in the local host cache.";
  }

  @Override
  public JsonObject inputSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    JsonObject props = new JsonObject();

    JsonObject hostProp = new JsonObject();
    hostProp.addProperty("type", "string");
    hostProp.addProperty("description", "Hostname or IP address of the relay");
    props.add("host", hostProp);

    JsonObject portProp = new JsonObject();
    portProp.addProperty("type", "integer");
    portProp.addProperty("description", "TCP identity/relay port (e.g. 6888)");
    props.add("port", portProp);

    JsonObject roleProp = new JsonObject();
    roleProp.addProperty("type", "string");
    roleProp.addProperty("description", "Relay role: BOTH, FORWARDER, or CLIENT (default BOTH)");
    props.add("role", roleProp);

    schema.add("properties", props);

    JsonArray required = new JsonArray();
    required.add("host");
    required.add("port");
    schema.add("required", required);
    return schema;
  }

  @Override
  public JsonObject execute(JsonObject arguments) {
    JsonObject out = new JsonObject();
    try {
      String host = RelayToolSupport.stringArg(arguments, "host");
      if (host == null || host.trim().isEmpty()) {
        out.addProperty("error", "Missing required parameter: host");
        return out;
      }
      host = host.trim();

      if (arguments == null || !arguments.has("port") || arguments.get("port").isJsonNull()) {
        out.addProperty("error", "Missing required parameter: port");
        return out;
      }
      int port;
      try {
        port = arguments.get("port").getAsInt();
      } catch (RuntimeException e) {
        out.addProperty("error", "port must be an integer");
        return out;
      }
      if (port <= 0 || port > 65535) {
        out.addProperty("error", "port must be between 1 and 65535");
        return out;
      }

      String role = RelayToolSupport.stringArg(arguments, "role");
      if (role == null || role.trim().isEmpty()) {
        role = "BOTH";
      } else {
        role = role.trim();
      }

      IceBridgeHostCache cache = IceBridgeHostCache.getInstance();
      if (cache == null) {
        out.addProperty("error", "IceBridge host cache is not available");
        return out;
      }

      cache.addOrUpdate(host, port, role);
      out.addProperty("added", true);
      out.addProperty("host", host);
      out.addProperty("port", port);
      out.addProperty("role", role);
    } catch (Throwable t) {
      out.addProperty("error", t.toString());
    }
    return out;
  }
}
