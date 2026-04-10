package com.frostwire.mcp.transport;

import com.google.gson.JsonObject;

public interface MCPTransportHandler {
    JsonObject handleRequest(JsonObject request);
}
