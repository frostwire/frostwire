package com.frostwire.mcp.desktop.tools.transfer;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.adapters.TransferAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

public class TransferFilePriorityTool implements MCPTool {

    @Override
    public String name() {
        return "frostwire_transfer_file_priority";
    }

    @Override
    public String description() {
        return "Set file priority for specific files in a torrent transfer (download, skip, normal, high)";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject downloadId = new JsonObject();
        downloadId.addProperty("type", "string");
        downloadId.addProperty("description", "The info hash of the download");
        properties.add("downloadId", downloadId);
        JsonObject fileIndices = new JsonObject();
        fileIndices.addProperty("type", "array");
        fileIndices.addProperty("description", "Array of file indices to set priority for");
        JsonObject itemsObj = new JsonObject();
        itemsObj.addProperty("type", "integer");
        fileIndices.add("items", itemsObj);
        properties.add("fileIndices", fileIndices);
        JsonObject priority = new JsonObject();
        priority.addProperty("type", "string");
        priority.addProperty("description", "Priority level: download, skip, normal, or high");
        properties.add("priority", priority);
        schema.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("downloadId");
        required.add("fileIndices");
        required.add("priority");
        schema.add("required", required);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        String downloadId = arguments.has("downloadId") ? arguments.get("downloadId").getAsString() : "";
        String priorityStr = arguments.has("priority") ? arguments.get("priority").getAsString() : "";

        if (downloadId.isEmpty()) {
            return TransferAdapter.errorJson("downloadId is required");
        }

        if (priorityStr.isEmpty()) {
            return TransferAdapter.errorJson("priority is required");
        }

        if (!arguments.has("fileIndices") || !arguments.get("fileIndices").isJsonArray()) {
            return TransferAdapter.errorJson("fileIndices array is required");
        }

        Priority priority = mapPriority(priorityStr);
        if (priority == null) {
            return TransferAdapter.errorJson("Invalid priority: " + priorityStr + ". Use download, skip, normal, or high.");
        }

        JsonArray indicesArr = arguments.getAsJsonArray("fileIndices");
        int[] fileIndices = new int[indicesArr.size()];
        for (int i = 0; i < indicesArr.size(); i++) {
            fileIndices[i] = indicesArr.get(i).getAsInt();
        }

        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    BTDownload dl = TransferAdapter.findDownload(downloadId);
                    if (dl == null) {
                        return TransferAdapter.errorJson("Download not found: " + downloadId);
                    }
                    TorrentHandle th = dl.getTorrentHandle();
                    if (th == null || !th.isValid()) {
                        return TransferAdapter.errorJson("Torrent handle is not valid for: " + downloadId);
                    }
                    Priority[] priorities = th.filePriorities();
                    int updatedCount = 0;
                    for (int idx : fileIndices) {
                        if (idx >= 0 && idx < priorities.length) {
                            priorities[idx] = priority;
                            updatedCount++;
                        }
                    }
                    if (updatedCount > 0) {
                        th.prioritizeFiles(priorities);
                    }
                    JsonObject result = new JsonObject();
                    result.addProperty("downloadId", downloadId);
                    result.addProperty("updatedFiles", updatedCount);
                    return result;
                } catch (Exception e) {
                    return TransferAdapter.errorJson("Failed to set file priority: " + e.getMessage());
                }
            }).join();
        } catch (Exception e) {
            return TransferAdapter.errorJson("Failed to set file priority: " + e.getMessage());
        }
    }

    private Priority mapPriority(String priority) {
        switch (priority.toLowerCase()) {
            case "download":
            case "normal":
                return Priority.NORMAL;
            case "skip":
                return Priority.IGNORE;
            case "high":
                return Priority.SEVEN;
            default:
                return null;
        }
    }
}
