package com.frostwire.mcp.desktop;

import com.frostwire.mcp.MCPServer;
import com.frostwire.mcp.desktop.notifications.MCPNotificationBridge;
import com.frostwire.mcp.transport.TlsConfig;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.settings.MCPSettings;

public final class MCPStartupHook {

    private static final Logger LOG = Logger.getLogger(MCPStartupHook.class);

    private static volatile MCPServer mcpServer;

    private MCPStartupHook() {
    }

    public static void initialize() {
        boolean enabled = MCPSettings.MCP_SERVER_ENABLED.getValue();
        boolean autoStart = MCPSettings.MCP_SERVER_AUTO_START.getValue();
        if (!enabled && !autoStart) {
            return;
        }
        try {
            startServer();
        } catch (Exception e) {
            LOG.error("MCP auto-start failed: " + e.getMessage(), e);
        }
    }

    public static void startServer() throws Exception {
        if (mcpServer != null) {
            LOG.info("MCP server already running, skipping start");
            return;
        }

        String host = MCPSettings.MCP_SERVER_HOST.getValue();
        int port = MCPSettings.MCP_SERVER_PORT.getValue();
        TlsConfig tlsConfig = new TlsConfig();
        tlsConfig.setEnabled(MCPSettings.MCP_SERVER_TLS_ENABLED.getValue());

        LOG.info("Starting MCP server on " + host + ":" + port + " (TLS=" + tlsConfig.isEnabled() + ")");
        try {
            MCPServer server = FrostWireMCPServerFactory.createServer(host, port, tlsConfig);
            mcpServer = server;
            server.start();
            MCPNotificationBridge.instance().setServer(server);
            LOG.info("MCP server started successfully on " + host + ":" + port);
        } catch (Throwable e) {
            mcpServer = null;
            LOG.error("Failed to start MCP server: " + e.getMessage(), e);
            throw new Exception("Failed to start MCP server: " + e.getMessage(), e);
        }
    }

    public static void stopServer() {
        MCPServer server = mcpServer;
        if (server != null) {
            try {
                MCPNotificationBridge.instance().setServer(null);
                server.stop();
            } catch (Throwable e) {
                LOG.error("Error stopping MCP server: " + e.getMessage(), e);
            } finally {
                mcpServer = null;
            }
        }
    }

    public static void restartServer() throws Exception {
        stopServer();
        startServer();
    }

    public static boolean isRunning() {
        MCPServer server = mcpServer;
        return server != null && server.isStarted();
    }

    public static String getServerUrl() {
        String host = MCPSettings.MCP_SERVER_HOST.getValue();
        int port = MCPSettings.MCP_SERVER_PORT.getValue();
        String scheme = MCPSettings.MCP_SERVER_TLS_ENABLED.getValue() ? "https" : "http";
        return scheme + "://" + host + ":" + port + "/mcp";
    }
}
