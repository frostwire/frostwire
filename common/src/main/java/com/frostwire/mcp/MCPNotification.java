package com.frostwire.mcp;

import com.google.gson.JsonObject;

public interface MCPNotification {
    String method();
    JsonObject payload();
}
