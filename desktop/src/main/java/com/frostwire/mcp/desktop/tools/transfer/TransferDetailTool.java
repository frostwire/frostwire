package com.frostwire.mcp.desktop.tools.transfer;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.adapters.TransferAdapter;
import com.frostwire.transfers.TransferItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TransferDetailTool implements MCPTool {

    @Override
    public String name() {
        return "frostwire_transfer_detail";
    }

    @Override
    public String description() {
        return "Get detailed information about a specific torrent transfer including file items";
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
        schema.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("downloadId");
        schema.add("required", required);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        String downloadId = arguments.has("downloadId") ? arguments.get("downloadId").getAsString() : "";

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
                    JsonObject result = TransferAdapter.toSummaryJson(dl);
                    List<TransferItem> items = dl.getItems();
                    result.add("items", TransferAdapter.toItemsJson(items));
                    return result;
                } catch (Exception e) {
                    return TransferAdapter.errorJson("Failed to get transfer detail: " + e.getMessage());
                }
            }).join();
        } catch (Exception e) {
            return TransferAdapter.errorJson("Failed to get transfer detail: " + e.getMessage());
        }
    }
}
