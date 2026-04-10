package com.frostwire.mcp.desktop.tools.btengine;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.TorrentBuilder;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.swig.create_flags_t;
import com.frostwire.mcp.MCPTool;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.settings.SharingSettings;
import com.limegroup.gnutella.util.FrostWireUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public final class CreateTorrentTool implements MCPTool {

    @Override
    public String name() {
        return "frostwire_btengine_create_torrent";
    }

    @Override
    public String description() {
        return "Create a .torrent file from a local file or directory. Returns the torrent path, magnet URI, and info hash.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject pathProp = new JsonObject();
        pathProp.addProperty("type", "string");
        pathProp.addProperty("description", "Absolute path to the file or directory to create a torrent from");
        props.add("path", pathProp);
        JsonObject trackersProp = new JsonObject();
        trackersProp.addProperty("type", "array");
        trackersProp.addProperty("description", "List of tracker URLs");
        JsonObject trackerItems = new JsonObject();
        trackerItems.addProperty("type", "string");
        trackersProp.add("items", trackerItems);
        props.add("trackers", trackersProp);
        JsonObject webSeedsProp = new JsonObject();
        webSeedsProp.addProperty("type", "array");
        webSeedsProp.addProperty("description", "List of web seed URLs");
        JsonObject webSeedItems = new JsonObject();
        webSeedItems.addProperty("type", "string");
        webSeedsProp.add("items", webSeedItems);
        props.add("webSeeds", webSeedsProp);
        JsonObject torrentTypeProp = new JsonObject();
        torrentTypeProp.addProperty("type", "string");
        torrentTypeProp.addProperty("description", "Torrent type: v1, v2, or hybrid (default: hybrid)");
        torrentTypeProp.addProperty("default", "hybrid");
        props.add("torrentType", torrentTypeProp);
        JsonObject pieceSizeProp = new JsonObject();
        pieceSizeProp.addProperty("type", "integer");
        pieceSizeProp.addProperty("description", "Piece size in bytes (0 for auto-detect)");
        pieceSizeProp.addProperty("default", 0);
        props.add("pieceSize", pieceSizeProp);
        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("path");
        schema.add("required", required);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        if (arguments == null || !arguments.has("path")) {
            return errorResult("Missing required parameter: path");
        }
        String pathStr = arguments.get("path").getAsString();
        if (pathStr == null || pathStr.isEmpty()) {
            return errorResult("path parameter must not be empty");
        }
        File sourceFile = new File(pathStr);
        if (!sourceFile.exists()) {
            return errorResult("Path does not exist: " + pathStr);
        }
        int pieceSize = 0;
        if (arguments.has("pieceSize") && !arguments.get("pieceSize").isJsonNull()) {
            pieceSize = arguments.get("pieceSize").getAsInt();
        }
        String torrentType = "hybrid";
        if (arguments.has("torrentType") && !arguments.get("torrentType").isJsonNull()) {
            torrentType = arguments.get("torrentType").getAsString();
        }
        try {
            TorrentBuilder builder = new TorrentBuilder();
            builder.path(sourceFile);
            if (pieceSize > 0) {
                builder.pieceSize(pieceSize);
            }
            builder.creator("FrostWire " + FrostWireUtils.getFrostWireVersion());
            builder.setPrivate(false);
            create_flags_t flags = getFlags(torrentType);
            if (flags != null) {
                builder.flags(flags);
            }
            if (arguments.has("trackers") && arguments.get("trackers").isJsonArray()) {
                JsonArray trackers = arguments.getAsJsonArray("trackers");
                for (int i = 0; i < trackers.size(); i++) {
                    String tracker = trackers.get(i).getAsString();
                    if (tracker != null && !tracker.isEmpty()) {
                        builder.addTracker(tracker);
                    }
                }
            }
            if (arguments.has("webSeeds") && arguments.get("webSeeds").isJsonArray()) {
                JsonArray webSeeds = arguments.getAsJsonArray("webSeeds");
                for (int i = 0; i < webSeeds.size(); i++) {
                    String webSeed = webSeeds.get(i).getAsString();
                    if (webSeed != null && !webSeed.isEmpty()) {
                        builder.addUrlSeed(webSeed);
                    }
                }
            }
            TorrentBuilder.Result result = builder.generate();
            Entry entry = result.entry();
            byte[] bencoded = entry.bencode();
            File torrentsDir = SharingSettings.TORRENTS_DIR_SETTING.getValue();
            if (!torrentsDir.exists()) {
                torrentsDir.mkdirs();
            }
            File torrentFile = new File(torrentsDir, sourceFile.getName() + ".torrent");
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(torrentFile))) {
                bos.write(bencoded);
            }
            TorrentInfo ti = TorrentInfo.bdecode(bencoded);
            String magnetUri = ti.makeMagnetUri();
            String infoHash = getInfoHash(ti);
            JsonObject json = new JsonObject();
            json.addProperty("torrentPath", torrentFile.getAbsolutePath());
            json.addProperty("magnetUri", magnetUri);
            json.addProperty("infoHash", infoHash);
            return json;
        } catch (Exception e) {
            return errorResult("Failed to create torrent: " + e.getMessage());
        }
    }

    private create_flags_t getFlags(String torrentType) {
        if ("v1".equals(torrentType)) {
            return TorrentBuilder.V1_ONLY;
        } else if ("v2".equals(torrentType)) {
            return TorrentBuilder.V2_ONLY;
        }
        return null;
    }

    private String getInfoHash(TorrentInfo ti) {
        try {
            return ti.infoHashV1().toString();
        } catch (Exception e) {
            try {
                return ti.infoHashV2().toString();
            } catch (Exception e2) {
                return "";
            }
        }
    }

    private JsonObject errorResult(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("error", message);
        return result;
    }
}
