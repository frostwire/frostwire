package com.frostwire.mcp.desktop.tools.settings;

import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.adapters.SettingsAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class SettingsSetTool implements MCPTool {

    @Override
    public String name() {
        return "frostwire_settings_set";
    }

    @Override
    public String description() {
        return "Set a FrostWire setting to a new value. Only whitelisted settings can be modified.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject keyProp = new JsonObject();
        keyProp.addProperty("type", "string");
        keyProp.addProperty("description", "The setting key name (e.g. VPN_DROP_PROTECTION, SEED_FINISHED_TORRENTS)");
        properties.add("key", keyProp);

        JsonObject valueProp = new JsonObject();
        valueProp.addProperty("description", "The new value for the setting (boolean, integer, or string)");
        properties.add("value", valueProp);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("key");
        required.add("value");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) {
        String key = arguments.get("key").getAsString();

        if (!SettingsAdapter.isKnownSetting(key)) {
            JsonObject error = new JsonObject();
            error.addProperty("error", "Unknown or restricted setting key: " + key);
            JsonArray allowed = new JsonArray();
            allowed.add("VPN_DROP_PROTECTION");
            allowed.add("ENABLE_DHT");
            allowed.add("I2P_ENABLED");
            allowed.add("I2P_HOSTNAME");
            allowed.add("I2P_PORT");
            allowed.add("I2P_ALLOW_MIXED");
            allowed.add("SEED_FINISHED_TORRENTS");
            allowed.add("ALLOW_PARTIAL_SHARING");
            allowed.add("TPB_SEARCH_ENABLED");
            allowed.add("SOUNDCLOUD_SEARCH_ENABLED");
            allowed.add("INTERNET_ARCHIVE_SEARCH_ENABLED");
            allowed.add("FROSTCLICK_SEARCH_ENABLED");
            allowed.add("NYAA_SEARCH_ENABLED");
            allowed.add("ONE337X_SEARCH_ENABLED");
            allowed.add("IDOPE_SEARCH_ENABLED");
            allowed.add("TORRENTZ2_SEARCH_ENABLED");
            allowed.add("MAGNETDL_ENABLED");
            allowed.add("YT_SEARCH_ENABLED");
            allowed.add("TORRENTSCSV_SEARCH_ENABLED");
            allowed.add("KNABEN_SEARCH_ENABLED");
            allowed.add("TELLURIDE_ENABLED");
            allowed.add("TORRENTDOWNLOADS_SEARCH_ENABLED");
            error.add("allowedKeys", allowed);
            return error;
        }

        Object previousValue = SettingsAdapter.getSettingValue(key);
        Object newValue = arguments.get("value");
        boolean success = SettingsAdapter.setSetting(key, newValue);

        JsonObject result = new JsonObject();
        result.addProperty("key", key);
        if (previousValue != null) {
            result.addProperty("previousValue", previousValue.toString());
        }
        if (success) {
            Object currentValue = SettingsAdapter.getSettingValue(key);
            if (currentValue != null) {
                result.addProperty("value", currentValue.toString());
            }
            result.addProperty("updated", true);
        } else {
            result.addProperty("updated", false);
            result.addProperty("error", "Failed to set value for key: " + key);
        }

        return result;
    }
}