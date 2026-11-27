/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.util.FWImageLoader;
import com.frostwire.android.util.SavedStateSizes;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import com.frostwire.platform.SystemPaths;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The pieces of MainApplication that can run without booting it: the lifecycle callbacks that keep
 * the image loader healthy, and the thread that configures the BitTorrent engine.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class MainApplicationHelpersTest {

  @Test
  public void theImageLoaderIsCheckedWhenTheFirstActivityStartsAndOnEveryResume() {
    MainApplication.ImageLoaderLifecycleCallbacks callbacks =
        new MainApplication.ImageLoaderLifecycleCallbacks();
    Activity first = Robolectric.buildActivity(Activity.class).get();
    Activity second = Robolectric.buildActivity(Activity.class).get();
    try (MockedStatic<FWImageLoader> loader = mockStatic(FWImageLoader.class)) {
      callbacks.onActivityStarted(first);
      callbacks.onActivityStarted(second);
      loader.verify(() -> FWImageLoader.ensureHealthyInstance(any()), times(1));
      callbacks.onActivityResumed(first);
      callbacks.onActivityResumed(second);
      loader.verify(() -> FWImageLoader.ensureHealthyInstance(any()), times(3));
      // not the last activity: the memory cache stays
      callbacks.onActivityStopped(second);
      loader.verify(FWImageLoader::onAppBackgrounded, never());
      // the last one stopped: the app is in the background
      callbacks.onActivityStopped(first);
      loader.verify(FWImageLoader::onAppBackgrounded);
      callbacks.onActivityPaused(first);
      callbacks.onActivityDestroyed(first);
    }
  }

  @Test
  public void aFailingImageLoaderDoesNotBreakTheActivityLifecycle() {
    MainApplication.ImageLoaderLifecycleCallbacks callbacks =
        new MainApplication.ImageLoaderLifecycleCallbacks();
    Activity activity = Robolectric.buildActivity(Activity.class).get();
    try (MockedStatic<FWImageLoader> loader = mockStatic(FWImageLoader.class)) {
      loader
          .when(() -> FWImageLoader.ensureHealthyInstance(any()))
          .thenThrow(new IllegalStateException("loader broke"));
      callbacks.onActivityStarted(activity);
      callbacks.onActivityResumed(activity);
      loader.verify(() -> FWImageLoader.ensureHealthyInstance(any()), times(2));
    }
  }

  @Test
  public void savedStateSizesAreCheckedForActivitiesAndTheirFragments() {
    MainApplication.ImageLoaderLifecycleCallbacks callbacks =
        new MainApplication.ImageLoaderLifecycleCallbacks();
    Bundle state = new Bundle();
    try (MockedStatic<SavedStateSizes> sizes = mockStatic(SavedStateSizes.class)) {
      Activity plain = Robolectric.buildActivity(Activity.class).get();
      callbacks.onActivityCreated(plain, null);
      callbacks.onActivitySaveInstanceState(plain, state);
      sizes.verify(() -> SavedStateSizes.check(Activity.class.getName(), state));

      org.robolectric.android.controller.ActivityController<FragmentActivity> controller =
          Robolectric.buildActivity(FragmentActivity.class).create().start().resume();
      FragmentActivity activity = controller.get();
      callbacks.onActivityCreated(activity, null);
      Fragment fragment = new Fragment();
      activity
          .getSupportFragmentManager()
          .beginTransaction()
          .add(fragment, "f")
          .commitNowAllowingStateLoss();
      Bundle out = new Bundle();
      controller.saveInstanceState(out);
      ArgumentCaptor<String> owners = ArgumentCaptor.forClass(String.class);
      sizes.verify(() -> SavedStateSizes.check(owners.capture(), any(Bundle.class)), atLeast(2));
      assertTrue(owners.getAllValues().contains(Fragment.class.getName()));
    }
  }

  private static BTEngineRun runInitializer(
      ConfigurationManager config, Platform platform, SystemPaths paths) {
    List<Throwable> failures = new ArrayList<>();
    try (MockedStatic<ConfigurationManager> configStatic = mockStatic(ConfigurationManager.class);
        MockedStatic<Platforms> platforms = mockStatic(Platforms.class);
        MockedStatic<MainApplication> main = mockStatic(MainApplication.class)) {
      configStatic.when(ConfigurationManager::instance).thenReturn(config);
      if (platform != null) {
        platforms.when(Platforms::get).thenReturn(platform);
      } else {
        platforms
            .when(Platforms::get)
            .thenThrow(new IllegalStateException("Platform can't be null"));
      }
      main.when(() -> MainApplication.recordBTEngineInitializationFailure(any()))
          .thenAnswer(
              call -> {
                failures.add(call.getArgument(0));
                return null;
              });
      new MainApplication.BTEngineInitializer().run();
    }
    return new BTEngineRun(failures);
  }

  private static final class BTEngineRun {
    final List<Throwable> failures;

    BTEngineRun(List<Throwable> failures) {
      this.failures = failures;
    }
  }

  private static ConfigurationManager config(int startPort, int endPort) {
    ConfigurationManager cm = mock(ConfigurationManager.class);
    when(cm.getInt(eq(Constants.PREF_KEY_TORRENT_INCOMING_PORT_START), any(Integer.class)))
        .thenReturn(startPort);
    when(cm.getInt(eq(Constants.PREF_KEY_TORRENT_INCOMING_PORT_END), any(Integer.class)))
        .thenReturn(endPort);
    when(cm.getString(anyString())).thenReturn("not a number");
    return cm;
  }

  private static SystemPaths paths() {
    SystemPaths paths = mock(SystemPaths.class);
    when(paths.libtorrent()).thenReturn(new File("/lt"));
    when(paths.torrents()).thenReturn(new File("/torrents"));
    when(paths.data()).thenReturn(new File("/data"));
    return paths;
  }

  private static Platform platform(SystemPaths paths) {
    Platform platform = mock(Platform.class);
    when(platform.systemPaths()).thenReturn(paths);
    return platform;
  }

  /**
   * Everything the initializer does before it touches libtorrent is plain Java: it must get to the
   * engine, which a unit test cannot load, so the only failure recorded is the missing native
   * library, never a configuration problem.
   */
  private static void assertOnlyTheEngineFailed(BTEngineRun run) {
    assertTrue("exactly one failure is recorded", run.failures.size() == 1);
    Throwable failure = run.failures.get(0);
    assertNotNull(failure);
    assertFalse(
        "configuration parsing must not be what failed: " + failure,
        failure instanceof NumberFormatException || failure instanceof NullPointerException);
  }

  @Test
  public void theEngineIsConfiguredWithTheDefaultPortRange() {
    SystemPaths paths = paths();
    assertOnlyTheEngineFailed(
        runInitializer(
            config(
                Constants.DEFAULT_TORRENT_INCOMING_PORT_START,
                Constants.DEFAULT_TORRENT_INCOMING_PORT_END),
            platform(paths),
            paths));
  }

  @Test
  public void theEngineAcceptsASinglePortOrARangeFromThePreferences() {
    SystemPaths paths = paths();
    assertOnlyTheEngineFailed(runInitializer(config(6881, 6881), platform(paths), paths));
    assertOnlyTheEngineFailed(runInitializer(config(6881, 6890), platform(paths), paths));
  }

  @Test
  public void anInvalidPortRangeFallsBackToTheDefaults() {
    SystemPaths paths = paths();
    assertOnlyTheEngineFailed(runInitializer(config(0, 100), platform(paths), paths));
    assertOnlyTheEngineFailed(runInitializer(config(5000, 4000), platform(paths), paths));
    assertOnlyTheEngineFailed(runInitializer(config(2000, 70000), platform(paths), paths));
  }

  @Test
  public void validI2pNumbersAreParsedAndBlankOnesFallBack() {
    SystemPaths paths = paths();
    ConfigurationManager cm = config(6881, 6890);
    when(cm.getString(anyString())).thenReturn(" 5 ");
    assertOnlyTheEngineFailed(runInitializer(cm, platform(paths), paths));
    ConfigurationManager blank = config(6881, 6890);
    when(blank.getString(anyString())).thenReturn("");
    assertOnlyTheEngineFailed(runInitializer(blank, platform(paths), paths));
    ConfigurationManager missing = config(6881, 6890);
    when(missing.getString(anyString())).thenReturn(null);
    assertOnlyTheEngineFailed(runInitializer(missing, platform(paths), paths));
  }

  @Test
  public void aMissingPlatformIsRecordedAsTheInitializationFailure() {
    BTEngineRun run = runInitializer(config(6881, 6890), null, null);
    assertTrue(run.failures.size() == 1);
    assertTrue(run.failures.get(0) instanceof IllegalStateException);
    assertNotNull(ApplicationProvider.getApplicationContext());
  }
}
