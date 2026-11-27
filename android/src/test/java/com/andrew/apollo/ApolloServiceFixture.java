/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.test.utils.FakeMediaSourceFactory;
import androidx.media3.test.utils.TestExoPlayerBuilder;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import org.mockito.Mockito;
import org.robolectric.Robolectric;

/**
 * Boots a real {@link MusicPlaybackService} with an ExoPlayer that plays fake media, the way the
 * playback service tests do, for tests of the classes that talk to the running service (MusicUtils
 * and friends). The player thread is the test's main thread, so posted work runs when Robolectric
 * idles the main looper.
 */
public final class ApolloServiceFixture {

  public MusicPlaybackService service;
  public ExoPlayer player;
  public org.robolectric.android.controller.ServiceController<MusicPlaybackService> controller;

  public static ApolloServiceFixture start() throws Exception {
    ApolloServiceFixture f = new ApolloServiceFixture();
    installPlatform(true);
    setStatic(ConfigurationManager.class, "instance", Mockito.mock(ConfigurationManager.class));
    f.controller = Robolectric.buildService(MusicPlaybackService.class).create();
    f.service = f.controller.get();
    setStatic(
        MusicPlaybackService.class,
        "mPlayerHandler",
        new MusicPlaybackService.MusicPlayerHandler(Looper.getMainLooper()));
    f.player =
        new TestExoPlayerBuilder(ApplicationProvider.<Application>getApplicationContext())
            .setMediaSourceFactory(new FakeMediaSourceFactory())
            .build();
    f.service.attachPlayer(f.player);
    forgetCachedService();
    return f;
  }

  /**
   * Installs the Platform the code asks "am I on the UI thread?". With {@code mainIsUi} false
   * nothing is the UI thread, so code that would hop to a background thread runs inline.
   */
  @SuppressWarnings("unchecked")
  public static void installPlatform(boolean mainIsUi) {
    Platform platform =
        (Platform)
            Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) ->
                    "isUIThread".equals(method.getName())
                        ? mainIsUi && Looper.myLooper() == Looper.getMainLooper()
                        : null);
    try {
      // Platforms.set keeps the first Platform for good, and another test may have set its own.
      Field field = Platforms.class.getDeclaredField("platform");
      field.setAccessible(true);
      ((java.util.concurrent.atomic.AtomicReference<Platform>) field.get(null)).set(platform);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  /** MusicUtils caches the service weakly; a test must not see the one from the test before. */
  public static void forgetCachedService() throws Exception {
    setStatic(com.andrew.apollo.utils.MusicUtils.class, "musicPlaybackServiceRef", null);
  }

  /** No service is running: what MusicUtils sees before the service starts or after it dies. */
  public static void noService() throws Exception {
    setStatic(MusicPlaybackService.class, "INSTANCE", null);
    setStatic(MusicPlaybackService.class, "mPlayerHandler", null);
    forgetCachedService();
  }

  public void stop() throws Exception {
    if (player != null) {
      new Handler(player.getApplicationLooper()).post(player::release);
    }
    setStatic(MusicPlaybackService.class, "mPlayerHandler", null);
    setStatic(ConfigurationManager.class, "instance", null);
    forgetCachedService();
  }

  private static void setStatic(Class<?> c, String name, Object value) throws Exception {
    Field field = c.getDeclaredField(name);
    field.setAccessible(true);
    field.set(null, value);
  }
}
