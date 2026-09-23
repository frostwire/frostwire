/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.ipfilter;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.swig.ip_filter;
import com.frostwire.mcp.MCPTool;
import com.frostwire.util.Logger;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPRange;
import java.util.List;

public class IPFilterAddTool implements MCPTool {

  private static final Logger LOG = Logger.getLogger(IPFilterAddTool.class);

  @Override
  public String name() {
    return "frostwire_ipfilter_add";
  }

  @Override
  public String description() {
    return "Add an IP range to the IP filter block list.";
  }

  @Override
  public JsonObject inputSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");

    JsonObject properties = new JsonObject();

    JsonObject startProp = new JsonObject();
    startProp.addProperty("type", "string");
    startProp.addProperty("description", "Start IP address of the range (e.g. 192.168.1.0)");
    properties.add("start", startProp);

    JsonObject endProp = new JsonObject();
    endProp.addProperty("type", "string");
    endProp.addProperty("description", "End IP address of the range (e.g. 192.168.1.255)");
    properties.add("end", endProp);

    JsonObject descProp = new JsonObject();
    descProp.addProperty("type", "string");
    descProp.addProperty("description", "Optional description for the IP range");
    properties.add("description", descProp);

    schema.add("properties", properties);

    com.google.gson.JsonArray required = new com.google.gson.JsonArray();
    required.add("start");
    required.add("end");
    schema.add("required", required);

    return schema;
  }

  @Override
  public JsonObject execute(JsonObject arguments) {
    String start = arguments.get("start").getAsString();
    String end = arguments.get("end").getAsString();
    String description = "";
    if (arguments.has("description") && arguments.get("description").isJsonPrimitive()) {
      description = arguments.get("description").getAsString();
    }

    JsonObject result = new JsonObject();

    try {
      IPRange ipRange = new IPRange(description, start, end);
      IPFilterRangeValidator.ParsedRange parsed = IPFilterRangeValidator.parse(ipRange);
      BTEngine engine = BTEngine.getInstance();
      if (engine != null && engine.swig() != null) {
        ip_filter currentFilter = engine.swig().get_ip_filter();
        currentFilter.add_rule(
            parsed.start(), parsed.end(), ip_filter.access_flags.blocked.swigValue());
        engine.swig().set_ip_filter(currentFilter);
      }
      IPFilterTableAccess.add(List.of(ipRange));
    } catch (IllegalArgumentException e) {
      result.addProperty("error", e.getMessage());
      return result;
    } catch (Exception e) {
      LOG.error("IPFilterAddTool: Failed to add range", e);
      result.addProperty("error", "Failed to add IP filter range: " + e.getMessage());
      return result;
    }

    result.addProperty("added", true);
    result.addProperty("start", start);
    result.addProperty("end", end);
    result.addProperty("description", description);
    return result;
  }
}
