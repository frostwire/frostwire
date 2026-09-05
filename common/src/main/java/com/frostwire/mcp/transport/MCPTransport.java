/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.transport;

import com.google.gson.JsonObject;

public interface MCPTransport {
    void start(MCPTransportHandler handler);
    void stop();
    void sendNotification(JsonObject notification);
    boolean isRunning();
}
