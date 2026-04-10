package com.frostwire.mcp.desktop.tools.library;

import com.frostwire.mcp.MCPTool;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;

public final class LibraryDeleteTool implements MCPTool {

    @Override
    public String name() {
        return "frostwire_library_delete";
    }

    @Override
    public String description() {
        return "Delete one or more files from the library.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject pathsProp = new JsonObject();
        pathsProp.addProperty("type", "array");
        pathsProp.addProperty("description", "Array of absolute file paths to delete");
        JsonObject itemsProp = new JsonObject();
        itemsProp.addProperty("type", "string");
        pathsProp.add("items", itemsProp);
        props.add("paths", pathsProp);
        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("paths");
        schema.add("required", required);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        if (arguments == null || !arguments.has("paths")) {
            return errorResult("Missing required parameter: paths");
        }
        if (!arguments.get("paths").isJsonArray()) {
            return errorResult("paths parameter must be an array");
        }
        JsonArray paths = arguments.getAsJsonArray("paths");
        if (paths.size() == 0) {
            return errorResult("paths array must not be empty");
        }
        int deleted = 0;
        int failed = 0;
        for (int i = 0; i < paths.size(); i++) {
            String pathStr = paths.get(i).getAsString();
            if (pathStr == null || pathStr.isEmpty()) {
                failed++;
                continue;
            }
            File file = new File(pathStr);
            if (!file.exists()) {
                failed++;
                continue;
            }
            if (file.delete()) {
                deleted++;
            } else {
                failed++;
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("deleted", deleted);
        result.addProperty("failed", failed);
        return result;
    }

    private JsonObject errorResult(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("error", message);
        return result;
    }
}
