package com.frostwire.mcp.desktop.tools.transfer;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.adapters.TransferAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

public class TransferRemoveTool implements MCPTool {

    @Override
    public String name() {
        return "frostwire_transfer_remove";
    }

    @Override
    public String description() {
        return "Remove a torrent transfer, optionally deleting downloaded data";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject downloadId = new JsonObject();
        downloadId.addProperty("type", "string");
        downloadId.addProperty("description", "The info hash of the download to remove");
        properties.add("downloadId", downloadId);
        JsonObject deleteData = new JsonObject();
        deleteData.addProperty("type", "boolean");
        deleteData.addProperty("description", "Whether to delete downloaded data files (default: false)");
        properties.add("deleteData", deleteData);
        schema.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("downloadId");
        schema.add("required", required);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        String downloadId = arguments.has("downloadId") ? arguments.get("downloadId").getAsString() : "";
        boolean deleteData = arguments.has("deleteData") && arguments.get("deleteData").getAsBoolean();

        if (downloadId.isEmpty()) {
            return TransferAdapter.errorJson("downloadId is required");
        }

        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    BTDownload dl = TransferAdapter.findDownload(downloadId);
                    if (dl == null) {
                        return TransferAdapter.errorJson("Download not found: " + downloadId);
                    }
                    dl.remove(deleteData);
                    JsonObject result = new JsonObject();
                    result.addProperty("downloadId", downloadId);
                    result.addProperty("removed", true);
                    return result;
                } catch (Exception e) {
                    return TransferAdapter.errorJson("Failed to remove transfer: " + e.getMessage());
                }
            }).join();
        } catch (Exception e) {
            return TransferAdapter.errorJson("Failed to remove transfer: " + e.getMessage());
        }
    }
}
