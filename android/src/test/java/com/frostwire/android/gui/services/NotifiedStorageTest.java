/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import android.app.Application;
import androidx.datastore.core.DataStore;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.core.DataStoreManager;
import com.frostwire.android.gui.MainApplication;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.jlibtorrent.swig.bloom_filter_256;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The notified-hashes bloom filter lives in libtorrent's native library. Without it (a unit test,
 * or a device where the engine failed to start) the storage must disable itself instead of crashing
 * the service that owns it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class NotifiedStorageTest {
  private static final String HASH = "0123456789abcdef0123456789abcdef01234567";
  private static final String OTHER_HASH = "fedcba9876543210fedcba9876543210fedcba98";
  private static final String PREF_KEY = "frostwire.prefs.gui.notified_hashes";

  @Test
  public void aBrokenEngineSkipsTheNativeSetupAndNothingIsEverRemembered() {
    try (MockedStatic<SystemUtils> system = mockStatic(SystemUtils.class);
        MockedStatic<DataStoreManager> stores = mockStatic(DataStoreManager.class);
        MockedStatic<MainApplication> main = mockStatic(MainApplication.class)) {
      stores.when(DataStoreManager::getNotifiedDataStore).thenReturn(mock(DataStore.class));
      main.when(MainApplication::hasBTEngineInitializationFailure).thenReturn(true);
      NotifiedStorage storage = new NotifiedStorage(ApplicationProvider.getApplicationContext());
      storage.add(HASH);
      assertFalse(storage.contains(HASH));
      main.verify(() -> MainApplication.recordBTEngineInitializationFailure(any()), never());
      stores.verify(() -> DataStoreManager.putString(any(), any(), any()), never());
      system.verify(() -> SystemUtils.ensureBackgroundThreadOrCrash(any()), times(1));
    }
  }

  @Test
  @SuppressWarnings("try")
  public void aMissingNativeLibraryIsRecordedAndDisablesTheStorage() {
    try (MockedStatic<SystemUtils> system = mockStatic(SystemUtils.class);
        MockedStatic<DataStoreManager> stores = mockStatic(DataStoreManager.class);
        MockedStatic<MainApplication> main = mockStatic(MainApplication.class);
        // libtorrent loads in unit tests; a device where it does not fails when the filter is
        // created
        MockedConstruction<bloom_filter_256> filters =
            mockConstruction(
                bloom_filter_256.class,
                (filter, context) -> {
                  throw new UnsatisfiedLinkError("no jlibtorrent");
                })) {
      stores.when(DataStoreManager::getNotifiedDataStore).thenReturn(mock(DataStore.class));
      main.when(MainApplication::hasBTEngineInitializationFailure).thenReturn(false);
      NotifiedStorage storage = new NotifiedStorage(ApplicationProvider.getApplicationContext());
      main.verify(() -> MainApplication.recordBTEngineInitializationFailure(any()));
      storage.add(HASH);
      storage.add(null);
      storage.add("too short");
      assertFalse(storage.contains(HASH));
      assertFalse(storage.contains(null));
      assertFalse(storage.contains("too short"));
      stores.verify(() -> DataStoreManager.putString(any(), any(), any()), never());
      system.verify(() -> SystemUtils.ensureBackgroundThreadOrCrash(any()), times(1));
    }
  }

  /** A storage over the real libtorrent bloom filter whose preference holds {@code saved}. */
  private static NotifiedStorage storage(MockedStatic<DataStoreManager> stores, String saved) {
    stores.when(DataStoreManager::getNotifiedDataStore).thenReturn(mock(DataStore.class));
    stores.when(() -> DataStoreManager.getString(any(), eq(PREF_KEY), eq(null))).thenReturn(saved);
    return new NotifiedStorage(ApplicationProvider.getApplicationContext());
  }

  @Test
  public void aNotifiedHashIsRememberedAndSavedAsHex() {
    try (MockedStatic<SystemUtils> system = mockStatic(SystemUtils.class);
        MockedStatic<DataStoreManager> stores = mockStatic(DataStoreManager.class);
        MockedStatic<MainApplication> main = mockStatic(MainApplication.class)) {
      main.when(MainApplication::hasBTEngineInitializationFailure).thenReturn(false);
      NotifiedStorage storage = storage(stores, null);
      assertFalse(storage.contains(HASH));
      storage.add(HASH);
      assertTrue(storage.contains(HASH));
      assertFalse(storage.contains(OTHER_HASH));
      ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
      stores.verify(() -> DataStoreManager.putString(any(), eq(PREF_KEY), saved.capture()));
      // a 256 byte bloom filter, hex encoded
      assertEquals(512, saved.getValue().length());
      main.verify(() -> MainApplication.recordBTEngineInitializationFailure(any()), never());
      system.verify(() -> SystemUtils.ensureBackgroundThreadOrCrash(any()), atLeastOnce());
    }
  }

  @Test
  public void theSavedFilterIsLoadedByTheNextStorage() {
    String saved;
    try (MockedStatic<SystemUtils> system = mockStatic(SystemUtils.class);
        MockedStatic<DataStoreManager> stores = mockStatic(DataStoreManager.class);
        MockedStatic<MainApplication> main = mockStatic(MainApplication.class)) {
      main.when(MainApplication::hasBTEngineInitializationFailure).thenReturn(false);
      NotifiedStorage first = storage(stores, null);
      first.add(HASH);
      ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
      stores.verify(() -> DataStoreManager.putString(any(), eq(PREF_KEY), captor.capture()));
      saved = captor.getValue();
      system.verify(() -> SystemUtils.ensureBackgroundThreadOrCrash(any()), atLeastOnce());
    }
    try (MockedStatic<SystemUtils> system = mockStatic(SystemUtils.class);
        MockedStatic<DataStoreManager> stores = mockStatic(DataStoreManager.class);
        MockedStatic<MainApplication> main = mockStatic(MainApplication.class)) {
      main.when(MainApplication::hasBTEngineInitializationFailure).thenReturn(false);
      NotifiedStorage second = storage(stores, saved);
      assertTrue(second.contains(HASH));
      assertFalse(second.contains(OTHER_HASH));
      system.verify(() -> SystemUtils.ensureBackgroundThreadOrCrash(any()), atLeastOnce());
    }
  }

  @Test
  public void unreadableSavedDataStartsAnEmptyFilterAndKeepsWorking() {
    try (MockedStatic<SystemUtils> system = mockStatic(SystemUtils.class);
        MockedStatic<DataStoreManager> stores = mockStatic(DataStoreManager.class);
        MockedStatic<MainApplication> main = mockStatic(MainApplication.class)) {
      main.when(MainApplication::hasBTEngineInitializationFailure).thenReturn(false);
      NotifiedStorage storage = storage(stores, "this is not hex");
      assertFalse(storage.contains(HASH));
      storage.add(HASH);
      assertTrue(storage.contains(HASH));
      main.verify(() -> MainApplication.recordBTEngineInitializationFailure(any()), never());
      system.verify(() -> SystemUtils.ensureBackgroundThreadOrCrash(any()), atLeastOnce());
    }
  }

  @Test
  public void hashesThatAreNotFortyHexCharactersAreIgnored() {
    try (MockedStatic<SystemUtils> system = mockStatic(SystemUtils.class);
        MockedStatic<DataStoreManager> stores = mockStatic(DataStoreManager.class);
        MockedStatic<MainApplication> main = mockStatic(MainApplication.class)) {
      main.when(MainApplication::hasBTEngineInitializationFailure).thenReturn(false);
      NotifiedStorage storage = storage(stores, null);
      String notHex = "zz".repeat(20);
      storage.add(null);
      storage.add("too short");
      storage.add(notHex);
      assertFalse(storage.contains(null));
      assertFalse(storage.contains("too short"));
      assertFalse(storage.contains(notHex));
      stores.verify(() -> DataStoreManager.putString(any(), any(), any()), never());
      system.verify(() -> SystemUtils.ensureBackgroundThreadOrCrash(any()), atLeastOnce());
    }
  }

  @Test
  public void aFailureSavingTheFilterDoesNotLoseTheHashInMemory() {
    try (MockedStatic<SystemUtils> system = mockStatic(SystemUtils.class);
        MockedStatic<DataStoreManager> stores = mockStatic(DataStoreManager.class);
        MockedStatic<MainApplication> main = mockStatic(MainApplication.class)) {
      main.when(MainApplication::hasBTEngineInitializationFailure).thenReturn(false);
      NotifiedStorage storage = storage(stores, null);
      stores
          .when(() -> DataStoreManager.putString(any(), any(), any()))
          .thenThrow(new IllegalStateException("disk full"));
      storage.add(HASH);
      assertTrue(storage.contains(HASH));
      system.verify(() -> SystemUtils.ensureBackgroundThreadOrCrash(any()), atLeastOnce());
    }
  }
}
