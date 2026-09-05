/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.notifications;

import com.frostwire.mcp.MCPNotification;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class SearchResultNotification implements MCPNotification {

    private final String token;
    private final int resultCount;
    private final JsonArray resultsPreview;

    public SearchResultNotification(String token, int resultCount, JsonArray resultsPreview) {
        this.token = token;
        this.resultCount = resultCount;
        this.resultsPreview = resultsPreview;
    }

    @Override
    public String method() {
        return "notifications/search_result";
    }

    @Override
    public JsonObject payload() {
        JsonObject p = new JsonObject();
        p.addProperty("token", token);
        p.addProperty("resultCount", resultCount);
        if (resultsPreview != null && resultsPreview.size() > 0) {
            p.add("resultsPreview", resultsPreview);
        }
        return p;
    }
}
