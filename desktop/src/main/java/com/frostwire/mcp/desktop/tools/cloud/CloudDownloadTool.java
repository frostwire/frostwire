package com.frostwire.mcp.desktop.tools.cloud;

import com.frostwire.mcp.MCPTool;
import com.frostwire.search.telluride.TellurideAbstractListener;
import com.frostwire.search.telluride.TellurideLauncher;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.settings.SharingSettings;
import com.limegroup.gnutella.util.FrostWireUtils;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class CloudDownloadTool implements MCPTool {

    private static final long TIMEOUT_SECONDS = 300;

    @Override
    public String name() {
        return "frostwire_cloud_download";
    }

    @Override
    public String description() {
        return "Download media from a cloud URL (YouTube, SoundCloud, etc.) using Telluride.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject urlProp = new JsonObject();
        urlProp.addProperty("type", "string");
        urlProp.addProperty("description", "The cloud URL to download from");
        props.add("url", urlProp);
        JsonObject formatIdProp = new JsonObject();
        formatIdProp.addProperty("type", "string");
        formatIdProp.addProperty("description", "The format ID to download (from cloud search results)");
        props.add("formatId", formatIdProp);
        JsonObject audioOnlyProp = new JsonObject();
        audioOnlyProp.addProperty("type", "boolean");
        audioOnlyProp.addProperty("description", "Download audio only");
        props.add("audioOnly", audioOnlyProp);
        JsonObject saveDirProp = new JsonObject();
        saveDirProp.addProperty("type", "string");
        saveDirProp.addProperty("description", "Save directory (defaults to torrent data directory)");
        props.add("saveDir", saveDirProp);
        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("url");
        schema.add("required", required);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        if (arguments == null || !arguments.has("url")) {
            return errorResult("Missing required parameter: url");
        }
        String url = arguments.get("url").getAsString();
        if (url == null || url.isEmpty()) {
            return errorResult("url parameter must not be empty");
        }
        boolean audioOnly = arguments.has("audioOnly") && arguments.get("audioOnly").getAsBoolean();
        File executable = FrostWireUtils.getTellurideLauncherFile();
        if (executable == null || !executable.exists()) {
            return errorResult("Telluride executable not found");
        }
        File saveDir;
        if (arguments.has("saveDir") && !arguments.get("saveDir").isJsonNull()) {
            saveDir = new File(arguments.get("saveDir").getAsString());
        } else {
            saveDir = SharingSettings.TORRENT_DATA_DIR_SETTING.getValue();
        }
        String downloadId = UUID.randomUUID().toString();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> destinationRef = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();
        AtomicReference<Integer> exitCodeRef = new AtomicReference<>();
        TellurideAbstractListener listener = new TellurideAbstractListener() {
            @Override
            public void onDestination(String outputFilename) {
                destinationRef.set(outputFilename);
            }

            @Override
            public void onError(String errorMessage) {
                errorRef.set(errorMessage);
                latch.countDown();
            }

            @Override
            public void onFinished(int exitCode) {
                exitCodeRef.set(exitCode);
                latch.countDown();
            }
        };
        TellurideLauncher.launch(executable, url, saveDir, audioOnly, false, false, listener);
        JsonObject result = new JsonObject();
        result.addProperty("downloadId", downloadId);
        result.addProperty("state", "downloading");
        try {
            boolean completed = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (completed) {
                String err = errorRef.get();
                if (err != null) {
                    result.addProperty("state", "failed");
                    result.addProperty("error", err);
                } else {
                    Integer exitCode = exitCodeRef.get();
                    if (exitCode != null && exitCode == 0) {
                        result.addProperty("state", "completed");
                        String dest = destinationRef.get();
                        if (dest != null) {
                            result.addProperty("destination", dest);
                        }
                    } else {
                        result.addProperty("state", "failed");
                        result.addProperty("exitCode", exitCode != null ? exitCode : -1);
                    }
                }
            } else {
                result.addProperty("state", "in_progress");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.addProperty("state", "interrupted");
        }
        return result;
    }

    private JsonObject errorResult(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("error", message);
        return result;
    }
}
