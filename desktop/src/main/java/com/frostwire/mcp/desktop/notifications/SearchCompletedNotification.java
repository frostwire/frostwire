/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.notifications;

import com.frostwire.mcp.MCPNotification;
import com.google.gson.JsonObject;

public class SearchCompletedNotification implements MCPNotification {

    private final String token;

    public SearchCompletedNotification(String token) {
        this.token = token;
    }

    @Override
    public String method() {
        return "notifications/search_completed";
    }

    @Override
    public JsonObject payload() {
        JsonObject p = new JsonObject();
        p.addProperty("token", token);
        return p;
    }
}
