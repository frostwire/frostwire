package com.frostwire.mcp.desktop.notifications;

import com.frostwire.mcp.MCPNotification;
import com.google.gson.JsonObject;

public class VPNStatusNotification implements MCPNotification {

    private final boolean vpnActive;
    private final boolean dropProtectionEnabled;

    public VPNStatusNotification(boolean vpnActive, boolean dropProtectionEnabled) {
        this.vpnActive = vpnActive;
        this.dropProtectionEnabled = dropProtectionEnabled;
    }

    @Override
    public String method() {
        return "notifications/vpn_status_changed";
    }

    @Override
    public JsonObject payload() {
        JsonObject p = new JsonObject();
        p.addProperty("vpnActive", vpnActive);
        p.addProperty("dropProtectionEnabled", dropProtectionEnabled);
        return p;
    }
}
