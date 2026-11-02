/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.core;

import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Looper;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Start-up asked for yt-dlp's version on the shared MISC thread, which then waited up to 30 s for
 * the Python runtime, twice: music commands, network-change handling and every other MISC task sat
 * behind it after each launch. The version is now looked up on its own thread.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TellurideCourierVersionThreadTest {

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

  @Test
  public void askingForTheVersionOnMiscDoesNotHoldMisc() throws Exception {
    SystemUtils.postToHandler(
        SystemUtils.HandlerThreadName.MISC, () -> TellurideCourier.ytDlpVersion(version -> {}));
    CountDownLatch miscRan = new CountDownLatch(1);
    SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, miscRan::countDown);

    assertTrue("MISC must not wait for the Python runtime", miscRan.await(2, TimeUnit.SECONDS));
  }
}
