package com.frostwire.mcp.desktop.tools.library;

import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.adapters.LibraryAdapter;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.settings.SharingSettings;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;

public final class LibraryScanTool implements MCPTool {

    @Override
    public String name() {
        return "frostwire_library_scan";
    }

    @Override
    public String description() {
        return "Scan a directory for files, optionally filtering by media type. Returns the count of files found.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject pathProp = new JsonObject();
        pathProp.addProperty("type", "string");
        pathProp.addProperty("description", "Directory path to scan (defaults to torrent data directory)");
        props.add("path", pathProp);
        JsonObject mediaTypeProp = new JsonObject();
        mediaTypeProp.addProperty("type", "string");
        mediaTypeProp.addProperty("description", "Filter by media type: all, audio, video, images, documents, torrents");
        mediaTypeProp.addProperty("default", "all");
        props.add("mediaType", mediaTypeProp);
        schema.add("properties", props);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        File scanDir;
        if (arguments != null && arguments.has("path") && !arguments.get("path").isJsonNull()) {
            String path = arguments.get("path").getAsString();
            scanDir = new File(path);
            if (!scanDir.isAbsolute()) {
                scanDir = new File(SharingSettings.TORRENT_DATA_DIR_SETTING.getValue(), path);
            }
        } else {
            scanDir = SharingSettings.TORRENT_DATA_DIR_SETTING.getValue();
        }
        if (!scanDir.exists() || !scanDir.isDirectory()) {
            JsonObject result = new JsonObject();
            result.addProperty("error", "Directory does not exist: " + scanDir.getAbsolutePath());
            return result;
        }
        String mediaType = "all";
        if (arguments != null && arguments.has("mediaType") && !arguments.get("mediaType").isJsonNull()) {
            mediaType = arguments.get("mediaType").getAsString();
        }
        int count = scanFiles(scanDir, mediaType);
        JsonObject result = new JsonObject();
        result.addProperty("scanned", true);
        result.addProperty("fileCount", count);
        result.addProperty("path", scanDir.getAbsolutePath());
        return result;
    }

    private int scanFiles(File root, String mediaType) {
        int count = 0;
        Deque<File> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            File dir = stack.pop();
            File[] children = dir.listFiles();
            if (children == null) continue;
            for (File child : children) {
                if (child.isDirectory()) {
                    stack.push(child);
                } else {
                    if ("all".equals(mediaType)) {
                        count++;
                    } else {
                        String name = child.getName();
                        int dot = name.lastIndexOf('.');
                        String ext = (dot >= 0 && dot < name.length() - 1) ? name.substring(dot + 1) : "";
                        if (mediaType.equals(LibraryAdapter.getMediaType(ext))) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }
}
