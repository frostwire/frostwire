package com.frostwire.mcp.desktop.tools.search;

import com.frostwire.mcp.MCPTool;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.search.SearchEngine;

import java.util.List;

public class SearchEngineToggleTool implements MCPTool {
    @Override
    public String name() {
        return "frostwire_search_engine_toggle";
    }

    @Override
    public String description() {
        return "Enable or disable a specific search engine.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject engineIdProp = new JsonObject();
        engineIdProp.addProperty("type", "string");
        engineIdProp.addProperty("description", "The engine ID to toggle (e.g. TPB_ID, SOUNDCLOUD_ID)");
        properties.add("engineId", engineIdProp);

        JsonObject enabledProp = new JsonObject();
        enabledProp.addProperty("type", "boolean");
        enabledProp.addProperty("description", "Whether the engine should be enabled");
        properties.add("enabled", enabledProp);

        schema.add("properties", properties);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("engineId");
        required.add("enabled");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        String engineId = arguments.get("engineId").getAsString();
        boolean enabled = arguments.get("enabled").getAsBoolean();

        List<SearchEngine> engines = SearchEngine.getEngines();
        SearchEngine target = null;
        for (SearchEngine engine : engines) {
            if (engine.getId().name().equals(engineId)) {
                target = engine;
                break;
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("engineId", engineId);

        if (target == null) {
            result.addProperty("error", "Engine not found: " + engineId);
            result.addProperty("enabled", false);
            return result;
        }

        target.getEnabledSetting().setValue(enabled);
        result.addProperty("enabled", target.isEnabled());
        result.addProperty("name", target.getName());
        return result;
    }
}
