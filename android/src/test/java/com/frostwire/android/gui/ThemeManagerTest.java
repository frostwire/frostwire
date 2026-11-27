/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.util.SystemUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class ThemeManagerTest {
  private final List<Runnable> background = new ArrayList<>();
  private final List<Runnable> uiFront = new ArrayList<>();
  private ConfigurationManager config;
  private MockedStatic<SystemUtils> system;
  private MockedStatic<ConfigurationManager> configStatic;

  @Before
  public void setUp() {
    config = mock(ConfigurationManager.class);
    system = mockStatic(SystemUtils.class);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            call -> {
              background.add(call.getArgument(1));
              return null;
            });
    system
        .when(() -> SystemUtils.postToUIThreadAtFront(any(Runnable.class)))
        .thenAnswer(
            call -> {
              uiFront.add(call.getArgument(0));
              return null;
            });
    configStatic = mockStatic(ConfigurationManager.class);
    configStatic.when(ConfigurationManager::instance).thenReturn(config);
  }

  @After
  public void tearDown() {
    configStatic.close();
    system.close();
  }

  @Test
  public void entriesMapToNightModesCaseInsensitivelyAndUnknownFollowsTheSystem() {
    assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeManager.getThemeModeFromEntry("light"));
    assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeManager.getThemeModeFromEntry("DARK"));
    assertEquals(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeManager.getThemeModeFromEntry("system"));
    assertEquals(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeManager.getThemeModeFromEntry("purple"));
  }

  @Test
  public void modesMapBackToTheirEntries() {
    assertEquals("light", ThemeManager.getThemeEntryFromMode(AppCompatDelegate.MODE_NIGHT_NO));
    assertEquals("dark", ThemeManager.getThemeEntryFromMode(AppCompatDelegate.MODE_NIGHT_YES));
    assertEquals(
        "system", ThemeManager.getThemeEntryFromMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
    assertNull(ThemeManager.getThemeEntryFromMode(12345));
  }

  @Test
  public void applyingAThemeSetsTheDefaultNightMode() {
    ThemeManager.applyThemeMode(AppCompatDelegate.MODE_NIGHT_YES);
    assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode());
    ThemeManager.applyThemeMode("light");
    assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode());
    ThemeManager.applyThemeMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
  }

  @Test
  public void loadingFromTheUiThreadHopsToABackgroundHandlerFirst() {
    system.when(SystemUtils::isUIThread).thenReturn(true);
    ThemeManager.OnThemeLoadedUIThreadCallback callback =
        mock(ThemeManager.OnThemeLoadedUIThreadCallback.class);
    ThemeManager.loadSavedThemeModeAsync(callback);
    assertEquals(1, background.size());
    assertEquals(0, uiFront.size());
    // on the background thread the saved entry is read and delivered at the front of the UI queue
    system.when(SystemUtils::isUIThread).thenReturn(false);
    when(config.getString(Constants.PREF_KEY_GUI_THEME_MODE, "system")).thenReturn("dark");
    background.get(0).run();
    assertEquals(1, uiFront.size());
    uiFront.get(0).run();
    verify(callback).onThemeLoaded(AppCompatDelegate.MODE_NIGHT_YES);
  }

  @Test
  public void aMissingSavedEntryDefaultsToTheSystemTheme() {
    system.when(SystemUtils::isUIThread).thenReturn(false);
    when(config.getString(Constants.PREF_KEY_GUI_THEME_MODE, "system")).thenReturn(null);
    ThemeManager.OnThemeLoadedUIThreadCallback callback =
        mock(ThemeManager.OnThemeLoadedUIThreadCallback.class);
    ThemeManager.loadSavedThemeModeAsync(callback);
    verify(config).setString(Constants.PREF_KEY_GUI_THEME_MODE, "system");
    uiFront.get(0).run();
    verify(callback).onThemeLoaded(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
  }

  @Test
  public void savingFromTheUiThreadHopsToABackgroundHandlerAndStoresTheEntryLowerCased() {
    system.when(SystemUtils::isUIThread).thenReturn(true);
    ThemeManager.saveThemeModeAsync("Dark");
    assertEquals(1, background.size());
    system.when(SystemUtils::isUIThread).thenReturn(false);
    background.get(0).run();
    verify(config).setString(Constants.PREF_KEY_GUI_THEME_MODE, "dark");
  }
}
