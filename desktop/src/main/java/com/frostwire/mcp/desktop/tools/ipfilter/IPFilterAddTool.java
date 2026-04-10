package com.frostwire.mcp.desktop.tools.ipfilter;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.swig.address;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.ip_filter;
import com.frostwire.mcp.MCPTool;
import com.frostwire.util.Logger;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.options.panes.IPFilterTableMediator;
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPRange;
import com.limegroup.gnutella.gui.tables.DataLineModel;

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

        IPRange ipRange = new IPRange(description, start, end);
        IPFilterTableMediator tableMediator = IPFilterTableMediator.getInstance();
        DataLineModel<IPFilterTableMediator.IPFilterDataLine, IPRange> dataModel = tableMediator.getDataModel();
        dataModel.add(ipRange, dataModel.getRowCount());
        tableMediator.refresh();

        BTEngine engine = BTEngine.getInstance();
        if (engine != null && engine.swig() != null) {
            try {
                ip_filter currentFilter = engine.swig().get_ip_filter();
                error_code ec = new error_code();
                address addrStart = address.from_string(start, ec);
                if (!ec.failed()) {
                    address addrEnd = address.from_string(end, ec);
                    if (!ec.failed()) {
                        currentFilter.add_rule(addrStart, addrEnd, 0);
                        engine.swig().set_ip_filter(currentFilter);
                    } else {
                        LOG.warn("IPFilterAddTool: Invalid end address: " + end);
                    }
                } else {
                    LOG.warn("IPFilterAddTool: Invalid start address: " + start);
                }
            } catch (Exception e) {
                LOG.error("IPFilterAddTool: Error syncing with BTEngine: " + e.getMessage(), e);
            }
        }

        result.addProperty("added", true);
        result.addProperty("start", start);
        result.addProperty("end", end);
        result.addProperty("description", description);
        return result;
    }
}