package com.frostwire.mcp.desktop.tools.transfer;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.adapters.TransferAdapter;
import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

public class TransferAnnounceTool implements MCPTool {

    @Override
    public String name() {
        return "frostwire_transfer_announce";
    }

    @Override
    public String description() {
        return "Force a tracker announce (re-announce) for a torrent transfer";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject downloadId = new JsonObject();
        downloadId.addProperty("type", "string");
        downloadId.addProperty("description", "The info hash of the download to announce");
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
                    dl.requestTrackerAnnounce();
                    JsonObject result = new JsonObject();
                    result.addProperty("downloadId", downloadId);
                    result.addProperty("announced", true);
                    return result;
                } catch (Exception e) {
                    return TransferAdapter.errorJson("Failed to announce: " + e.getMessage());
                }
            }).join();
        } catch (Exception e) {
            return TransferAdapter.errorJson("Failed to announce: " + e.getMessage());
        }
    }
}
