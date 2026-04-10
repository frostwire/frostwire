package com.frostwire.mcp.transport;

import com.google.gson.JsonObject;

public interface MCPTransport {
    void start(MCPTransportHandler handler);
    void stop();
    void sendNotification(JsonObject notification);
    boolean isRunning();
}
