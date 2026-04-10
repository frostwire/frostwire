package com.frostwire.mcp.desktop.tools.transfer;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.adapters.TransferAdapter;
import com.frostwire.transfers.TransferState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TransfersListTool implements MCPTool {

    @Override
    public String name() {
        return "frostwire_transfers_list";
    }

    @Override
    public String description() {
        return "List all torrent transfers with optional state filtering and pagination";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject filter = new JsonObject();
        filter.addProperty("type", "string");
        filter.addProperty("description", "Filter by state: all, downloading, seeding, completed, paused, error");
        schema.add("filter", filter);
        JsonObject offset = new JsonObject();
        offset.addProperty("type", "integer");
        offset.addProperty("description", "Offset for pagination (default: 0)");
        schema.add("offset", offset);
        JsonObject limit = new JsonObject();
        limit.addProperty("type", "integer");
        limit.addProperty("description", "Maximum number of transfers to return (default: 50)");
        schema.add("limit", limit);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        String filter = arguments.has("filter") ? arguments.get("filter").getAsString() : "all";
        int offset = arguments.has("offset") ? arguments.get("offset").getAsInt() : 0;
        int limit = arguments.has("limit") ? arguments.get("limit").getAsInt() : 50;

        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    List<BTDownload> allDownloads = TransferAdapter.getAllDownloads();
                    List<BTDownload> filtered = filterDownloads(allDownloads, filter);
                    int total = filtered.size();
                    int end = Math.min(offset + limit, total);
                    List<BTDownload> paged = offset < total ? filtered.subList(offset, end) : new ArrayList<>();

                    JsonArray transfers = new JsonArray();
                    for (BTDownload dl : paged) {
                        transfers.add(TransferAdapter.toSummaryJson(dl));
                    }

                    JsonObject result = new JsonObject();
                    result.add("transfers", transfers);
                    result.addProperty("total", total);
                    result.addProperty("offset", offset);
                    result.addProperty("limit", limit);
                    return result;
                } catch (Exception e) {
                    return TransferAdapter.errorJson("Failed to list transfers: " + e.getMessage());
                }
            }).join();
        } catch (Exception e) {
            return TransferAdapter.errorJson("Failed to list transfers: " + e.getMessage());
        }
    }

    private List<BTDownload> filterDownloads(List<BTDownload> downloads, String filter) {
        if ("all".equalsIgnoreCase(filter)) {
            return downloads;
        }
        List<BTDownload> result = new ArrayList<>();
        for (BTDownload dl : downloads) {
            TransferState state = dl.getState();
            switch (filter.toLowerCase()) {
                case "downloading":
                    if (state == TransferState.DOWNLOADING || state == TransferState.DOWNLOADING_METADATA) {
                        result.add(dl);
                    }
                    break;
                case "seeding":
                    if (state == TransferState.SEEDING) {
                        result.add(dl);
                    }
                    break;
                case "completed":
                    if (state == TransferState.SEEDING || state == TransferState.FINISHED || state == TransferState.COMPLETE) {
                        result.add(dl);
                    }
                    break;
                case "paused":
                    if (state == TransferState.PAUSED) {
                        result.add(dl);
                    }
                    break;
                case "error":
                    if (TransferState.isErrored(state)) {
                        result.add(dl);
                    }
                    break;
                default:
                    result.add(dl);
                    break;
            }
        }
        return result;
    }
}
