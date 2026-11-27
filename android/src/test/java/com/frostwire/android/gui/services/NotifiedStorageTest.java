/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.services;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import android.app.Application;
import androidx.datastore.core.DataStore;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.core.DataStoreManager;
import com.frostwire.android.gui.MainApplication;
import com.frostwire.android.util.SystemUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
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
  public void aMissingNativeLibraryIsRecordedAndDisablesTheStorage() {
    try (MockedStatic<SystemUtils> system = mockStatic(SystemUtils.class);
        MockedStatic<DataStoreManager> stores = mockStatic(DataStoreManager.class);
        MockedStatic<MainApplication> main = mockStatic(MainApplication.class)) {
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
}
