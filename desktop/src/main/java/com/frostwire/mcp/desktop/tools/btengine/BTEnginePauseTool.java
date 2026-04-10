package com.frostwire.mcp.desktop.tools.btengine;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.mcp.MCPTool;
import com.google.gson.JsonObject;

public final class BTEnginePauseTool implements MCPTool {

    @Override
    public String name() {
        return "frostwire_btengine_pause";
    }

    @Override
    public String description() {
        return "Pause the BitTorrent engine. All active torrents will be paused.";
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
        BTEngine.getInstance().pause();
        JsonObject result = new JsonObject();
        result.addProperty("paused", true);
        return result;
    }
}
