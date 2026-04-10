package com.frostwire.mcp.desktop.tools.library;

import com.frostwire.mcp.MCPTool;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.awt.Desktop;
import java.io.File;

public final class LibraryOpenTool implements MCPTool {

    @Override
    public String name() {
        return "frostwire_library_open";
    }

    @Override
    public String description() {
        return "Open a file with the system's default application.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject pathProp = new JsonObject();
        pathProp.addProperty("type", "string");
        pathProp.addProperty("description", "Absolute path to the file to open");
        props.add("path", pathProp);
        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("path");
        schema.add("required", required);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        if (arguments == null || !arguments.has("path")) {
            return errorResult("Missing required parameter: path");
        }
        String path = arguments.get("path").getAsString();
        if (path == null || path.isEmpty()) {
            return errorResult("path parameter must not be empty");
        }
        File file = new File(path);
        if (!file.exists()) {
            return errorResult("File does not exist: " + path);
        }
        try {
            Desktop.getDesktop().open(file);
            JsonObject result = new JsonObject();
            result.addProperty("opened", true);
            result.addProperty("path", file.getAbsolutePath());
            return result;
        } catch (Exception e) {
            return errorResult("Failed to open file: " + e.getMessage());
        }
    }

    private JsonObject errorResult(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("error", message);
        return result;
    }
}
