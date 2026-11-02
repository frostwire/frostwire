/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * Two queue changes within a second sent one QUEUE_CHANGED: the second was dropped, so the queue
 * screen and the saved queue missed it (GitHub #1453). It now goes out when the second ends.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicPlaybackServiceNotifyChangeTest {

  @Before
  public void installPlatform() {
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

  private static long queueChangedBroadcasts() {
    Application app = ApplicationProvider.getApplicationContext();
    return shadowOf(app).getBroadcastIntents().stream()
        .map(Intent::getAction)
        .filter(MusicPlaybackService.QUEUE_CHANGED::equals)
        .count();
  }

  @Test
  public void aSecondQueueChangeWithinTheIntervalStillGoesOut() throws Exception {
    MusicPlaybackService service =
        Robolectric.buildService(MusicPlaybackService.class).create().get();
    Method setUpHandler = MusicPlaybackService.class.getDeclaredMethod("setupMPlayerHandler");
    setUpHandler.setAccessible(true);
    Handler handler = (Handler) setUpHandler.invoke(null);
    ShadowLooper player = shadowOf(handler.getLooper());
    Method notifyChange =
        MusicPlaybackService.class.getDeclaredMethod("notifyChange", String.class);
    notifyChange.setAccessible(true);
    player.idle();
    long before = queueChangedBroadcasts();

    notifyChange.invoke(service, MusicPlaybackService.QUEUE_CHANGED);
    player.idle();
    notifyChange.invoke(service, MusicPlaybackService.QUEUE_CHANGED);
    player.idleFor(Duration.ofSeconds(2));

    assertEquals(2, queueChangedBroadcasts() - before);
  }
}
