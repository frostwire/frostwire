package com.frostwire.mcp.desktop.tools.cloud;

import com.frostwire.mcp.MCPTool;
import com.frostwire.search.telluride.TellurideAbstractListener;
import com.frostwire.search.telluride.TellurideLauncher;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.limegroup.gnutella.util.FrostWireUtils;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class CloudSearchTool implements MCPTool {

    private static final long TIMEOUT_SECONDS = 60;

    @Override
    public String name() {
        return "frostwire_cloud_search";
    }

    @Override
    public String description() {
        return "Search for downloadable media from a cloud URL (YouTube, SoundCloud, etc.). Returns available formats and metadata.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject urlProp = new JsonObject();
        urlProp.addProperty("type", "string");
        urlProp.addProperty("description", "The cloud URL to search (YouTube, SoundCloud, etc.)");
        props.add("url", urlProp);
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
        File executable = FrostWireUtils.getTellurideLauncherFile();
        if (executable == null || !executable.exists()) {
            return errorResult("Telluride executable not found");
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> metaRef = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();
        TellurideAbstractListener listener = new TellurideAbstractListener() {
            @Override
            public void onMeta(String json) {
                metaRef.set(json);
                latch.countDown();
            }

            @Override
            public void onError(String errorMessage) {
                errorRef.set(errorMessage);
                latch.countDown();
            }

            @Override
            public void onFinished(int exitCode) {
                latch.countDown();
            }
        };
        TellurideLauncher.launch(executable, url, null, false, true, false, false, listener);
        try {
            boolean completed = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                return errorResult("Cloud search timed out after " + TIMEOUT_SECONDS + " seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return errorResult("Cloud search interrupted");
        }
        String errorMsg = errorRef.get();
        if (errorMsg != null) {
            return errorResult(errorMsg);
        }
        String metaJson = metaRef.get();
        if (metaJson == null || metaJson.isEmpty()) {
            return errorResult("No metadata received from cloud source");
        }
        try {
            JsonElement parsed = JsonParser.parseString(metaJson);
            if (!parsed.isJsonObject()) {
                return errorResult("Invalid metadata format received");
            }
            JsonObject meta = parsed.getAsJsonObject();
            JsonObject result = new JsonObject();
            if (meta.has("id")) result.addProperty("id", meta.get("id").getAsString());
            if (meta.has("title")) result.addProperty("title", meta.get("title").getAsString());
            if (meta.has("thumbnail")) result.addProperty("thumbnail", meta.get("thumbnail").getAsString());
            if (meta.has("formats") && meta.get("formats").isJsonArray()) {
                JsonArray formatsIn = meta.getAsJsonArray("formats");
                JsonArray formatsOut = new JsonArray();
                for (JsonElement fmtEl : formatsIn) {
                    if (!fmtEl.isJsonObject()) continue;
                    JsonObject fmtIn = fmtEl.getAsJsonObject();
                    JsonObject fmtOut = new JsonObject();
                    if (fmtIn.has("format_id")) fmtOut.addProperty("formatId", fmtIn.get("format_id").getAsString());
                    if (fmtIn.has("ext")) fmtOut.addProperty("ext", fmtIn.get("ext").getAsString());
                    if (fmtIn.has("resolution")) fmtOut.addProperty("resolution", fmtIn.get("resolution").getAsString());
                    if (fmtIn.has("filesize") && !fmtIn.get("filesize").isJsonNull()) {
                        fmtOut.addProperty("fileSize", fmtIn.get("filesize").getAsLong());
                    }
                    if (fmtIn.has("vcodec")) fmtOut.addProperty("vcodec", fmtIn.get("vcodec").getAsString());
                    if (fmtIn.has("acodec")) fmtOut.addProperty("acodec", fmtIn.get("acodec").getAsString());
                    formatsOut.add(fmtOut);
                }
                result.add("formats", formatsOut);
            }
            return result;
        } catch (Exception e) {
            return errorResult("Failed to parse metadata: " + e.getMessage());
        }
    }

    private JsonObject errorResult(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("error", message);
        return result;
    }
}
