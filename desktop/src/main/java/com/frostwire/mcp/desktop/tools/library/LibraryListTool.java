package com.frostwire.mcp.desktop.tools.library;

import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.adapters.LibraryAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.settings.SharingSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LibraryListTool implements MCPTool {

    @Override
    public String name() {
        return "frostwire_library_list";
    }

    @Override
    public String description() {
        return "List files in the library, filtered by media type with sorting and pagination.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject mediaTypeProp = new JsonObject();
        mediaTypeProp.addProperty("type", "string");
        mediaTypeProp.addProperty("description", "Filter by media type: all, audio, video, images, documents, torrents");
        mediaTypeProp.addProperty("default", "all");
        props.add("mediaType", mediaTypeProp);
        JsonObject offsetProp = new JsonObject();
        offsetProp.addProperty("type", "integer");
        offsetProp.addProperty("description", "Pagination offset (0-based)");
        offsetProp.addProperty("default", 0);
        props.add("offset", offsetProp);
        JsonObject limitProp = new JsonObject();
        limitProp.addProperty("type", "integer");
        limitProp.addProperty("description", "Maximum number of results (default 100, max 1000)");
        limitProp.addProperty("default", 100);
        props.add("limit", limitProp);
        JsonObject sortByProp = new JsonObject();
        sortByProp.addProperty("type", "string");
        sortByProp.addProperty("description", "Sort by: name, size, date");
        sortByProp.addProperty("default", "name");
        props.add("sortBy", sortByProp);
        schema.add("properties", props);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        String mediaType = "all";
        int offset = 0;
        int limit = 100;
        String sortBy = "name";
        if (arguments != null) {
            if (arguments.has("mediaType") && !arguments.get("mediaType").isJsonNull()) {
                mediaType = arguments.get("mediaType").getAsString();
            }
            if (arguments.has("offset") && !arguments.get("offset").isJsonNull()) {
                offset = Math.max(0, arguments.get("offset").getAsInt());
            }
            if (arguments.has("limit") && !arguments.get("limit").isJsonNull()) {
                limit = Math.min(1000, Math.max(1, arguments.get("limit").getAsInt()));
            }
            if (arguments.has("sortBy") && !arguments.get("sortBy").isJsonNull()) {
                sortBy = arguments.get("sortBy").getAsString();
            }
        }
        File dataDir = SharingSettings.TORRENT_DATA_DIR_SETTING.getValue();
        if (!dataDir.exists() || !dataDir.isDirectory()) {
            JsonObject result = new JsonObject();
            result.addProperty("error", "Torrent data directory does not exist: " + dataDir.getAbsolutePath());
            return result;
        }
        List<File> files = collectFiles(dataDir, mediaType);
        sortFiles(files, sortBy);
        int total = files.size();
        int end = Math.min(offset + limit, total);
        JsonArray filesArray = new JsonArray();
        for (int i = offset; i < end; i++) {
            filesArray.add(LibraryAdapter.toFileJson(files.get(i)));
        }
        JsonObject result = new JsonObject();
        result.add("files", filesArray);
        result.addProperty("total", total);
        result.addProperty("offset", offset);
        result.addProperty("limit", limit);
        return result;
    }

    private List<File> collectFiles(File root, String mediaType) {
        List<File> result = new ArrayList<>();
        collectFilesRecursive(root, mediaType, result);
        return result;
    }

    private void collectFilesRecursive(File dir, String mediaType, List<File> result) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collectFilesRecursive(child, mediaType, result);
            } else {
                if ("all".equals(mediaType)) {
                    result.add(child);
                } else {
                    String name = child.getName();
                    int dot = name.lastIndexOf('.');
                    String ext = (dot >= 0 && dot < name.length() - 1) ? name.substring(dot + 1) : "";
                    if (mediaType.equals(LibraryAdapter.getMediaType(ext))) {
                        result.add(child);
                    }
                }
            }
        }
    }

    private void sortFiles(List<File> files, String sortBy) {
        Comparator<File> comparator;
        switch (sortBy) {
            case "size":
                comparator = Comparator.comparingLong(File::length).reversed();
                break;
            case "date":
                comparator = Comparator.comparingLong(File::lastModified).reversed();
                break;
            case "name":
            default:
                comparator = Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER);
                break;
        }
        files.sort(comparator);
    }
}
