package com.frostwire.mcp.desktop.tools.search;

import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.state.SearchSession;
import com.frostwire.mcp.desktop.state.SearchSessionManager;
import com.google.gson.JsonObject;

public class SearchStatusTool implements MCPTool {
    @Override
    public String name() {
        return "frostwire_search_status";
    }

    @Override
    public String description() {
        return "Check the status of a running search session.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject tokenProp = new JsonObject();
        tokenProp.addProperty("type", "string");
        tokenProp.addProperty("description", "The search token returned by frostwire_search");
        properties.add("token", tokenProp);

        schema.add("properties", properties);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("token");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        String tokenStr = arguments.get("token").getAsString();
        long token = Long.parseLong(tokenStr);

        SearchSession session = SearchSessionManager.instance().getSession(token);
        JsonObject result = new JsonObject();

        if (session == null) {
            result.addProperty("status", "not_found");
            result.addProperty("error", "No search session found for token " + tokenStr);
            return result;
        }

        if (session.getError() != null) {
            result.addProperty("status", "error");
            result.addProperty("error", session.getError());
        } else if (session.isComplete()) {
            result.addProperty("status", "complete");
        } else {
            result.addProperty("status", "running");
        }

        result.addProperty("resultCount", session.getResults().size());
        result.addProperty("keywords", session.getKeywords());

        return result;
    }
}
