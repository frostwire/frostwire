package com.frostwire.mcp.desktop.tools.transfer;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTDownload;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.adapters.TransferAdapter;
import com.frostwire.search.LibTorrentMagnetDownloader;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.settings.SharingSettings;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

public class DownloadTorrentTool implements MCPTool {

    private static final Logger LOG = Logger.getLogger(DownloadTorrentTool.class);

    @Override
    public String name() {
        return "frostwire_download_torrent";
    }

    @Override
    public String description() {
        return "Start a torrent download from a magnet link, URL, or local .torrent file";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject source = new JsonObject();
        source.addProperty("type", "string");
        source.addProperty("description", "Source type: magnet, url, or file");
        properties.add("source", source);
        JsonObject value = new JsonObject();
        value.addProperty("type", "string");
        value.addProperty("description", "Magnet URI, torrent URL, or local file path");
        properties.add("value", value);
        JsonObject saveDir = new JsonObject();
        saveDir.addProperty("type", "string");
        saveDir.addProperty("description", "Optional save directory path");
        properties.add("saveDir", saveDir);
        schema.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("source");
        required.add("value");
        schema.add("required", required);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        String source = arguments.has("source") ? arguments.get("source").getAsString() : "";
        String value = arguments.has("value") ? arguments.get("value").getAsString() : "";
        File saveDir = arguments.has("saveDir") ? new File(arguments.get("saveDir").getAsString()) : SharingSettings.TORRENT_DATA_DIR_SETTING.getValue();

        if (source.isEmpty() || value.isEmpty()) {
            return TransferAdapter.errorJson("source and value are required");
        }

        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    switch (source.toLowerCase()) {
                        case "magnet":
                            return downloadMagnet(value, saveDir);
                        case "url":
                            return downloadUrl(value, saveDir);
                        case "file":
                            return downloadFile(value, saveDir);
                        default:
                            return TransferAdapter.errorJson("Invalid source type: " + source + ". Use magnet, url, or file.");
                    }
                } catch (Exception e) {
                    LOG.error("Error starting torrent download", e);
                    return TransferAdapter.errorJson("Download failed: " + e.getMessage());
                }
            }).join();
        } catch (Exception e) {
            LOG.error("Error in download torrent tool", e);
            return TransferAdapter.errorJson("Download failed: " + e.getMessage());
        }
    }

    private JsonObject downloadMagnet(String magnetUri, File saveDir) {
        try {
            LibTorrentMagnetDownloader magnetDownloader = new LibTorrentMagnetDownloader();
            byte[] data = magnetDownloader.download(magnetUri, 90);
            if (data == null) {
                return TransferAdapter.errorJson("Failed to fetch torrent metadata from magnet URI");
            }
            TorrentInfo ti = TorrentInfo.bdecode(data);
            BTEngine.getInstance().download(ti, saveDir, null, null, true);
            String infoHash = ti.infoHashV1() != null ? ti.infoHashV1().toString().toLowerCase() : ti.infoHashV2().toString().toLowerCase();
            JsonObject result = new JsonObject();
            result.addProperty("downloadId", infoHash);
            result.addProperty("state", "downloading");
            result.addProperty("name", ti.name());
            return result;
        } catch (Exception e) {
            LOG.error("Error downloading magnet", e);
            return TransferAdapter.errorJson("Magnet download failed: " + e.getMessage());
        }
    }

    private JsonObject downloadUrl(String url, File saveDir) {
        try {
            byte[] data = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD).getBytes(url, 30000);
            if (data == null) {
                return TransferAdapter.errorJson("Failed to download .torrent file from URL");
            }
            File tempFile = File.createTempFile("fwmcp_", ".torrent");
            tempFile.deleteOnExit();
            Files.copy(new java.io.ByteArrayInputStream(data), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            BTEngine.getInstance().download(tempFile, saveDir, null);
            TorrentInfo ti = new TorrentInfo(tempFile);
            String infoHash = ti.infoHashV1() != null ? ti.infoHashV1().toString().toLowerCase() : ti.infoHashV2().toString().toLowerCase();
            JsonObject result = new JsonObject();
            result.addProperty("downloadId", infoHash);
            result.addProperty("state", "downloading");
            result.addProperty("name", ti.name());
            return result;
        } catch (Exception e) {
            LOG.error("Error downloading torrent from URL", e);
            return TransferAdapter.errorJson("URL download failed: " + e.getMessage());
        }
    }

    private JsonObject downloadFile(String filePath, File saveDir) {
        try {
            File torrentFile = new File(filePath);
            if (!torrentFile.exists() || !torrentFile.canRead()) {
                return TransferAdapter.errorJson("Torrent file not found or not readable: " + filePath);
            }
            BTEngine.getInstance().download(torrentFile, saveDir, null);
            TorrentInfo ti = new TorrentInfo(torrentFile);
            String infoHash = ti.infoHashV1() != null ? ti.infoHashV1().toString().toLowerCase() : ti.infoHashV2().toString().toLowerCase();
            JsonObject result = new JsonObject();
            result.addProperty("downloadId", infoHash);
            result.addProperty("state", "downloading");
            result.addProperty("name", ti.name());
            return result;
        } catch (Exception e) {
            LOG.error("Error downloading torrent from file", e);
            return TransferAdapter.errorJson("File download failed: " + e.getMessage());
        }
    }
}
