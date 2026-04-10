package com.frostwire.mcp.desktop.tools.transfer;

import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.adapters.TransferAdapter;
import com.frostwire.search.telluride.TellurideLauncher;
import com.frostwire.search.telluride.TellurideListener;
import com.frostwire.util.Logger;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.settings.SharingSettings;
import com.limegroup.gnutella.util.FrostWireUtils;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DownloadHttpTool implements MCPTool {

    private static final Logger LOG = Logger.getLogger(DownloadHttpTool.class);

    @Override
    public String name() {
        return "frostwire_download_http";
    }

    @Override
    public String description() {
        return "Download a file from an HTTP/HTTPS URL using Telluride (supports YouTube, SoundCloud, etc.)";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject url = new JsonObject();
        url.addProperty("type", "string");
        url.addProperty("description", "The HTTP/HTTPS URL to download");
        schema.add("url", url);
        JsonObject filename = new JsonObject();
        filename.addProperty("type", "string");
        filename.addProperty("description", "Optional output filename");
        schema.add("filename", filename);
        JsonObject saveDir = new JsonObject();
        saveDir.addProperty("type", "string");
        saveDir.addProperty("description", "Optional save directory path");
        schema.add("saveDir", saveDir);
        JsonObject extractAudio = new JsonObject();
        extractAudio.addProperty("type", "boolean");
        extractAudio.addProperty("description", "Extract audio only if true");
        schema.add("extractAudio", extractAudio);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        String url = arguments.has("url") ? arguments.get("url").getAsString() : "";
        boolean audioOnly = arguments.has("extractAudio") && arguments.get("extractAudio").getAsBoolean();
        File saveDir = arguments.has("saveDir") ? new File(arguments.get("saveDir").getAsString()) : SharingSettings.TORRENT_DATA_DIR_SETTING.getValue();

        if (url.isEmpty()) {
            return TransferAdapter.errorJson("url is required");
        }

        String downloadId = UUID.randomUUID().toString();

        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    File executable = FrostWireUtils.getTellurideLauncherFile();
                    if (executable == null || !executable.exists()) {
                        return TransferAdapter.errorJson("Telluride executable not found");
                    }
                    TellurideLauncher.launch(executable, url, saveDir, audioOnly, false, false, new TellurideListener() {
                        @Override
                        public void onProgress(float completionPercentage, float fileSize, String fileSizeUnits, float downloadSpeed, String downloadSpeedUnits, String ETA) {
                        }

                        @Override
                        public void onError(String errorMessage) {
                            LOG.warn("Telluride download error: " + errorMessage);
                        }

                        @Override
                        public void onFinished(int exitCode) {
                            LOG.info("Telluride download finished with exit code: " + exitCode);
                        }

                        @Override
                        public void onDestination(String outputFilename) {
                            LOG.info("Telluride download destination: " + outputFilename);
                        }

                        @Override
                        public boolean aborted() {
                            return false;
                        }

                        @Override
                        public void onMeta(String json) {
                        }
                    });
                    JsonObject result = new JsonObject();
                    result.addProperty("downloadId", downloadId);
                    result.addProperty("state", "downloading");
                    return result;
                } catch (Exception e) {
                    LOG.error("Error starting HTTP download", e);
                    return TransferAdapter.errorJson("HTTP download failed: " + e.getMessage());
                }
            }).join();
        } catch (Exception e) {
            LOG.error("Error in download HTTP tool", e);
            return TransferAdapter.errorJson("HTTP download failed: " + e.getMessage());
        }
    }
}
