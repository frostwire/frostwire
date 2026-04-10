package com.frostwire.mcp.desktop.tools.ipfilter;

import com.frostwire.mcp.MCPTool;
import com.frostwire.util.Logger;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.options.panes.IPFilterTableMediator;
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPRange;
import com.limegroup.gnutella.gui.tables.DataLineModel;

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
            offset = arguments.get("offset").getAsInt();
        }
        if (arguments.has("limit") && arguments.get("limit").isJsonPrimitive()) {
            limit = arguments.get("limit").getAsInt();
        }

        JsonObject result = new JsonObject();
        try {
            DataLineModel<IPFilterTableMediator.IPFilterDataLine, IPRange> model = IPFilterTableMediator.getInstance().getDataModel();
            int total = model.getRowCount();

            JsonArray ranges = new JsonArray();
            int end = Math.min(offset + limit, total);
            for (int i = offset; i < end; i++) {
                try {
                    IPFilterTableMediator.IPFilterDataLine dataLine = model.get(i);
                    if (dataLine != null) {
                        IPRange ipRange = dataLine.getInitializeObject();
                        if (ipRange != null) {
                            JsonObject rangeObj = new JsonObject();
                            rangeObj.addProperty("start", ipRange.startAddress());
                            rangeObj.addProperty("end", ipRange.endAddress());
                            rangeObj.addProperty("description", ipRange.description());
                            ranges.add(rangeObj);
                        }
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