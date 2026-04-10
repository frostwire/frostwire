package com.frostwire.mcp.desktop;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTDownload;
import com.frostwire.mcp.MCPResource;
import com.frostwire.mcp.MCPServer;
import com.frostwire.mcp.desktop.adapters.SettingsAdapter;
import com.frostwire.mcp.desktop.adapters.TransferAdapter;
import com.frostwire.mcp.desktop.tools.btengine.BTEnginePauseTool;
import com.frostwire.mcp.desktop.tools.btengine.BTEngineResumeTool;
import com.frostwire.mcp.desktop.tools.btengine.BTEngineStatusTool;
import com.frostwire.mcp.desktop.tools.btengine.CreateTorrentTool;
import com.frostwire.mcp.desktop.tools.cloud.CloudDownloadTool;
import com.frostwire.mcp.desktop.tools.cloud.CloudSearchTool;
import com.frostwire.mcp.desktop.tools.ipfilter.IPFilterAddTool;
import com.frostwire.mcp.desktop.tools.ipfilter.IPFilterClearTool;
import com.frostwire.mcp.desktop.tools.ipfilter.IPFilterImportTool;
import com.frostwire.mcp.desktop.tools.ipfilter.IPFilterListTool;
import com.frostwire.mcp.desktop.tools.library.LibraryDeleteTool;
import com.frostwire.mcp.desktop.tools.library.LibraryFileDetailTool;
import com.frostwire.mcp.desktop.tools.library.LibraryListTool;
import com.frostwire.mcp.desktop.tools.library.LibraryOpenTool;
import com.frostwire.mcp.desktop.tools.library.LibraryScanTool;
import com.frostwire.mcp.desktop.tools.search.SearchCancelTool;
import com.frostwire.mcp.desktop.tools.search.SearchEnginesTool;
import com.frostwire.mcp.desktop.tools.search.SearchEngineToggleTool;
import com.frostwire.mcp.desktop.tools.search.SearchResultsTool;
import com.frostwire.mcp.desktop.tools.search.SearchStatusTool;
import com.frostwire.mcp.desktop.tools.search.SearchTool;
import com.frostwire.mcp.desktop.tools.settings.SettingsGetTool;
import com.frostwire.mcp.desktop.tools.settings.SettingsSetTool;
import com.frostwire.mcp.desktop.tools.transfer.DownloadHttpTool;
import com.frostwire.mcp.desktop.tools.transfer.DownloadTorrentTool;
import com.frostwire.mcp.desktop.tools.transfer.TransferAnnounceTool;
import com.frostwire.mcp.desktop.tools.transfer.TransferDetailTool;
import com.frostwire.mcp.desktop.tools.transfer.TransferFilePriorityTool;
import com.frostwire.mcp.desktop.tools.transfer.TransferPauseTool;
import com.frostwire.mcp.desktop.tools.transfer.TransferRecheckTool;
import com.frostwire.mcp.desktop.tools.transfer.TransferResumeTool;
import com.frostwire.mcp.desktop.tools.transfer.TransferRemoveTool;
import com.frostwire.mcp.desktop.tools.transfer.TransferSequentialTool;
import com.frostwire.mcp.desktop.tools.transfer.TransferSpeedLimitTool;
import com.frostwire.mcp.desktop.tools.transfer.TransfersListTool;
import com.frostwire.mcp.desktop.tools.vpn.VPNDropProtectionTool;
import com.frostwire.mcp.desktop.tools.vpn.VPNStatusTool;
import com.frostwire.mcp.transport.StreamableHttpTransport;
import com.frostwire.mcp.transport.TlsConfig;
import com.frostwire.transfers.TransferItem;import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.VPNs;
import com.limegroup.gnutella.settings.ConnectionSettings;

import java.util.List;

public final class FrostWireMCPServerFactory {

    private FrostWireMCPServerFactory() {
    }

    public static MCPServer createServer(String host, int port, TlsConfig tlsConfig) {
        MCPServer server = new MCPServer();
        registerAllTools(server);
        registerAllResources(server);
        StreamableHttpTransport transport = new StreamableHttpTransport(host, port, tlsConfig);
        server.setTransport(transport);
        return server;
    }

    private static void registerAllTools(MCPServer server) {
        server.registerTool(new SearchTool());
        server.registerTool(new SearchStatusTool());
        server.registerTool(new SearchResultsTool());
        server.registerTool(new SearchCancelTool());
        server.registerTool(new SearchEnginesTool());
        server.registerTool(new SearchEngineToggleTool());

        server.registerTool(new DownloadTorrentTool());
        server.registerTool(new DownloadHttpTool());
        server.registerTool(new TransfersListTool());
        server.registerTool(new TransferDetailTool());
        server.registerTool(new TransferPauseTool());
        server.registerTool(new TransferResumeTool());
        server.registerTool(new TransferRemoveTool());
        server.registerTool(new TransferSpeedLimitTool());
        server.registerTool(new TransferSequentialTool());
        server.registerTool(new TransferRecheckTool());
        server.registerTool(new TransferAnnounceTool());
        server.registerTool(new TransferFilePriorityTool());

        server.registerTool(new CloudSearchTool());
        server.registerTool(new CloudDownloadTool());

        server.registerTool(new LibraryScanTool());
        server.registerTool(new LibraryListTool());
        server.registerTool(new LibraryFileDetailTool());
        server.registerTool(new LibraryOpenTool());
        server.registerTool(new LibraryDeleteTool());

        server.registerTool(new BTEngineStatusTool());
        server.registerTool(new BTEnginePauseTool());
        server.registerTool(new BTEngineResumeTool());
        server.registerTool(new CreateTorrentTool());

        server.registerTool(new SettingsGetTool());
        server.registerTool(new SettingsSetTool());

        server.registerTool(new VPNStatusTool());
        server.registerTool(new VPNDropProtectionTool());

        server.registerTool(new IPFilterListTool());
        server.registerTool(new IPFilterAddTool());
        server.registerTool(new IPFilterClearTool());
        server.registerTool(new IPFilterImportTool());
    }

    private static void registerAllResources(MCPServer server) {
        server.registerResource(new TransfersResource());
        server.registerResource(new TransferDetailResource());
        server.registerResource(new TransferFilesResource());
        server.registerResource(new LibraryResource());
        server.registerResource(new SettingsResource());
        server.registerResource(new BTEngineStatusResource());
        server.registerResource(new VPNStatusResource());
    }

    private static final class TransfersResource implements MCPResource {
        @Override
        public String uri() { return "frostwire://transfers"; }
        @Override
        public String name() { return "Transfers"; }
        @Override
        public String description() { return "Live list of all transfers"; }
        @Override
        public String mimeType() { return "application/json"; }
        @Override
        public String read() {
            JsonArray transfers = new JsonArray();
            for (BTDownload dl : TransferAdapter.getAllDownloads()) {
                transfers.add(TransferAdapter.toSummaryJson(dl));
            }
            return new Gson().toJson(transfers);
        }
    }

    private static final class TransferDetailResource implements MCPResource {
        @Override
        public String uri() { return "frostwire://transfers/detail"; }
        @Override
        public String name() { return "Transfer Details"; }
        @Override
        public String description() { return "Detailed status of all active transfers"; }
        @Override
        public String mimeType() { return "application/json"; }
        @Override
        public String read() {
            JsonArray details = new JsonArray();
            for (BTDownload dl : TransferAdapter.getAllDownloads()) {
                JsonObject detail = TransferAdapter.toSummaryJson(dl);
                detail.addProperty("connectedPeers", dl.getConnectedPeers());
                detail.addProperty("totalPeers", dl.getTotalPeers());
                detail.addProperty("connectedSeeds", dl.getConnectedSeeds());
                detail.addProperty("totalSeeds", dl.getTotalSeeds());
                detail.addProperty("sequential", dl.isSequentialDownload());
                details.add(detail);
            }
            return new Gson().toJson(details);
        }
    }

    private static final class TransferFilesResource implements MCPResource {
        @Override
        public String uri() { return "frostwire://transfers/files"; }
        @Override
        public String name() { return "Transfer Files"; }
        @Override
        public String description() { return "File list for all transfers"; }
        @Override
        public String mimeType() { return "application/json"; }
        @Override
        public String read() {
            JsonArray allFiles = new JsonArray();
            for (BTDownload dl : TransferAdapter.getAllDownloads()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("downloadId", dl.getInfoHash());
                entry.addProperty("name", dl.getName());
                List<TransferItem> items = dl.getItems();
                entry.add("files", TransferAdapter.toItemsJson(items));
                allFiles.add(entry);
            }
            return new Gson().toJson(allFiles);
        }
    }

    private static final class LibraryResource implements MCPResource {
        @Override
        public String uri() { return "frostwire://library"; }
        @Override
        public String name() { return "Library"; }
        @Override
        public String description() { return "FrostWire media library overview"; }
        @Override
        public String mimeType() { return "application/json"; }
        @Override
        public String read() {
            JsonObject library = new JsonObject();
            library.addProperty("status", "available");
            return new Gson().toJson(library);
        }
    }

    private static final class SettingsResource implements MCPResource {
        @Override
        public String uri() { return "frostwire://settings"; }
        @Override
        public String name() { return "Settings"; }
        @Override
        public String description() { return "Current FrostWire settings snapshot"; }
        @Override
        public String mimeType() { return "application/json"; }
        @Override
        public String read() {
            JsonObject settings = new JsonObject();
            settings.add("connection", SettingsAdapter.getCategorySettings("connection"));
            settings.add("sharing", SettingsAdapter.getCategorySettings("sharing"));
            settings.add("search", SettingsAdapter.getCategorySettings("search"));
            settings.add("vpn", SettingsAdapter.getCategorySettings("vpn"));
            return new Gson().toJson(settings);
        }
    }

    private static final class BTEngineStatusResource implements MCPResource {
        @Override
        public String uri() { return "frostwire://btengine/status"; }
        @Override
        public String name() { return "BTEngine Status"; }
        @Override
        public String description() { return "Current BitTorrent engine status"; }
        @Override
        public String mimeType() { return "application/json"; }
        @Override
        public String read() {
            JsonObject status = new JsonObject();
            try {
                BTEngine engine = BTEngine.getInstance();
                status.addProperty("running", engine.isRunning());
                status.addProperty("paused", engine.isPausedCached());
                status.addProperty("totalDownload", engine.totalDownload());
                status.addProperty("totalUpload", engine.totalUpload());
            } catch (Throwable e) {
                status.addProperty("error", e.getMessage());
            }
            return new Gson().toJson(status);
        }
    }

    private static final class VPNStatusResource implements MCPResource {
        @Override
        public String uri() { return "frostwire://vpn/status"; }
        @Override
        public String name() { return "VPN Status"; }
        @Override
        public String description() { return "Current VPN protection status"; }
        @Override
        public String mimeType() { return "application/json"; }
        @Override
        public String read() {
            JsonObject status = new JsonObject();
            status.addProperty("vpnActive", VPNs.isVPNActive());
            status.addProperty("dropProtectionEnabled", ConnectionSettings.VPN_DROP_PROTECTION.getValue());
            return new Gson().toJson(status);
        }
    }
}
