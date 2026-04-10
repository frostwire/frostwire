package com.frostwire.mcp.desktop.adapters;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.gui.bittorrent.BTDownloadMediator;
import com.frostwire.gui.bittorrent.BittorrentDownload;
import com.frostwire.transfers.TransferItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class TransferAdapter {

    private TransferAdapter() {
    }

    public static JsonObject toSummaryJson(BTDownload dl) {
        JsonObject json = new JsonObject();
        json.addProperty("id", dl.getInfoHash());
        json.addProperty("name", dl.getName());
        json.addProperty("displayName", dl.getDisplayName());
        json.addProperty("state", dl.getState().name());
        json.addProperty("progress", dl.getProgress());
        json.addProperty("size", dl.getSize());
        json.addProperty("downloaded", dl.getBytesReceived());
        json.addProperty("uploaded", dl.getBytesSent());
        json.addProperty("downloadSpeed", dl.getDownloadSpeed());
        json.addProperty("uploadSpeed", dl.getUploadSpeed());
        json.addProperty("eta", dl.getETA());

        JsonObject seeds = new JsonObject();
        seeds.addProperty("connected", dl.getConnectedSeeds());
        seeds.addProperty("total", dl.getTotalSeeds());
        json.add("seeds", seeds);

        JsonObject peers = new JsonObject();
        peers.addProperty("connected", dl.getConnectedPeers());
        peers.addProperty("total", dl.getTotalPeers());
        json.add("peers", peers);

        json.addProperty("infoHash", dl.getInfoHash());
        json.addProperty("savePath", dl.getSavePath() != null ? dl.getSavePath().getAbsolutePath() : "");
        json.addProperty("magnetUri", dl.magnetUri() != null ? dl.magnetUri() : "");
        json.addProperty("sequential", dl.isSequentialDownload());

        return json;
    }

    public static JsonObject toItemJson(TransferItem item) {
        JsonObject json = new JsonObject();
        json.addProperty("name", item.getName());
        json.addProperty("size", item.getSize());
        json.addProperty("downloaded", item.getDownloaded());
        json.addProperty("progress", item.getProgress());
        json.addProperty("complete", item.isComplete());
        json.addProperty("skipped", item.isSkipped());
        json.addProperty("path", item.getFile() != null ? item.getFile().getAbsolutePath() : "");
        return json;
    }

    public static JsonArray toItemsJson(List<TransferItem> items) {
        JsonArray arr = new JsonArray();
        for (TransferItem item : items) {
            arr.add(toItemJson(item));
        }
        return arr;
    }

    public static BTDownload findDownload(String downloadId) {
        List<com.frostwire.gui.bittorrent.BTDownload> downloads = BTDownloadMediator.instance().getDownloads();
        for (com.frostwire.gui.bittorrent.BTDownload btDownload : downloads) {
            if (btDownload instanceof BittorrentDownload) {
                BTDownload dl = ((BittorrentDownload) btDownload).getDl();
                if (dl != null && dl.getInfoHash() != null && dl.getInfoHash().equalsIgnoreCase(downloadId)) {
                    return dl;
                }
            }
        }
        return null;
    }

    public static List<BTDownload> getAllDownloads() {
        List<com.frostwire.gui.bittorrent.BTDownload> guiDownloads = BTDownloadMediator.instance().getDownloads();
        List<BTDownload> result = new ArrayList<>(guiDownloads.size());
        for (com.frostwire.gui.bittorrent.BTDownload btDownload : guiDownloads) {
            if (btDownload instanceof BittorrentDownload) {
                BTDownload dl = ((BittorrentDownload) btDownload).getDl();
                if (dl != null) {
                    result.add(dl);
                }
            }
        }
        return result;
    }

    public static JsonObject errorJson(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("error", message);
        return json;
    }
}
