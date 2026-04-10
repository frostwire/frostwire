package com.frostwire.mcp.desktop.adapters;

import com.frostwire.bittorrent.BTEngine;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.settings.BittorrentSettings;
import com.limegroup.gnutella.settings.ConnectionSettings;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import com.limegroup.gnutella.settings.SharingSettings;
import org.limewire.setting.BooleanSetting;
import org.limewire.setting.IntSetting;
import org.limewire.setting.Setting;
import org.limewire.setting.StringSetting;

import java.util.LinkedHashMap;
import java.util.Map;

public class SettingsAdapter {

    private SettingsAdapter() {
    }

    public static JsonObject getCategorySettings(String category) {
        JsonObject settings = new JsonObject();
        switch (category) {
            case "connection":
                settings.addProperty("vpnDropProtection", ConnectionSettings.VPN_DROP_PROTECTION.getValue());
                settings.addProperty("dhtEnabled", SharingSettings.ENABLE_DISTRIBUTED_HASH_TABLE.getValue());
                settings.addProperty("i2pEnabled", ConnectionSettings.I2P_ENABLED.getValue());
                settings.addProperty("i2pHostname", ConnectionSettings.I2P_HOSTNAME.getValue());
                settings.addProperty("i2pPort", ConnectionSettings.I2P_PORT.getValue());
                settings.addProperty("i2pAllowMixed", ConnectionSettings.I2P_ALLOW_MIXED.getValue());
                settings.addProperty("portRangeStart", ConnectionSettings.PORT_RANGE_0.getValue());
                settings.addProperty("portRangeEnd", ConnectionSettings.PORT_RANGE_1.getValue());
                BTEngine engine = BTEngine.getInstance();
                if (engine != null && engine.swig() != null) {
                    settings.addProperty("maxActiveDownloads", engine.maxActiveDownloads());
                    settings.addProperty("maxActiveSeeds", engine.maxActiveSeeds());
                    settings.addProperty("maxConnections", engine.maxConnections());
                    settings.addProperty("maxPeers", engine.maxPeers());
                }
                break;
            case "library":
                settings.addProperty("torrentDataDir", SharingSettings.TORRENT_DATA_DIR_SETTING.getValue().getAbsolutePath());
                settings.addProperty("torrentsDir", SharingSettings.TORRENTS_DIR_SETTING.getValue().getAbsolutePath());
                break;
            case "sharing":
                settings.addProperty("seedFinishedTorrents", SharingSettings.SEED_FINISHED_TORRENTS.getValue());
                settings.addProperty("allowPartialSharing", SharingSettings.ALLOW_PARTIAL_SHARING.getValue());
                break;
            case "search":
                JsonObject engines = new JsonObject();
                engines.addProperty("tpb", SearchEnginesSettings.TPB_SEARCH_ENABLED.getValue());
                engines.addProperty("soundcloud", SearchEnginesSettings.SOUNDCLOUD_SEARCH_ENABLED.getValue());
                engines.addProperty("archiveOrg", SearchEnginesSettings.INTERNET_ARCHIVE_SEARCH_ENABLED.getValue());
                engines.addProperty("frostclick", SearchEnginesSettings.FROSTCLICK_SEARCH_ENABLED.getValue());
                engines.addProperty("nyaa", SearchEnginesSettings.NYAA_SEARCH_ENABLED.getValue());
                engines.addProperty("1337x", SearchEnginesSettings.ONE337X_SEARCH_ENABLED.getValue());
                engines.addProperty("idope", SearchEnginesSettings.IDOPE_SEARCH_ENABLED.getValue());
                engines.addProperty("torrentz2", SearchEnginesSettings.TORRENTZ2_SEARCH_ENABLED.getValue());
                engines.addProperty("magnetdl", SearchEnginesSettings.MAGNETDL_ENABLED.getValue());
                engines.addProperty("yt", SearchEnginesSettings.YT_SEARCH_ENABLED.getValue());
                engines.addProperty("torrentscsv", SearchEnginesSettings.TORRENTSCSV_SEARCH_ENABLED.getValue());
                engines.addProperty("knaben", SearchEnginesSettings.KNABEN_SEARCH_ENABLED.getValue());
                engines.addProperty("telluride", SearchEnginesSettings.TELLURIDE_ENABLED.getValue());
                engines.addProperty("torrentdownloads", SearchEnginesSettings.TORRENTDOWNLOADS_SEARCH_ENABLED.getValue());
                settings.add("engines", engines);
                break;
            case "vpn":
                settings.addProperty("dropProtectionEnabled", ConnectionSettings.VPN_DROP_PROTECTION.getValue());
                break;
            default:
                settings.addProperty("error", "Unknown category: " + category);
                break;
        }
        return settings;
    }

    public static boolean setSetting(String key, Object value) {
        Map<String, Setting> settingMap = getSettingMap();
        Setting setting = settingMap.get(key);
        if (setting == null) {
            return false;
        }
        try {
            if (setting instanceof BooleanSetting) {
                ((BooleanSetting) setting).setValue(Boolean.parseBoolean(value.toString()));
            } else if (setting instanceof IntSetting) {
                ((IntSetting) setting).setValue(Integer.parseInt(value.toString()));
            } else if (setting instanceof StringSetting) {
                ((StringSetting) setting).setValue(value.toString());
            } else {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static Object getSettingValue(String key) {
        Map<String, Setting> settingMap = getSettingMap();
        Setting setting = settingMap.get(key);
        if (setting == null) {
            return null;
        }
        if (setting instanceof BooleanSetting) {
            return ((BooleanSetting) setting).getValue();
        } else if (setting instanceof IntSetting) {
            return ((IntSetting) setting).getValue();
        } else if (setting instanceof StringSetting) {
            return ((StringSetting) setting).getValue();
        }
        return setting.getValueAsString();
    }

    public static boolean isKnownSetting(String key) {
        return getSettingMap().containsKey(key);
    }

    private static Map<String, Setting> getSettingMap() {
        Map<String, Setting> map = new LinkedHashMap<>();
        map.put("VPN_DROP_PROTECTION", ConnectionSettings.VPN_DROP_PROTECTION);
        map.put("ENABLE_DHT", SharingSettings.ENABLE_DISTRIBUTED_HASH_TABLE);
        map.put("I2P_ENABLED", ConnectionSettings.I2P_ENABLED);
        map.put("I2P_HOSTNAME", ConnectionSettings.I2P_HOSTNAME);
        map.put("I2P_PORT", ConnectionSettings.I2P_PORT);
        map.put("I2P_ALLOW_MIXED", ConnectionSettings.I2P_ALLOW_MIXED);
        map.put("SEED_FINISHED_TORRENTS", SharingSettings.SEED_FINISHED_TORRENTS);
        map.put("ALLOW_PARTIAL_SHARING", SharingSettings.ALLOW_PARTIAL_SHARING);
        map.put("TPB_SEARCH_ENABLED", SearchEnginesSettings.TPB_SEARCH_ENABLED);
        map.put("SOUNDCLOUD_SEARCH_ENABLED", SearchEnginesSettings.SOUNDCLOUD_SEARCH_ENABLED);
        map.put("INTERNET_ARCHIVE_SEARCH_ENABLED", SearchEnginesSettings.INTERNET_ARCHIVE_SEARCH_ENABLED);
        map.put("FROSTCLICK_SEARCH_ENABLED", SearchEnginesSettings.FROSTCLICK_SEARCH_ENABLED);
        map.put("NYAA_SEARCH_ENABLED", SearchEnginesSettings.NYAA_SEARCH_ENABLED);
        map.put("ONE337X_SEARCH_ENABLED", SearchEnginesSettings.ONE337X_SEARCH_ENABLED);
        map.put("IDOPE_SEARCH_ENABLED", SearchEnginesSettings.IDOPE_SEARCH_ENABLED);
        map.put("TORRENTZ2_SEARCH_ENABLED", SearchEnginesSettings.TORRENTZ2_SEARCH_ENABLED);
        map.put("MAGNETDL_ENABLED", SearchEnginesSettings.MAGNETDL_ENABLED);
        map.put("YT_SEARCH_ENABLED", SearchEnginesSettings.YT_SEARCH_ENABLED);
        map.put("TORRENTSCSV_SEARCH_ENABLED", SearchEnginesSettings.TORRENTSCSV_SEARCH_ENABLED);
        map.put("KNABEN_SEARCH_ENABLED", SearchEnginesSettings.KNABEN_SEARCH_ENABLED);
        map.put("TELLURIDE_ENABLED", SearchEnginesSettings.TELLURIDE_ENABLED);
        map.put("TORRENTDOWNLOADS_SEARCH_ENABLED", SearchEnginesSettings.TORRENTDOWNLOADS_SEARCH_ENABLED);
        return map;
    }
}
