package com.frostwire.mcp.desktop.tools.btengine;

import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.adapters.BTEngineAdapter;
import com.google.gson.JsonObject;

public final class BTEngineStatusTool implements MCPTool {

    @Override
    public String name() {
        return "frostwire_btengine_status";
    }

    @Override
    public String description() {
        return "Get the current status of the BitTorrent engine including download/upload rates, DHT nodes, and settings.";
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
        return BTEngineAdapter.toStatusJson();
    }
}
