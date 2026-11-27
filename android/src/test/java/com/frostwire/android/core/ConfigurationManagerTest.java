/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Runs the real ConfigurationManager over the real DataStore-backed repository. It uses its own SDK
 * level so Robolectric gives it a private class loader: the manager is a process singleton and
 * other tests replace it with mocks.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class ConfigurationManagerTest {

  private ConfigurationManager cm;

  @Before
  public void createOrReuseTheManager() {
    Context context = ApplicationProvider.getApplicationContext();
    try {
      ConfigurationManager.create(context);
    } catch (RuntimeException alreadyCreatedByAnEarlierTest) {
      // create() is once per JVM class loader; the instance is shared by this class's tests.
    }
    cm = ConfigurationManager.instance();
  }

  @Test
  public void instanceIsAlwaysTheSameObject() {
    assertSame(cm, ConfigurationManager.instance());
  }

  @Test
  public void creatingTwiceIsRejected() {
    assertThrows(
        RuntimeException.class,
        () -> ConfigurationManager.create(ApplicationProvider.getApplicationContext()));
  }

  @Test
  public void defaultsAreSeeded() {
    assertEquals(50, cm.getInt(Constants.PREF_KEY_GUI_SUPPORT_VPN_THRESHOLD, -1));
    assertEquals(4L, cm.getLong(Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS, -1));
    assertEquals("BOTH", cm.getString(Constants.PREF_KEY_ICEBRIDGE_ROLE));
    assertNotNull(cm.getUUIDString());
    assertEquals(32, cm.getUUIDString().length());
  }

  @Test
  public void stringsRoundTripAndFallBackToTheGivenDefault() {
    assertEquals("fallback", cm.getString("test.string.missing", "fallback"));
    assertNull(cm.getString("test.string.missing"));
    cm.setString("test.string.key", "value");
    assertEquals("value", cm.getString("test.string.key", "other"));
    assertEquals("value", cm.getString("test.string.key"));
  }

  @Test
  public void intsLongsAndBooleansRoundTrip() {
    assertEquals(7, cm.getInt("test.int.missing", 7));
    assertEquals(0, cm.getInt("test.int.missing"));
    cm.setInt("test.int.key", 42);
    assertEquals(42, cm.getInt("test.int.key"));

    assertEquals(9L, cm.getLong("test.long.missing", 9L));
    assertEquals(0L, cm.getLong("test.long.missing"));
    cm.setLong("test.long.key", 1L << 40);
    assertEquals(1L << 40, cm.getLong("test.long.key"));

    assertFalse(cm.getBoolean("test.bool.missing"));
    cm.setBoolean("test.bool.key", true);
    assertTrue(cm.getBoolean("test.bool.key"));
    cm.setBoolean("test.bool.key", false);
    assertFalse(cm.getBoolean("test.bool.key"));
  }

  @Test
  public void numbersStoredAsStringsAreParsedAndGarbageFallsBack() {
    cm.setString("test.numeric.string", "123");
    assertEquals(123, cm.getInt("test.numeric.string", -1));
    assertEquals(123L, cm.getLong("test.numeric.string", -1));
    cm.setString("test.garbage.string", "abc");
    assertEquals(-1, cm.getInt("test.garbage.string", -1));
    assertEquals(-1L, cm.getLong("test.garbage.string", -1));
  }

  @Test
  public void intAndLongAreInterchangeable() {
    cm.setInt("test.int.as.long", 5);
    assertEquals(5L, cm.getLong("test.int.as.long"));
    cm.setLong("test.long.as.int", 6L);
    assertEquals(6, cm.getInt("test.long.as.int"));
  }

  @Test
  public void stringArraysRoundTripThroughJson() {
    String[] values = {"a", "b c", "d\"e"};
    cm.setStringArray("test.array.key", values);
    assertArrayEquals(values, cm.getStringArray("test.array.key"));
    assertNull(cm.getStringArray("test.array.missing"));
  }

  @Test
  public void malformedStringArrayReadsAsNull() {
    cm.setString("test.array.bad", "not json at all");
    assertNull(cm.getStringArray("test.array.bad"));
  }

  @Test
  public void listenersHearAboutEveryChangeUntilRemoved() {
    List<String> heard = new ArrayList<>();
    ConfigurationRepository.OnPreferenceChangeListener listener = heard::add;
    cm.registerOnPreferenceChange(listener);
    cm.setString("test.listener.a", "x");
    cm.setInt("test.listener.b", 1);
    cm.setBoolean("test.listener.c", true);
    cm.unregisterOnPreferenceChange(listener);
    cm.setString("test.listener.d", "ignored");
    assertEquals(List.of("test.listener.a", "test.listener.b", "test.listener.c"), heard);
  }

  @Test
  public void aThrowingListenerDoesNotStopTheOthers() {
    List<String> heard = new ArrayList<>();
    ConfigurationRepository.OnPreferenceChangeListener bad =
        key -> {
          throw new IllegalStateException("boom");
        };
    ConfigurationRepository.OnPreferenceChangeListener good = heard::add;
    cm.registerOnPreferenceChange(bad);
    cm.registerOnPreferenceChange(good);
    try {
      cm.setString("test.listener.robust", "x");
    } finally {
      cm.unregisterOnPreferenceChange(bad);
      cm.unregisterOnPreferenceChange(good);
    }
    assertEquals(List.of("test.listener.robust"), heard);
  }

  @Test
  public void namedAccessorsUseTheirPreferenceKeys() {
    cm.setLastMediaTypeFilter(3);
    assertEquals(3, cm.getInt(Constants.PREF_KEY_GUI_LAST_MEDIA_TYPE_FILTER));
    assertEquals(3, cm.getLastMediaTypeFilter());

    cm.setBoolean(Constants.PREF_KEY_GUI_VIBRATE_ON_FINISHED_DOWNLOAD, false);
    assertFalse(cm.vibrateOnFinishedDownload());
    cm.setBoolean(Constants.PREF_KEY_GUI_SHOW_TRANSFERS_ON_DOWNLOAD_START, false);
    assertFalse(cm.showTransfersOnDownloadStart());

    cm.setSeedFinishedTorrents(true);
    assertTrue(cm.isSeedFinishedTorrents());
    cm.setBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY, false);
    assertFalse(cm.isSeedingEnabledOnlyForWifi());
  }

  @Test
  public void storagePathIgnoresNullAndEmptyValues() {
    cm.setStoragePath("/storage/test-path");
    assertEquals("/storage/test-path", cm.getStoragePath());
    cm.setStoragePath(null);
    cm.setStoragePath("");
    assertEquals("/storage/test-path", cm.getStoragePath());
  }

  @Test
  public void resetToDefaultsRestoresDefaultValues() {
    cm.setInt(Constants.PREF_KEY_GUI_SUPPORT_VPN_THRESHOLD, 99);
    cm.setBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED, true);
    cm.resetToDefaults();
    assertEquals(50, cm.getInt(Constants.PREF_KEY_GUI_SUPPORT_VPN_THRESHOLD));
    assertFalse(cm.getBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED));
  }

  @Test
  public void repositoryKnowsItsDefaultKeys() {
    assertTrue(ConfigurationRepository.containsKey(Constants.PREF_KEY_CORE_UUID));
    assertFalse(ConfigurationRepository.containsKey("test.never.defined"));
    assertTrue(
        ConfigurationRepository.getDefaultValues()
            .containsKey(Constants.PREF_KEY_GUI_SUPPORT_VPN_THRESHOLD));
    cm.setString("test.contains.key", "v");
    assertTrue(ConfigurationRepository.containsKey("test.contains.key"));
  }

  @Test
  public void initializeIsIdempotentAndAwaitReturnsImmediately() {
    ConfigurationRepository.initialize(ApplicationProvider.getApplicationContext());
    ConfigurationRepository.awaitInitialization(1);
    assertEquals(50, cm.getInt(Constants.PREF_KEY_GUI_SUPPORT_VPN_THRESHOLD, -1));
  }

  @Test
  public void preferenceDataStoreReadsAndWritesThroughTheRepository() {
    FrostwirePreferenceDataStore store = new FrostwirePreferenceDataStore();
    store.putString("test.pds.string", "s");
    assertEquals("s", store.getString("test.pds.string", null));
    store.putString("test.pds.string", null);
    assertEquals("s", store.getString("test.pds.string", null));
    assertEquals("d", store.getString("test.pds.string.missing", "d"));

    store.putInt("test.pds.int", 3);
    assertEquals(3, store.getInt("test.pds.int", 0));
    store.putLong("test.pds.long", 4L);
    assertEquals(4L, store.getLong("test.pds.long", 0L));
    store.putBoolean("test.pds.bool", true);
    assertTrue(store.getBoolean("test.pds.bool", false));

    java.util.Set<String> set = new java.util.HashSet<>(List.of("x", "y"));
    store.putStringSet("test.pds.set", set);
    assertEquals(set, store.getStringSet("test.pds.set", null));
    store.putStringSet("test.pds.set", null);
    assertEquals(set, store.getStringSet("test.pds.set", null));
    java.util.Set<String> fallback = new java.util.HashSet<>(List.of("z"));
    assertEquals(fallback, store.getStringSet("test.pds.set.missing", fallback));
  }
}
