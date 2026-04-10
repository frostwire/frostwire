package com.frostwire.mcp.desktop.tools.search;

import com.frostwire.mcp.MCPTool;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.search.SearchEngine;

import java.util.List;

public class SearchEnginesTool implements MCPTool {
    @Override
    public String name() {
        return "frostwire_search_engines";
    }

    @Override
    public String description() {
        return "List all available search engines and their current status.";
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
        List<SearchEngine> engines = SearchEngine.getEngines();
        JsonArray enginesArray = new JsonArray();

        for (SearchEngine engine : engines) {
            JsonObject engineObj = new JsonObject();
            engineObj.addProperty("id", engine.getId().name());
            engineObj.addProperty("name", engine.getName());
            engineObj.addProperty("enabled", engine.isEnabled());
            engineObj.addProperty("type", getEngineType(engine));
            enginesArray.add(engineObj);
        }

        JsonObject result = new JsonObject();
        result.add("engines", enginesArray);
        return result;
    }

    private static String getEngineType(SearchEngine engine) {
        switch (engine.getId()) {
            case SOUNDCLOUD_ID:
            case INTERNET_ARCHIVE_ID:
            case YT_ID:
                return "cloud";
            case FROSTCLICK_ID:
                return "direct";
            case TELLURIDE_ID:
                return "cloud";
            default:
                return "torrent";
        }
    }
}
