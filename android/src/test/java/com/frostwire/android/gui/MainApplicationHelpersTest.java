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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import com.frostwire.bittorrent.BTContext;
import com.frostwire.bittorrent.BTEngine;
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

  /**
   * Runs the initializer where libtorrent cannot be created: unit tests do load it, so the link
   * error of a device without the native library is simulated at the engine's factory.
   */
  private static BTEngineRun runInitializer(
      ConfigurationManager config, Platform platform, SystemPaths paths) {
    return run(config, platform, null);
  }

  /** Runs the initializer with {@code engine} as the application's engine. */
  private static BTEngineRun runInitializerWith(
      ConfigurationManager config, Platform platform, BTEngine engine) {
    return run(config, platform, engine);
  }

  private static BTEngineRun run(ConfigurationManager config, Platform platform, BTEngine engine) {
    List<Throwable> failures = new ArrayList<>();
    BTContext previous = BTEngine.ctx;
    BTContext context;
    try (MockedStatic<ConfigurationManager> configStatic = mockStatic(ConfigurationManager.class);
        MockedStatic<Platforms> platforms = mockStatic(Platforms.class);
        MockedStatic<BTEngine> engines = mockStatic(BTEngine.class);
        MockedStatic<MainApplication> main = mockStatic(MainApplication.class)) {
      if (engine != null) {
        engines.when(BTEngine::getInstance).thenReturn(engine);
      } else {
        engines
            .when(BTEngine::getInstance)
            .thenThrow(new UnsatisfiedLinkError("jlibtorrent native library is not available"));
      }
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
      context = BTEngine.ctx;
    } finally {
      BTEngine.ctx = previous;
    }
    return new BTEngineRun(failures, context);
  }

  private static final class BTEngineRun {
    final List<Throwable> failures;
    final BTContext context;

    BTEngineRun(List<Throwable> failures, BTContext context) {
      this.failures = failures;
      this.context = context;
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
   * engine, which is made to fail here, so the only failure recorded is the missing native library,
   * never a configuration problem.
   */
  private static void assertOnlyTheEngineFailed(BTEngineRun run) {
    assertTrue("exactly one failure is recorded", run.failures.size() == 1);
    Throwable failure = run.failures.get(0);
    assertNotNull(failure);
    assertTrue("the engine is what failed: " + failure, failure instanceof UnsatisfiedLinkError);
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

  @Test
  public void aWorkingEngineIsStartedWithTheDefaultPortRangeAndTheAppsFolders() {
    SystemPaths paths = paths();
    BTEngine engine = mock(BTEngine.class);
    BTEngineRun run =
        runInitializerWith(
            config(
                Constants.DEFAULT_TORRENT_INCOMING_PORT_START,
                Constants.DEFAULT_TORRENT_INCOMING_PORT_END),
            platform(paths),
            engine);
    assertTrue(run.failures.isEmpty());
    verify(engine).start();
    BTContext ctx = run.context;
    assertNotNull(ctx);
    assertEquals(new File("/lt"), ctx.homeDir);
    assertEquals(new File("/torrents"), ctx.torrentsDir);
    assertEquals(new File("/data"), ctx.dataDir);
    assertTrue(ctx.optimizeMemory);
    assertEquals(10, ctx.retries);
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("0\\.0\\.0\\.0:(\\d+),\\[::\\]:(\\d+)")
            .matcher(ctx.interfaces);
    assertTrue(ctx.interfaces, m.matches());
    int port = Integer.parseInt(m.group(1));
    assertEquals(m.group(1), m.group(2));
    assertTrue(port >= 37000 && port < 57000);
    String[] version = Constants.FROSTWIRE_VERSION_STRING.split("\\.");
    assertEquals(Integer.parseInt(version[0]), ctx.version[0]);
    assertEquals(Integer.parseInt(version[1]), ctx.version[1]);
    assertEquals(Integer.parseInt(version[2]), ctx.version[2]);
  }

  @Test
  public void aConfiguredPortRangeIsUsedAndASinglePortGetsNoRetries() {
    SystemPaths paths = paths();
    BTEngineRun range =
        runInitializerWith(config(6881, 6890), platform(paths), mock(BTEngine.class));
    assertEquals("0.0.0.0:6881,[::]:6881", range.context.interfaces);
    assertEquals(9, range.context.retries);
    BTEngineRun single =
        runInitializerWith(config(6881, 6881), platform(paths), mock(BTEngine.class));
    assertEquals("0.0.0.0:6881,[::]:6881", single.context.interfaces);
    assertEquals(1, single.context.retries);
  }

  @Test
  public void anInvalidConfiguredRangeIsReplacedByTheDefaultRange() {
    SystemPaths paths = paths();
    for (ConfigurationManager invalid :
        new ConfigurationManager[] {config(0, 100), config(5000, 4000), config(2000, 70000)}) {
      BTEngineRun run = runInitializerWith(invalid, platform(paths), mock(BTEngine.class));
      assertEquals(10, run.context.retries);
      assertTrue(run.context.interfaces, run.context.interfaces.startsWith("0.0.0.0:"));
      assertFalse(run.context.interfaces.startsWith("0.0.0.0:2000,"));
    }
  }

  @Test
  public void i2pAndNatPmpNumbersAreParsedAndBadOnesFallBackToTheirDefaults() {
    SystemPaths paths = paths();
    ConfigurationManager numbers = config(6881, 6890);
    when(numbers.getString(anyString())).thenReturn(" 5 ");
    BTContext parsed = runInitializerWith(numbers, platform(paths), mock(BTEngine.class)).context;
    assertEquals(5, parsed.i2pPort);
    assertEquals(5, parsed.i2pInboundQuantity);
    assertEquals(5, parsed.i2pOutboundQuantity);
    assertEquals(5, parsed.i2pInboundLength);
    assertEquals(5, parsed.i2pOutboundLength);
    assertEquals(5, parsed.natpmpLeaseDuration);
    BTContext defaults =
        runInitializerWith(config(6881, 6890), platform(paths), mock(BTEngine.class)).context;
    assertEquals(7656, defaults.i2pPort);
    assertEquals(3, defaults.i2pInboundQuantity);
    assertEquals(3, defaults.i2pOutboundLength);
    assertEquals(3600, defaults.natpmpLeaseDuration);
  }

  @Test
  public void aEngineThatFailsToStartIsRecordedAsTheInitializationFailure() {
    SystemPaths paths = paths();
    BTEngine engine = mock(BTEngine.class);
    IllegalStateException boom = new IllegalStateException("session did not start");
    org.mockito.Mockito.doThrow(boom).when(engine).start();
    BTEngineRun run = runInitializerWith(config(6881, 6890), platform(paths), engine);
    assertEquals(1, run.failures.size());
    assertSame(boom, run.failures.get(0));
  }
}
