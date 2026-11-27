/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The first start after an upgrade imports the old SharedPreferences into the DataStore and runs
 * the one-time migrations. The repository initializes once per process, so everything is checked in
 * a single start; the SDK level gives this class a class loader no other test shares.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31, application = Application.class)
public class ConfigurationRepositoryMigrationTest {

  @Test
  public void anUpgradeImportsLegacyPreferencesAndRunsTheMigrations() {
    Context context = ApplicationProvider.getApplicationContext();
    SharedPreferences legacy = PreferenceManager.getDefaultSharedPreferences(context);
    legacy
        .edit()
        .putBoolean(Constants.PREF_KEY_GUI_VIBRATE_ON_FINISHED_DOWNLOAD, false)
        .putString(Constants.PREF_KEY_GUI_FINISHED_DOWNLOADS_BETWEEN_RATINGS_REMINDER, "7")
        .putString(Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS, "9")
        .putString(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY, "true")
        .putString(Constants.PREF_KEY_TORRENT_INCOMING_PORT_START, "not-a-number")
        .putString(Constants.PREF_KEY_GUI_THEME_MODE, "dark")
        .putString(Constants.PREF_KEY_ICEBRIDGE_RUDP_PORT, "6889")
        .putString(Constants.PREF_KEY_ICEBRIDGE_ROLE, "CLIENT")
        .putBoolean(Constants.PREF_KEY_SEARCH_USE_DISTRIBUTED, false)
        .putInt(Constants.PREF_KEY_SEARCH_MIN_SEEDS_FOR_TORRENT_RESULT, 99)
        .commit();

    ConfigurationRepository.initialize(context);
    ConfigurationRepository.awaitInitialization(1);

    assertFalse(
        ConfigurationRepository.getBoolean(Constants.PREF_KEY_GUI_VIBRATE_ON_FINISHED_DOWNLOAD));
    assertEquals(
        "an int saved as text is read back as an int",
        7,
        ConfigurationRepository.getInt(
            Constants.PREF_KEY_GUI_FINISHED_DOWNLOADS_BETWEEN_RATINGS_REMINDER));
    assertEquals(9L, ConfigurationRepository.getLong(Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS));
    assertTrue(
        "a boolean saved as text is read back as a boolean",
        ConfigurationRepository.getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY));
    assertEquals(
        "an unreadable number keeps its default",
        1024,
        ConfigurationRepository.getInt(Constants.PREF_KEY_TORRENT_INCOMING_PORT_START));
    assertEquals("dark", ConfigurationRepository.getString(Constants.PREF_KEY_GUI_THEME_MODE));

    assertEquals(
        "the legacy default rUDP port becomes automatic",
        "0",
        ConfigurationRepository.getString(Constants.PREF_KEY_ICEBRIDGE_RUDP_PORT));
    assertEquals(
        "a stored leaf-only role is upgraded once",
        "BOTH",
        ConfigurationRepository.getString(Constants.PREF_KEY_ICEBRIDGE_ROLE));
    assertFalse(
        "participation follows the old distributed-search switch",
        ConfigurationRepository.getBoolean(Constants.PREF_KEY_ICEBRIDGE_ENABLED));
    assertEquals(
        "volatile keys always start from their default",
        20,
        ConfigurationRepository.getInt(Constants.PREF_KEY_SEARCH_MIN_SEEDS_FOR_TORRENT_RESULT));

    assertTrue(legacy.contains("_migrated_to_datastore"));
    assertFalse(
        "imported keys leave the legacy store", legacy.contains(Constants.PREF_KEY_GUI_THEME_MODE));
  }
}
