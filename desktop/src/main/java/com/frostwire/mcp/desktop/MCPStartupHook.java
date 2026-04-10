package com.frostwire.mcp.desktop;

import com.frostwire.mcp.MCPServer;
import com.frostwire.mcp.desktop.notifications.MCPNotificationBridge;
import com.frostwire.mcp.desktop.transport.TlsConfig;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.settings.MCPSettings;

import java.util.concurrent.CompletableFuture;

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
        startServer();
    }

    public static void startServer() {
        if (mcpServer != null) {
            return;
        }

        String host = MCPSettings.MCP_SERVER_HOST.getValue();
        int port = MCPSettings.MCP_SERVER_PORT.getValue();
        TlsConfig tlsConfig = new TlsConfig();
        tlsConfig.setEnabled(MCPSettings.MCP_SERVER_TLS_ENABLED.getValue());

        CompletableFuture.runAsync(() -> {
            try {
                MCPServer server = FrostWireMCPServerFactory.createServer(host, port, tlsConfig);
                mcpServer = server;
                server.start();
                MCPNotificationBridge.instance().setServer(server);
                LOG.info("MCP server started on " + host + ":" + port);
            } catch (Throwable e) {
                LOG.error("Failed to start MCP server: " + e.getMessage(), e);
                mcpServer = null;
            }
        });
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

    public static void restartServer() {
        stopServer();
        startServer();
    }

    public static boolean isRunning() {
        MCPServer server = mcpServer;
        return server != null && server.isInitialized();
    }

    public static String getServerUrl() {
        String host = MCPSettings.MCP_SERVER_HOST.getValue();
        int port = MCPSettings.MCP_SERVER_PORT.getValue();
        String scheme = MCPSettings.MCP_SERVER_TLS_ENABLED.getValue() ? "https" : "http";
        return scheme + "://" + host + ":" + port + "/mcp";
    }
}
