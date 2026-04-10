package com.frostwire.mcp.desktop.tools.search;

import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.state.SearchSession;
import com.frostwire.mcp.desktop.state.SearchSessionManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class SearchCancelTool implements MCPTool {
    @Override
    public String name() {
        return "frostwire_search_cancel";
    }

    @Override
    public String description() {
        return "Cancel a running search session.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject tokenProp = new JsonObject();
        tokenProp.addProperty("type", "string");
        tokenProp.addProperty("description", "The search token to cancel");
        properties.add("token", tokenProp);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
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
            result.addProperty("cancelled", false);
            result.addProperty("error", "No search session found for token " + tokenStr);
            return result;
        }

        session.cancel();
        result.addProperty("cancelled", true);
        result.addProperty("token", tokenStr);
        return result;
    }
}
