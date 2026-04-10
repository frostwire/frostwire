package com.frostwire.mcp.desktop.tools.settings;

import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.adapters.SettingsAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class SettingsGetTool implements MCPTool {

    @Override
    public String name() {
        return "frostwire_settings_get";
    }

    @Override
    public String description() {
        return "Read FrostWire settings by category. Returns all settings in the requested category.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        JsonObject categoryProp = new JsonObject();
        categoryProp.addProperty("type", "string");
        categoryProp.addProperty("description", "Settings category: connection, library, sharing, search, or vpn");
        JsonArray enumValues = new JsonArray();
        enumValues.add("connection");
        enumValues.add("library");
        enumValues.add("sharing");
        enumValues.add("search");
        enumValues.add("vpn");
        categoryProp.add("enum", enumValues);
        properties.add("category", categoryProp);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("category");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        String category = arguments.get("category").getAsString();
        JsonObject result = SettingsAdapter.getCategorySettings(category);
        if (result.has("error")) {
            JsonObject error = new JsonObject();
            error.addProperty("error", result.get("error").getAsString());
            JsonArray validCategories = new JsonArray();
            validCategories.add("connection");
            validCategories.add("library");
            validCategories.add("sharing");
            validCategories.add("search");
            validCategories.add("vpn");
            error.add("validCategories", validCategories);
            return error;
        }
        return result;
    }
}