/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui;

import android.app.Application;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Proxy;

/**
 * What the Apollo screens expect from {@code MainApplication} when a test runs them under a plain
 * {@link Application}: the platform that knows the UI thread and the configuration manager.
 */
public final class ApolloUiTestSupport {

  private ApolloUiTestSupport() {}

  /** A platform whose only behavior is telling the main looper's thread from the others. */
  public static void installPlatform() {
    Platforms.set(
        (Platform)
            Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) ->
                    "isUIThread".equals(method.getName())
                        ? Looper.myLooper() == Looper.getMainLooper()
                        : null));
  }

  /** The real configuration manager, created once per test JVM sandbox as the app does. */
  public static void installConfiguration() {
    try {
      ConfigurationManager.create(ApplicationProvider.getApplicationContext());
    } catch (RuntimeException alreadyCreated) {
      // created by an earlier test in this sandbox
    }
    ConfigurationManager.instance();
  }

  /**
   * Runs the main looper until the condition holds, for work that hops to a background handler
   * thread and back; fails after ten seconds.
   */
  public static void waitUntil(String what, java.util.function.BooleanSupplier condition) {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      org.robolectric.shadows.ShadowLooper.idleMainLooper();
      try {
        Thread.sleep(2);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    if (!condition.getAsBoolean()) {
      throw new AssertionError("timed out waiting for " + what);
    }
  }

  /** Lets work already posted to the main looper, and what it posts in turn, run. */
  public static void settle() {
    for (int i = 0; i < 5; i++) {
      org.robolectric.shadows.ShadowLooper.idleMainLooper();
    }
  }

  /** Names of the {@code MusicUtils} calls that reach the playback service or change what plays. */
  private static final java.util.Set<String> SERVICE_CALLS =
      java.util.Set.of(
          "playFDs",
          "playNext",
          "addToQueue",
          "addToPlaylist",
          "playPlaylist",
          "playFavorites",
          "playLastAdded",
          "shuffleAll",
          "movePlaylistMember",
          "removePlaylistMember",
          "removeFromPlaylist",
          "notifyForegroundStateChanged",
          "seek",
          "next",
          "previous",
          "toggleFavorite",
          "playFileFromUri",
          "playAlbum",
          "playArtist",
          "playPauseOrResume",
          "cycleRepeat",
          "cycleShuffle",
          "setQueuePosition",
          "removeTrack",
          "moveQueueItem",
          "clearQueue");

  /**
   * A {@code MusicUtils} with no service behind it: the calls that would reach the service are
   * recorded in {@code calls} as {@code name[long args...]} instead of running, the waits for the
   * service run their work at once, and everything else is the real code. Close it after the test.
   */
  public static org.mockito.MockedStatic<com.andrew.apollo.utils.MusicUtils> mockMusicUtils(
      java.util.List<String> calls) {
    org.mockito.MockedStatic<com.andrew.apollo.utils.MusicUtils> music =
        org.mockito.Mockito.mockStatic(
            com.andrew.apollo.utils.MusicUtils.class,
            invocation -> {
              String name = invocation.getMethod().getName();
              if ("executeWithMusicPlaybackService".equals(name)) {
                ((Runnable) invocation.getArgument(1)).run();
                return null;
              }
              if ("startMusicPlaybackService".equals(name)) {
                ((Runnable) invocation.getArgument(2)).run();
                return null;
              }
              if (SERVICE_CALLS.contains(name)) {
                StringBuilder call = new StringBuilder(name).append('[');
                for (Object argument : invocation.getArguments()) {
                  if (argument instanceof long[]) {
                    call.append(java.util.Arrays.toString((long[]) argument));
                  } else if (argument instanceof Number || argument instanceof Boolean) {
                    call.append(argument).append(' ');
                  } else if (argument instanceof android.net.Uri) {
                    call.append(argument).append(' ');
                  }
                }
                calls.add(call.append(']').toString().replace(" ]", "]"));
                return null;
              }
              return invocation.callRealMethod();
            });
    // other tests in the JVM may have left a service behind: start from an idle player
    music.when(com.andrew.apollo.utils.MusicUtils::getMusicPlaybackService).thenReturn(null);
    music.when(com.andrew.apollo.utils.MusicUtils::isMusicPlaybackServiceRunning).thenReturn(false);
    music.when(com.andrew.apollo.utils.MusicUtils::isStopped).thenReturn(true);
    music.when(com.andrew.apollo.utils.MusicUtils::isPlaying).thenReturn(false);
    music.when(com.andrew.apollo.utils.MusicUtils::isShuffleEnabled).thenReturn(false);
    music.when(com.andrew.apollo.utils.MusicUtils::getRepeatMode).thenReturn(0);
    music.when(com.andrew.apollo.utils.MusicUtils::getCurrentAudioId).thenReturn(-1L);
    return music;
  }

  public static void installAll() {
    installPlatform();
    installConfiguration();
  }
}
