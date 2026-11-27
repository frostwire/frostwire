/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class DataStoreManagerTest {

  private final Context context = ApplicationProvider.getApplicationContext();

  @Test
  public void namedStoresAreCreatedOnceAndReused() {
    DataStore<Preferences> first = DataStoreManager.dataStore(context, "dsm_test_reuse");
    assertSame(first, DataStoreManager.dataStore(context, "dsm_test_reuse"));
  }

  @Test
  public void valuesRoundTripAndMissingKeysGiveTheDefault() {
    DataStore<Preferences> store = DataStoreManager.dataStore(context, "dsm_test_values");
    assertEquals("d", DataStoreManager.getString(store, "s", "d"));
    assertEquals(3, DataStoreManager.getInt(store, "i", 3));
    assertEquals(4L, DataStoreManager.getLong(store, "l", 4L));
    assertFalse(DataStoreManager.getBoolean(store, "b", false));
    assertFalse(DataStoreManager.containsKey(store, "s"));

    DataStoreManager.putString(store, "s", "v");
    DataStoreManager.putInt(store, "i", 30);
    DataStoreManager.putLong(store, "l", 40L);
    DataStoreManager.putBoolean(store, "b", true);

    assertEquals("v", DataStoreManager.getString(store, "s", "d"));
    assertEquals(30, DataStoreManager.getInt(store, "i", 3));
    assertEquals(40L, DataStoreManager.getLong(store, "l", 4L));
    assertTrue(DataStoreManager.getBoolean(store, "b", false));
    assertTrue(DataStoreManager.containsKey(store, "s"));
    assertNull(DataStoreManager.getString(store, "other", null));
  }

  @Test
  public void initializeCreatesTheServiceAndNotifiedStores() {
    DataStoreManager.initialize(context);
    DataStoreManager.initialize(context);
    assertNotNull(DataStoreManager.getServiceDataStore());
    assertNotNull(DataStoreManager.getNotifiedDataStore());
    assertSame(
        DataStoreManager.getServiceDataStore(),
        DataStoreManager.dataStore(context, "service_prefs"));
  }
}
