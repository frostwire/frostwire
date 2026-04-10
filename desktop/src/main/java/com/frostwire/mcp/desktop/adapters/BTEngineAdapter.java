package com.frostwire.mcp.desktop.adapters;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.SessionStats;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.settings.ConnectionSettings;
import com.limegroup.gnutella.settings.SharingSettings;

public final class BTEngineAdapter {

    private BTEngineAdapter() {
    }

    public static JsonObject toStatusJson() {
        BTEngine engine = BTEngine.getInstance();
        JsonObject json = new JsonObject();
        json.addProperty("started", engine.isRunning());
        json.addProperty("paused", engine.isPausedCached());
        json.addProperty("totalDownload", engine.totalDownload());
        json.addProperty("totalUpload", engine.totalUpload());
        json.addProperty("dhtNodes", engine.dhtNodes());
        json.addProperty("dhtEnabled", SharingSettings.ENABLE_DISTRIBUTED_HASH_TABLE.getValue());
        json.addProperty("i2pEnabled", ConnectionSettings.I2P_ENABLED.getValue());
        json.addProperty("vpnDropProtection", ConnectionSettings.VPN_DROP_PROTECTION.getValue());
        SessionStats stats = engine.stats();
        if (stats != null) {
            json.addProperty("downloadRate", stats.downloadRate());
            json.addProperty("uploadRate", stats.uploadRate());
        } else {
            json.addProperty("downloadRate", 0L);
            json.addProperty("uploadRate", 0L);
        }
        return json;
    }
}
