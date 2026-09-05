/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp;

import com.google.gson.JsonObject;

public interface MCPNotification {
    String method();
    JsonObject payload();
}
