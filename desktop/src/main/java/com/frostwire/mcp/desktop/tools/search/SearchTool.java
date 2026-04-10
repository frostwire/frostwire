package com.frostwire.mcp.desktop.tools.search;

import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.state.SearchSession;
import com.frostwire.mcp.desktop.state.SearchSessionManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class SearchTool implements MCPTool {
    @Override
    public String name() {
        return "frostwire_search";
    }

    @Override
    public String description() {
        return "Search for torrents and cloud content on FrostWire. Returns a search token for polling results.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject keywordsProp = new JsonObject();
        keywordsProp.addProperty("type", "string");
        keywordsProp.addProperty("description", "Search keywords");
        properties.add("keywords", keywordsProp);

        JsonObject enginesProp = new JsonObject();
        enginesProp.addProperty("type", "array");
        enginesProp.addProperty("description", "Optional list of engine IDs to search (e.g. TPB_ID, SOUNDCLOUD_ID). Searches all enabled engines if omitted.");
        JsonObject enginesItems = new JsonObject();
        enginesItems.addProperty("type", "string");
        enginesProp.add("items", enginesItems);
        properties.add("engines", enginesProp);

        JsonObject timeoutProp = new JsonObject();
        timeoutProp.addProperty("type", "integer");
        timeoutProp.addProperty("description", "Optional timeout in seconds for the search to complete (default: no timeout, poll with frostwire_search_status)");
        properties.add("timeout", timeoutProp);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("keywords");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        String keywords = arguments.get("keywords").getAsString();
        List<String> engines = Collections.emptyList();
        if (arguments.has("engines") && arguments.get("engines").isJsonArray()) {
            engines = arguments.getAsJsonArray("engines").asList().stream()
                    .map(e -> e.getAsString())
                    .collect(Collectors.toList());
        }

        final List<String> finalEngines = engines;
        SearchSession session = CompletableFuture.supplyAsync(() ->
                SearchSessionManager.instance().createSearch(keywords, finalEngines)
        ).join();

        JsonObject result = new JsonObject();
        result.addProperty("token", String.valueOf(session.getToken()));
        result.addProperty("status", "started");
        result.addProperty("keywords", keywords);
        return result;
    }
}
