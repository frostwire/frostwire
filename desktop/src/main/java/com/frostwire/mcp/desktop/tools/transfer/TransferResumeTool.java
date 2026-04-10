package com.frostwire.mcp.desktop.tools.transfer;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.adapters.TransferAdapter;
import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

public class TransferResumeTool implements MCPTool {

    @Override
    public String name() {
        return "frostwire_transfer_resume";
    }

    @Override
    public String description() {
        return "Resume a paused torrent transfer";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject downloadId = new JsonObject();
        downloadId.addProperty("type", "string");
        downloadId.addProperty("description", "The info hash of the download to resume");
        schema.add("downloadId", downloadId);
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
                    dl.resume();
                    JsonObject result = new JsonObject();
                    result.addProperty("downloadId", downloadId);
                    result.addProperty("state", dl.getState().name());
                    return result;
                } catch (Exception e) {
                    return TransferAdapter.errorJson("Failed to resume transfer: " + e.getMessage());
                }
            }).join();
        } catch (Exception e) {
            return TransferAdapter.errorJson("Failed to resume transfer: " + e.getMessage());
        }
    }
}
