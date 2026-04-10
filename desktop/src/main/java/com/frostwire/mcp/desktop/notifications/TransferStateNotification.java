package com.frostwire.mcp.desktop.notifications;

import com.frostwire.mcp.MCPNotification;
import com.google.gson.JsonObject;

public class TransferStateNotification implements MCPNotification {

    private final String downloadId;
    private final String oldState;
    private final String newState;
    private final int progress;

    public TransferStateNotification(String downloadId, String oldState, String newState, int progress) {
        this.downloadId = downloadId;
        this.oldState = oldState;
        this.newState = newState;
        this.progress = progress;
    }

    @Override
    public String method() {
        return "notifications/transfer_state_changed";
    }

    @Override
    public JsonObject payload() {
        JsonObject p = new JsonObject();
        p.addProperty("downloadId", downloadId);
        p.addProperty("oldState", oldState);
        p.addProperty("newState", newState);
        p.addProperty("progress", progress);
        return p;
    }
}
