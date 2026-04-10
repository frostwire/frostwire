package com.frostwire.mcp.desktop.tools.ipfilter;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.swig.ip_filter;
import com.frostwire.mcp.MCPTool;
import com.frostwire.util.Logger;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.options.panes.IPFilterTableMediator;
import org.limewire.util.CommonUtils;

import java.io.File;
import java.io.IOException;

public class IPFilterClearTool implements MCPTool {

    private static final Logger LOG = Logger.getLogger(IPFilterClearTool.class);

    @Override
    public String name() {
        return "frostwire_ipfilter_clear";
    }

    @Override
    public String description() {
        return "Clear all IP filter ranges from the block list.";
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
        JsonObject result = new JsonObject();

        IPFilterTableMediator tableMediator = IPFilterTableMediator.getInstance();
        tableMediator.clearTable();

        File ipFilterDBFile = new File(CommonUtils.getUserSettingsDir(), "ip_filter.db");
        ipFilterDBFile.delete();
        try {
            ipFilterDBFile.createNewFile();
        } catch (IOException e) {
            LOG.warn("IPFilterClearTool: Could not recreate ip_filter.db: " + e.getMessage());
        }

        BTEngine engine = BTEngine.getInstance();
        if (engine != null && engine.swig() != null) {
            ip_filter freshFilter = new ip_filter();
            engine.swig().set_ip_filter(freshFilter);
        }

        result.addProperty("cleared", true);
        return result;
    }
}