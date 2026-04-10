package com.frostwire.mcp.desktop.tools.vpn;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.mcp.MCPTool;
import com.frostwire.util.Logger;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.VPNs;
import com.limegroup.gnutella.settings.ConnectionSettings;

public class VPNDropProtectionTool implements MCPTool {

    private static final Logger LOG = Logger.getLogger(VPNDropProtectionTool.class);

    @Override
    public String name() {
        return "frostwire_vpn_set_drop_protection";
    }

    @Override
    public String description() {
        return "Enable or disable VPN drop protection. When enabled, BitTorrent will only run when a VPN connection is active.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        JsonObject enabledProp = new JsonObject();
        enabledProp.addProperty("type", "boolean");
        enabledProp.addProperty("description", "Whether VPN drop protection should be enabled");
        properties.add("enabled", enabledProp);

        schema.add("properties", properties);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("enabled");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        boolean enabled = arguments.get("enabled").getAsBoolean();

        ConnectionSettings.VPN_DROP_PROTECTION.setValue(enabled);

        BTEngine engine = BTEngine.getInstance();
        if (engine != null && engine.swig() != null) {
            if (enabled && !VPNs.isVPNActive()) {
                engine.pause();
            } else if (!enabled && engine.isPausedCached()) {
                engine.resume();
            }
        }

        try {
            GUIMediator mediator = GUIMediator.instance();
            if (mediator != null) {
                mediator.getStatusLine().updateVPNDropProtectionLabelState();
                mediator.getStatusLine().refresh();
            }
        } catch (Exception e) {
            LOG.warn("VPNDropProtectionTool: Could not update GUI status line: " + e.getMessage());
        }

        JsonObject result = new JsonObject();
        result.addProperty("enabled", ConnectionSettings.VPN_DROP_PROTECTION.getValue());
        return result;
    }
}