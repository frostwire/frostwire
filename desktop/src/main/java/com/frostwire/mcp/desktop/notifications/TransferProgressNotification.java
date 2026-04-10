package com.frostwire.mcp.desktop.notifications;

import com.frostwire.mcp.MCPNotification;
import com.google.gson.JsonObject;

public class TransferProgressNotification implements MCPNotification {

    private final String downloadId;
    private final int progress;
    private final long downloadSpeed;
    private final long uploadSpeed;
    private final long eta;
    private final long downloaded;
    private final long uploaded;

    public TransferProgressNotification(String downloadId, int progress, long downloadSpeed, long uploadSpeed, long eta, long downloaded, long uploaded) {
        this.downloadId = downloadId;
        this.progress = progress;
        this.downloadSpeed = downloadSpeed;
        this.uploadSpeed = uploadSpeed;
        this.eta = eta;
        this.downloaded = downloaded;
        this.uploaded = uploaded;
    }

    @Override
    public String method() {
        return "notifications/transfer_progress";
    }

    @Override
    public JsonObject payload() {
        JsonObject p = new JsonObject();
        p.addProperty("downloadId", downloadId);
        p.addProperty("progress", progress);
        p.addProperty("downloadSpeed", downloadSpeed);
        p.addProperty("uploadSpeed", uploadSpeed);
        p.addProperty("eta", eta);
        p.addProperty("downloaded", downloaded);
        p.addProperty("uploaded", uploaded);
        return p;
    }
}
