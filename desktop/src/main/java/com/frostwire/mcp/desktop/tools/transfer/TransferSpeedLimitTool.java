package com.frostwire.mcp.desktop.tools.transfer;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.adapters.TransferAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

public class TransferSpeedLimitTool implements MCPTool {

    @Override
    public String name() {
        return "frostwire_transfer_set_speed_limit";
    }

    @Override
    public String description() {
        return "Set download and/or upload speed limits for a torrent transfer (KB/s, 0 = unlimited)";
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
        JsonObject downloadLimit = new JsonObject();
        downloadLimit.addProperty("type", "integer");
        downloadLimit.addProperty("description", "Download speed limit in KB/s (0 = unlimited)");
        properties.add("downloadLimit", downloadLimit);
        JsonObject uploadLimit = new JsonObject();
        uploadLimit.addProperty("type", "integer");
        uploadLimit.addProperty("description", "Upload speed limit in KB/s (0 = unlimited)");
        properties.add("uploadLimit", uploadLimit);
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

        if (!arguments.has("downloadLimit") && !arguments.has("uploadLimit")) {
            return TransferAdapter.errorJson("At least one of downloadLimit or uploadLimit is required");
        }

        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    BTDownload dl = TransferAdapter.findDownload(downloadId);
                    if (dl == null) {
                        return TransferAdapter.errorJson("Download not found: " + downloadId);
                    }
                    int dlLimit = dl.getDownloadRateLimit();
                    int ulLimit = dl.getUploadRateLimit();
                    if (arguments.has("downloadLimit")) {
                        dlLimit = arguments.get("downloadLimit").getAsInt();
                        dl.setDownloadRateLimit(dlLimit);
                    }
                    if (arguments.has("uploadLimit")) {
                        ulLimit = arguments.get("uploadLimit").getAsInt();
                        dl.setUploadRateLimit(ulLimit);
                    }
                    JsonObject result = new JsonObject();
                    result.addProperty("downloadId", downloadId);
                    result.addProperty("downloadLimit", dlLimit);
                    result.addProperty("uploadLimit", ulLimit);
                    return result;
                } catch (Exception e) {
                    return TransferAdapter.errorJson("Failed to set speed limit: " + e.getMessage());
                }
            }).join();
        } catch (Exception e) {
            return TransferAdapter.errorJson("Failed to set speed limit: " + e.getMessage());
        }
    }
}
