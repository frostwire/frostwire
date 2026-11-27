/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.Application;
import com.frostwire.android.util.FWImageLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The memory-pressure callbacks and the engine failure flag of MainApplication. The application
 * object is only instantiated, never booted. The failure flag is process-wide, so this runs in a
 * Robolectric sandbox (SDK 29) of its own.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, application = Application.class)
public class MainApplicationMemoryAndFailureTest {

  @Test
  public void lowMemoryFreesTheImageMemoryCacheAndChecksTheLoader() {
    MainApplication application = new MainApplication();
    FWImageLoader loader = mock(FWImageLoader.class);
    try (MockedStatic<FWImageLoader> statics = mockStatic(FWImageLoader.class)) {
      statics.when(FWImageLoader::getInstanceIfExists).thenReturn(loader);
      application.onLowMemory();
      verify(loader).clearMemoryCache();
      statics.verify(() -> FWImageLoader.ensureHealthyInstance(application));
    }
  }

  @Test
  public void lowMemoryWithoutALoaderOnlyChecksTheLoader() {
    MainApplication application = new MainApplication();
    try (MockedStatic<FWImageLoader> statics = mockStatic(FWImageLoader.class)) {
      statics.when(FWImageLoader::getInstanceIfExists).thenReturn(null);
      application.onLowMemory();
      statics.verify(() -> FWImageLoader.ensureHealthyInstance(application));
    }
  }

  @Test
  @SuppressWarnings("deprecation")
  public void onlyModerateOrWorseMemoryTrimsCheckTheLoader() {
    MainApplication application = new MainApplication();
    try (MockedStatic<FWImageLoader> statics = mockStatic(FWImageLoader.class)) {
      application.onTrimMemory(Application.TRIM_MEMORY_UI_HIDDEN);
      statics.verify(() -> FWImageLoader.ensureHealthyInstance(any()), never());
      application.onTrimMemory(Application.TRIM_MEMORY_MODERATE);
      statics.verify(() -> FWImageLoader.ensureHealthyInstance(application));
      application.onTrimMemory(Application.TRIM_MEMORY_COMPLETE);
      statics.verify(
          () -> FWImageLoader.ensureHealthyInstance(application), org.mockito.Mockito.times(2));
    }
  }

  @Test
  public void theFirstEngineFailureIsRememberedAndNullIsIgnored() {
    assertFalse(MainApplication.hasBTEngineInitializationFailure());
    MainApplication.recordBTEngineInitializationFailure(null);
    assertFalse(MainApplication.hasBTEngineInitializationFailure());
    MainApplication.recordBTEngineInitializationFailure(new IllegalStateException("first"));
    assertTrue(MainApplication.hasBTEngineInitializationFailure());
    // a later failure does not replace the first one, and the flag stays set
    MainApplication.recordBTEngineInitializationFailure(new IllegalStateException("second"));
    assertTrue(MainApplication.hasBTEngineInitializationFailure());
  }
}
