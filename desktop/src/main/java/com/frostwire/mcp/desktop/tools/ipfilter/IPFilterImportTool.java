package com.frostwire.mcp.desktop.tools.ipfilter;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.swig.address;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.ip_filter;
import com.frostwire.mcp.MCPTool;
import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.util.Logger;
import com.frostwire.util.http.JdkHttpClient;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.options.panes.IPFilterTableMediator;
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPRange;
import com.limegroup.gnutella.gui.tables.DataLineModel;
import org.limewire.util.CommonUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

public class IPFilterImportTool implements MCPTool {

    private static final Logger LOG = Logger.getLogger(IPFilterImportTool.class);
    private static final Pattern P2P_LINE_PATTERN = Pattern.compile("(.*)\\:(.*)\\-(.*)$", java.util.regex.Pattern.COMMENTS);

    @Override
    public String name() {
        return "frostwire_ipfilter_import";
    }

    @Override
    public String description() {
        return "Import IP filter ranges from a URL or local file path (P2P/PeerGuardian format supported).";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject urlProp = new JsonObject();
        urlProp.addProperty("type", "string");
        urlProp.addProperty("description", "URL to download the IP filter blocklist from");
        properties.add("url", urlProp);

        JsonObject pathProp = new JsonObject();
        pathProp.addProperty("type", "string");
        pathProp.addProperty("description", "Local file path of the IP filter blocklist");
        properties.add("path", pathProp);

        schema.add("properties", properties);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        JsonObject result = new JsonObject();

        String url = null;
        String path = null;
        if (arguments.has("url") && arguments.get("url").isJsonPrimitive()) {
            url = arguments.get("url").getAsString();
        }
        if (arguments.has("path") && arguments.get("path").isJsonPrimitive()) {
            path = arguments.get("path").getAsString();
        }

        if (url == null && path == null) {
            result.addProperty("error", "Either 'url' or 'path' must be provided");
            result.addProperty("imported", 0);
            return result;
        }

        File sourceFile;
        boolean shouldCleanup = false;

        if (url != null) {
            try {
                sourceFile = downloadBlocklist(url);
                shouldCleanup = true;
            } catch (Exception e) {
                LOG.error("IPFilterImportTool: Failed to download blocklist: " + e.getMessage(), e);
                result.addProperty("error", "Failed to download blocklist: " + e.getMessage());
                result.addProperty("imported", 0);
                return result;
            }
        } else {
            sourceFile = new File(path);
            if (!sourceFile.exists() || !sourceFile.canRead()) {
                result.addProperty("error", "File not found or not readable: " + path);
                result.addProperty("imported", 0);
                return result;
            }
        }

        try {
            File decompressedFile = decompressIfNeeded(sourceFile);
            boolean shouldCleanupDecompressed = (decompressedFile != sourceFile);

            int imported = importP2PFile(decompressedFile);

            if (shouldCleanupDecompressed && decompressedFile.exists()) {
                decompressedFile.delete();
            }
            if (shouldCleanup && sourceFile.exists()) {
                sourceFile.delete();
            }

            result.addProperty("imported", imported);
        } catch (Exception e) {
            LOG.error("IPFilterImportTool: Import failed: " + e.getMessage(), e);
            result.addProperty("error", "Import failed: " + e.getMessage());
            result.addProperty("imported", 0);
            if (shouldCleanup && sourceFile.exists()) {
                sourceFile.delete();
            }
        }

        return result;
    }

    private File downloadBlocklist(String url) throws IOException {
        File tempFile = new File(CommonUtils.getUserSettingsDir(), "mcp_blocklist_download.temp");
        JdkHttpClient http = new JdkHttpClient();
        http.save(url, tempFile, false, 60000, null, null);
        return tempFile;
    }

    private File decompressIfNeeded(File file) throws IOException {
        if (isGZipped(file)) {
            File output = new File(CommonUtils.getUserSettingsDir(), "mcp_blocklist_decompressed.temp");
            if (output.exists()) {
                output.delete();
            }
            try (GZIPInputStream gis = new GZIPInputStream(new FileInputStream(file));
                 FileOutputStream fos = new FileOutputStream(output)) {
                byte[] buffer = new byte[32768];
                int len;
                while ((len = gis.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
            }
            return output;
        }
        return file;
    }

    private boolean isGZipped(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] signature = new byte[2];
            int read = fis.read(signature);
            if (read < 2) return false;
            return signature[0] == (byte) 0x1f && signature[1] == (byte) 0x8b;
        }
    }

    private int importP2PFile(File file) {
        IPFilterTableMediator tableMediator = IPFilterTableMediator.getInstance();
        DataLineModel<IPFilterTableMediator.IPFilterDataLine, IPRange> dataModel = tableMediator.getDataModel();
        BTEngine engine = BTEngine.getInstance();
        ip_filter currentFilter = null;
        if (engine != null && engine.swig() != null) {
            try {
                currentFilter = engine.swig().get_ip_filter();
            } catch (Exception e) {
                LOG.warn("IPFilterImportTool: Could not get ip_filter from BTEngine: " + e.getMessage());
            }
        }

        int imported = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                Matcher matcher = P2P_LINE_PATTERN.matcher(line);
                if (matcher.find()) {
                    String description = matcher.group(1).trim();
                    String startAddr = matcher.group(2).trim();
                    String endAddr = matcher.group(3).trim();
                    try {
                        IPRange ipRange = new IPRange(description, startAddr, endAddr);
                        dataModel.add(ipRange, dataModel.getRowCount());
                        if (currentFilter != null) {
                            error_code ec = new error_code();
                            address addrStart = address.from_string(startAddr, ec);
                            if (!ec.failed()) {
                                address addrEnd = address.from_string(endAddr, ec);
                                if (!ec.failed()) {
                                    currentFilter.add_rule(addrStart, addrEnd, 0);
                                }
                            }
                        }
                        imported++;
                    } catch (Exception e) {
                        LOG.warn("IPFilterImportTool: Skipping invalid range: " + line);
                    }
                }
            }
        } catch (IOException e) {
            LOG.error("IPFilterImportTool: Error reading blocklist file: " + e.getMessage(), e);
        }

        if (engine != null && currentFilter != null) {
            try {
                engine.swig().set_ip_filter(currentFilter);
            } catch (Exception e) {
                LOG.error("IPFilterImportTool: Error setting ip_filter on BTEngine: " + e.getMessage(), e);
            }
        }

        tableMediator.refresh();
        persistFilter(dataModel);

        return imported;
    }

    private void persistFilter(DataLineModel<IPFilterTableMediator.IPFilterDataLine, IPRange> dataModel) {
        File ipFilterDBFile = new File(CommonUtils.getUserSettingsDir(), "ip_filter.db");
        try (FileOutputStream fos = new FileOutputStream(ipFilterDBFile)) {
            int count = dataModel.getRowCount();
            for (int i = 0; i < count; i++) {
                try {
                    IPFilterTableMediator.IPFilterDataLine dataLine = dataModel.get(i);
                    if (dataLine != null) {
                        IPRange ipRange = dataLine.getInitializeObject();
                        if (ipRange != null) {
                            ipRange.writeObjectTo(fos);
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("IPFilterImportTool: Error persisting range at index " + i + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            LOG.error("IPFilterImportTool: Error writing ip_filter.db: " + e.getMessage(), e);
        }
    }
}