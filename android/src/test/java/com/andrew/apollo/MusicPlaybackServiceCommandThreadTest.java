/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * A command intent (media button, notification action, foreground-state change) moved to the shared
 * MISC thread and waited there up to 4 s for the service to finish initializing, so network-change
 * handling, music commands and everything else on MISC waited behind it. A 3.2.2 ANR dump showed
 * that wait (GitHub #1376), and it made tests that use MISC time out. Commands now run on the
 * player thread, after initService, without blocking MISC.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicPlaybackServiceCommandThreadTest {

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

  /** The handler is static: left set, later tests would post their work to this test's thread. */
  @After
  public void stopThePlayerThread() throws Exception {
    Field field = MusicPlaybackService.class.getDeclaredField("mPlayerHandler");
    field.setAccessible(true);
    Handler handler = (Handler) field.get(null);
    if (handler != null) {
      handler.getLooper().quitSafely();
      field.set(null, null);
    }
  }

  @Test
  public void aCommandDoesNotHoldTheSharedMiscThread() throws Exception {
    MusicPlaybackService service =
        Robolectric.buildService(MusicPlaybackService.class).create().get();
    Method setUpHandler = MusicPlaybackService.class.getDeclaredMethod("setupMPlayerHandler");
    setUpHandler.setAccessible(true);
    setUpHandler.invoke(null);
    Field initialized = MusicPlaybackService.class.getDeclaredField("serviceInitialized");
    initialized.setAccessible(true);
    ((AtomicBoolean) initialized.get(service)).set(true);

    service.handleCommandIntent(new Intent(MusicPlaybackService.FOREGROUND_STATE_CHANGED));
    CountDownLatch miscRan = new CountDownLatch(1);
    SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, miscRan::countDown);

    assertTrue(
        "work queued on MISC after a command must not wait for it",
        miscRan.await(1, TimeUnit.SECONDS));
  }
}
