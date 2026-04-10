package com.frostwire.mcp.desktop.tools.vpn;

import com.frostwire.mcp.MCPTool;
import com.frostwire.util.Logger;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.VPNs;
import com.limegroup.gnutella.settings.ConnectionSettings;

import java.net.NetworkInterface;
import java.util.Enumeration;

public class VPNStatusTool implements MCPTool {

    private static final Logger LOG = Logger.getLogger(VPNStatusTool.class);

    @Override
    public String name() {
        return "frostwire_vpn_status";
    }

    @Override
    public String description() {
        return "Check VPN connection status and VPN drop protection setting.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        JsonObject result = new JsonObject();
        try {
            boolean vpnActive = VPNs.isVPNActive();
            result.addProperty("active", vpnActive);
            if (vpnActive) {
                String vpnName = detectVPNInterfaceName();
                if (vpnName != null) {
                    result.addProperty("vpnName", vpnName);
                }
            }
        } catch (Exception e) {
            LOG.error("VPN detection error: " + e.getMessage(), e);
            result.addProperty("active", false);
        }
        result.addProperty("dropProtectionEnabled", ConnectionSettings.VPN_DROP_PROTECTION.getValue());
        return result;
    }

    private static String detectVPNInterfaceName() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    try {
                        if (!ni.isUp()) {
                            continue;
                        }
                        String name = ni.getName().toLowerCase();
                        if (name.startsWith("tun") || name.startsWith("tap") || name.startsWith("utun") ||
                                name.startsWith("ppp") || name.contains("vpn") || name.contains("wg")) {
                            return ni.getName();
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("detectVPNInterfaceName error: " + e.getMessage(), e);
        }
        return null;
    }
}