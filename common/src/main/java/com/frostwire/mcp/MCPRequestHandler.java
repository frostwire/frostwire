package com.frostwire.mcp;

import com.google.gson.JsonObject;

public interface MCPRequestHandler {
    JsonObject handleRequest(JsonObject request);
}
