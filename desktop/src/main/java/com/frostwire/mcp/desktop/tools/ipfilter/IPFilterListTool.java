/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.ipfilter;

import com.frostwire.mcp.MCPTool;
import com.frostwire.util.Logger;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPRange;
import java.util.List;

public class IPFilterListTool implements MCPTool {

  private static final Logger LOG = Logger.getLogger(IPFilterListTool.class);

  @Override
  public String name() {
    return "frostwire_ipfilter_list";
  }

  @Override
  public String description() {
    return "List IP filter ranges currently configured in FrostWire.";
  }

  @Override
  public JsonObject inputSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");

    JsonObject properties = new JsonObject();

    JsonObject offsetProp = new JsonObject();
    offsetProp.addProperty("type", "integer");
    offsetProp.addProperty("description", "Offset for pagination (default: 0)");
    properties.add("offset", offsetProp);

    JsonObject limitProp = new JsonObject();
    limitProp.addProperty("type", "integer");
    limitProp.addProperty("description", "Maximum number of ranges to return (default: 100)");
    properties.add("limit", limitProp);

    schema.add("properties", properties);
    return schema;
  }

  @Override
  public JsonObject execute(JsonObject arguments) {
    int offset = 0;
    int limit = 100;
    if (arguments.has("offset") && arguments.get("offset").isJsonPrimitive()) {
      offset = Math.max(0, arguments.get("offset").getAsInt());
    }
    if (arguments.has("limit") && arguments.get("limit").isJsonPrimitive()) {
      limit = Math.min(1000, Math.max(1, arguments.get("limit").getAsInt()));
    }

    JsonObject result = new JsonObject();
    try {
      List<IPRange> snapshot = IPFilterTableAccess.snapshot();
      int total = snapshot.size();

      JsonArray ranges = new JsonArray();
      int end = Math.min(offset + limit, total);
      for (int i = offset; i < end; i++) {
        try {
          IPRange ipRange = snapshot.get(i);
          if (ipRange != null) {
            JsonObject rangeObj = new JsonObject();
            rangeObj.addProperty("start", ipRange.startAddress());
            rangeObj.addProperty("end", ipRange.endAddress());
            rangeObj.addProperty("description", ipRange.description());
            ranges.add(rangeObj);
          }
        } catch (Exception e) {
          LOG.warn("IPFilterListTool: Error reading range at index " + i + ": " + e.getMessage());
        }
      }

      result.addProperty("total", total);
      result.add("ranges", ranges);
    } catch (Exception e) {
      LOG.error("IPFilterListTool error: " + e.getMessage(), e);
      result.addProperty("error", "Failed to list IP filter ranges: " + e.getMessage());
      result.addProperty("total", 0);
      result.add("ranges", new JsonArray());
    }
    return result;
  }
}
