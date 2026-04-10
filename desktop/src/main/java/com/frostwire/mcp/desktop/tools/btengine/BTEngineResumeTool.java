package com.frostwire.mcp.desktop.tools.btengine;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.mcp.MCPTool;
import com.google.gson.JsonObject;

public final class BTEngineResumeTool implements MCPTool {

    @Override
    public String name() {
        return "frostwire_btengine_resume";
    }

    @Override
    public String description() {
        return "Resume the BitTorrent engine. All paused torrents will resume.";
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
        BTEngine.getInstance().resume();
        JsonObject result = new JsonObject();
        result.addProperty("paused", false);
        return result;
    }
}
