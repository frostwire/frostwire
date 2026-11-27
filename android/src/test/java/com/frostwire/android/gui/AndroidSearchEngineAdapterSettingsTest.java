/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.search.engines.SearchEngineID;
import com.frostwire.search.engines.TellurideSearchFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The adapter reads and writes engine switches through the app's preferences. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class AndroidSearchEngineAdapterSettingsTest {

  @Test
  public void anEngineIsEnabledWhenItsPreferenceSaysSo() {
    ConfigurationManager config = mock(ConfigurationManager.class);
    String key = AndroidSearchEngineAdapter.preferenceKey(SearchEngineID.TPB_ID);
    when(config.getBoolean(key)).thenReturn(true);
    AndroidSearchEngineAdapter adapter = new AndroidSearchEngineAdapter();
    try (MockedStatic<ConfigurationManager> statics = mockStatic(ConfigurationManager.class)) {
      statics.when(ConfigurationManager::instance).thenReturn(config);
      assertTrue(adapter.isEnabled(SearchEngineID.TPB_ID));
      assertFalse(adapter.isEnabled(SearchEngineID.NYAA_ID));
      adapter.setEnabled(SearchEngineID.TPB_ID, false);
      verify(config).setBoolean(key, false);
    }
  }

  @Test
  public void thePlatformEnginesAreOfferedAccordingToTheBuild() {
    AndroidSearchEngineAdapter adapter = new AndroidSearchEngineAdapter();
    // the Local engine is a debug-build tool
    assertEquals(
        com.frostwire.android.BuildConfig.DEBUG, adapter.isOffered(SearchEngineID.LOCAL_ID));
    // YouTube is not offered on the Google Play distribution
    assertEquals(
        !com.frostwire.android.core.Constants.IS_GOOGLE_PLAY_DISTRIBUTION
            || com.frostwire.android.core.Constants.IS_BASIC_AND_DEBUG,
        adapter.isOffered(SearchEngineID.YT_ID));
    assertTrue(adapter.isOffered(SearchEngineID.TPB_ID));
  }

  @Test
  public void theUserAgentNamesTheAppVersionAndTheOs() {
    AndroidSearchEngineAdapter adapter = new AndroidSearchEngineAdapter();
    assertNotNull(adapter.userAgent());
    String os = AndroidSearchEngineAdapter.osVersionString();
    assertTrue(os.endsWith("_" + android.os.Build.VERSION.SDK_INT));
    assertEquals(4, os.split("_", -1).length);
  }

  @Test
  public void soundCloudCredentialsComeFromTheSoftwareUpdater() {
    AndroidSearchEngineAdapter adapter = new AndroidSearchEngineAdapter();
    assertEquals(SoftwareUpdater.getSoundCloudClientId(), adapter.soundCloudClientId());
    assertEquals(SoftwareUpdater.getSoundCloudAppVersion(), adapter.soundCloudAppVersion());
  }

  @Test
  public void tellurideIsPluggedInOnceThePythonRuntimeCan() {
    AndroidSearchEngineAdapter adapter = new AndroidSearchEngineAdapter();
    assertNull(adapter.telluride());
    TellurideSearchFactory factory = mock(TellurideSearchFactory.class);
    adapter.telluride(factory);
    assertSame(factory, adapter.telluride());
  }
}
