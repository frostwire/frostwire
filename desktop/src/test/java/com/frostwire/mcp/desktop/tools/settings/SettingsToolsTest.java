/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.BuildConfig;
import com.frostwire.mcp.desktop.EngineContext;
import com.frostwire.mcp.desktop.adapters.SettingsAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.settings.ConnectionSettings;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import com.limegroup.gnutella.settings.SharingSettings;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reading settings by category and changing whitelisted settings through MCP. Every setting a test
 * changes is put back afterwards.
 */
class SettingsToolsTest {

  @TempDir Path engineHome;

  @Test
  void connectionCategoryReportsI2pAndPortSettings() {
    // Reading the connection category asks the engine for its limits; with no session it has none.
    EngineContext engine = EngineContext.install(engineHome.toFile());
    try {
      JsonObject connection = get("connection");

      assertEquals(
          ConnectionSettings.VPN_DROP_PROTECTION.getValue(),
          connection.get("vpnDropProtection").getAsBoolean());
      assertEquals(
          SharingSettings.ENABLE_DISTRIBUTED_HASH_TABLE.getValue(),
          connection.get("dhtEnabled").getAsBoolean());
      assertEquals(
          ConnectionSettings.I2P_ENABLED.getValue(), connection.get("i2pEnabled").getAsBoolean());
      assertEquals(
          ConnectionSettings.I2P_HOSTNAME.getValue(), connection.get("i2pHostname").getAsString());
      assertEquals(ConnectionSettings.I2P_PORT.getValue(), connection.get("i2pPort").getAsInt());
      assertEquals(
          ConnectionSettings.I2P_ALLOW_MIXED.getValue(),
          connection.get("i2pAllowMixed").getAsBoolean());
      assertEquals(
          ConnectionSettings.PORT_RANGE_0.getValue(), connection.get("portRangeStart").getAsInt());
      assertEquals(
          ConnectionSettings.PORT_RANGE_1.getValue(), connection.get("portRangeEnd").getAsInt());
      assertFalse(connection.has("maxActiveDownloads"), "no session, no engine limits");
    } finally {
      engine.close();
    }
  }

  @Test
  void libraryAndSharingCategoriesReportFoldersAndSeeding() {
    JsonObject library = get("library");
    assertEquals(
        SharingSettings.TORRENT_DATA_DIR_SETTING.getValue().getAbsolutePath(),
        library.get("torrentDataDir").getAsString());
    assertEquals(
        SharingSettings.TORRENTS_DIR_SETTING.getValue().getAbsolutePath(),
        library.get("torrentsDir").getAsString());

    JsonObject sharing = get("sharing");
    assertEquals(
        SharingSettings.SEED_FINISHED_TORRENTS.getValue(),
        sharing.get("seedFinishedTorrents").getAsBoolean());
    assertEquals(
        SharingSettings.ALLOW_PARTIAL_SHARING.getValue(),
        sharing.get("allowPartialSharing").getAsBoolean());

    assertEquals(
        ConnectionSettings.VPN_DROP_PROTECTION.getValue(),
        get("vpn").get("dropProtectionEnabled").getAsBoolean());
  }

  @Test
  void searchCategoryReportsEveryEngineSwitch() {
    JsonObject engines = get("search").getAsJsonObject("engines");

    assertEquals(
        SearchEnginesSettings.TPB_SEARCH_ENABLED.getValue(), engines.get("tpb").getAsBoolean());
    assertEquals(
        SearchEnginesSettings.YT_SEARCH_ENABLED.getValue(), engines.get("yt").getAsBoolean());
    assertEquals(
        SearchEnginesSettings.TELLURIDE_ENABLED.getValue(),
        engines.get("telluride").getAsBoolean());
    for (String key :
        List.of(
            "soundcloud",
            "archiveOrg",
            "frostclick",
            "nyaa",
            "1337x",
            "torrentz2",
            "magnetdl",
            "torrentscsv",
            "knaben",
            "bitsearch",
            "torrentdownloads")) {
      assertTrue(engines.has(key), key);
    }
    assertEquals(BuildConfig.DEBUG, engines.has("local"), "the local engine is a debug build tool");
  }

  @Test
  void unknownCategoryListsTheValidOnes() {
    JsonObject out = get("passwords");

    assertEquals("Unknown category: passwords", out.get("error").getAsString());
    JsonArray valid = out.getAsJsonArray("validCategories");
    assertEquals(6, valid.size());
    for (var category : valid) {
      // Every category the error offers can be read without an error. Connection needs the engine.
      if (!category.getAsString().equals("connection")) {
        assertFalse(get(category.getAsString()).has("error"), category.getAsString());
      }
    }
  }

  @Test
  void setChangesBooleanAndIntSettingsAndReportsThePreviousValue() {
    boolean mixed = ConnectionSettings.I2P_ALLOW_MIXED.getValue();
    int port = ConnectionSettings.I2P_PORT.getValue();
    try {
      JsonObject bool = set("I2P_ALLOW_MIXED", !mixed);
      assertEquals("I2P_ALLOW_MIXED", bool.get("key").getAsString());
      assertTrue(bool.get("updated").getAsBoolean());
      assertEquals(String.valueOf(mixed), bool.get("previousValue").getAsString());
      assertEquals(String.valueOf(!mixed), bool.get("value").getAsString());
      assertEquals(!mixed, ConnectionSettings.I2P_ALLOW_MIXED.getValue());

      JsonObject number = set("I2P_PORT", port + 1);
      assertTrue(number.get("updated").getAsBoolean());
      assertEquals(port + 1, ConnectionSettings.I2P_PORT.getValue());
      assertEquals(String.valueOf(port), number.get("previousValue").getAsString());
    } finally {
      ConnectionSettings.I2P_ALLOW_MIXED.setValue(mixed);
      ConnectionSettings.I2P_PORT.setValue(port);
    }
  }

  @Test
  void setReportsAValueThatDoesNotFitTheSetting() {
    int port = ConnectionSettings.I2P_PORT.getValue();
    JsonObject args = new JsonObject();
    args.addProperty("key", "I2P_PORT");
    args.addProperty("value", "not a port");

    JsonObject out = new SettingsSetTool().execute(args);

    assertFalse(out.get("updated").getAsBoolean());
    assertEquals("Failed to set value for key: I2P_PORT", out.get("error").getAsString());
    assertFalse(out.has("value"));
    assertEquals(port, ConnectionSettings.I2P_PORT.getValue(), "the setting is unchanged");
  }

  @Test
  void setRejectsUnknownKeysAndListsTheKeysItAccepts() {
    JsonObject args = new JsonObject();
    args.addProperty("key", "MAX_UPLOADS");
    args.addProperty("value", "1");

    JsonObject out = new SettingsSetTool().execute(args);

    assertEquals("Unknown or restricted setting key: MAX_UPLOADS", out.get("error").getAsString());
    JsonArray allowed = out.getAsJsonArray("allowedKeys");
    assertTrue(allowed.size() > 20);
    for (var key : allowed) {
      assertTrue(
          SettingsAdapter.isKnownSetting(key.getAsString()),
          "every advertised key can be set: " + key);
      assertTrue(SettingsAdapter.getSettingValue(key.getAsString()) != null, "and read: " + key);
    }
  }

  @Test
  void adapterRefusesUnknownSettings() {
    assertFalse(SettingsAdapter.setSetting("NOPE", "1"));
    assertNull(SettingsAdapter.getSettingValue("NOPE"));
    assertFalse(SettingsAdapter.isKnownSetting("NOPE"));
  }

  @Test
  void toolsDeclareTheirRequiredArguments() {
    JsonObject getSchema = new SettingsGetTool().inputSchema();
    assertEquals("category", getSchema.getAsJsonArray("required").get(0).getAsString());
    JsonObject setSchema = new SettingsSetTool().inputSchema();
    JsonArray required = setSchema.getAsJsonArray("required");
    assertEquals(2, required.size());
    for (var name : required) {
      assertTrue(setSchema.getAsJsonObject("properties").has(name.getAsString()));
    }
  }

  private static JsonObject get(String category) {
    JsonObject args = new JsonObject();
    args.addProperty("category", category);
    return new SettingsGetTool().execute(args);
  }

  private static JsonObject set(String key, Object value) {
    JsonObject args = new JsonObject();
    args.addProperty("key", key);
    if (value instanceof Number number) {
      args.addProperty("value", number);
    } else if (value instanceof Boolean bool) {
      args.addProperty("value", bool);
    } else {
      args.addProperty("value", String.valueOf(value));
    }
    return new SettingsSetTool().execute(args);
  }
}
