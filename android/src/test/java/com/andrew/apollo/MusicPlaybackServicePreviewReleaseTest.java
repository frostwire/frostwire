/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.os.Looper;
import androidx.media3.exoplayer.ExoPlayer;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * The preview player (playSimple) is built on the main thread, but play() stopped it from the
 * player thread once ExoPlayer's playlist became the queue. ExoPlayer refuses calls from another
 * thread, the error was swallowed and release() never ran: the preview kept playing over the music
 * and leaked (GitHub #1452). It is now stopped and released on its own thread.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicPlaybackServicePreviewReleaseTest {

  @Test
  public void thePreviewIsStoppedAndReleasedOnItsOwnThread() throws Exception {
    List<String> calls = new CopyOnWriteArrayList<>();
    ExoPlayer preview =
        (ExoPlayer)
            Proxy.newProxyInstance(
                ExoPlayer.class.getClassLoader(),
                new Class<?>[] {ExoPlayer.class},
                (proxy, method, args) -> {
                  switch (method.getName()) {
                    case "getApplicationLooper":
                      return Looper.getMainLooper();
                    case "stop":
                    case "release":
                      calls.add(
                          method.getName()
                              + (Looper.myLooper() == Looper.getMainLooper()
                                  ? " on main"
                                  : " off main"));
                      return null;
                    default:
                      return null;
                  }
                });

    Thread playerThread = new Thread(() -> MusicPlaybackService.stopAndRelease(preview));
    playerThread.start();
    playerThread.join(5_000);
    ShadowLooper.idleMainLooper();

    assertEquals(List.of("stop on main", "release on main"), calls);
  }
}
