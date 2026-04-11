package com.frostwire.mcp.desktop.tools.transfer;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.adapters.TransferAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

public class TransferSequentialTool implements MCPTool {

    @Override
    public String name() {
        return "frostwire_transfer_set_sequential";
    }

    @Override
    public String description() {
        return "Enable or disable sequential download mode for a torrent transfer";
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
        JsonObject sequential = new JsonObject();
        sequential.addProperty("type", "boolean");
        sequential.addProperty("description", "Enable sequential download if true, disable if false");
        properties.add("sequential", sequential);
        schema.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("downloadId");
        required.add("sequential");
        schema.add("required", required);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        String downloadId = arguments.has("downloadId") ? arguments.get("downloadId").getAsString() : "";

        if (downloadId.isEmpty()) {
            return TransferAdapter.errorJson("downloadId is required");
        }

        if (!arguments.has("sequential")) {
            return TransferAdapter.errorJson("sequential is required");
        }

        boolean sequential = arguments.get("sequential").getAsBoolean();

        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    BTDownload dl = TransferAdapter.findDownload(downloadId);
                    if (dl == null) {
                        return TransferAdapter.errorJson("Download not found: " + downloadId);
                    }
                    dl.setSequentialDownload(sequential);
                    JsonObject result = new JsonObject();
                    result.addProperty("downloadId", downloadId);
                    result.addProperty("sequential", sequential);
                    return result;
                } catch (Exception e) {
                    return TransferAdapter.errorJson("Failed to set sequential mode: " + e.getMessage());
                }
            }).join();
        } catch (Exception e) {
            return TransferAdapter.errorJson("Failed to set sequential mode: " + e.getMessage());
        }
    }
}
