/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.andrew.apollo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.ActivityManager;
import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Proxy;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * A service started with startForegroundService() must call startForeground() in time, or the
 * system kills the app with ForegroundServiceDidNotStartInTimeException (frostwire/frostwire#1371).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicPlaybackServiceForegroundStartTest {

  @Before
  public void installPlatform() {
    // The service asks the platform which thread is the UI thread; MainApplication would set the
    // real one, and it isn't used here (application = Application.class).
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
  public void aStartPromotesTheServiceEvenWhenTheAppIsInTheBackground() {
    MusicPlaybackService service =
        Robolectric.buildService(MusicPlaybackService.class).create().get();
    reportAppProcessImportance(service, ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED);

    service.onStartCommand(new Intent(service, MusicPlaybackService.class), 0, 1);

    assertNotNull(
        "startForeground() was not called", shadowOf(service).getLastForegroundNotification());
    assertFalse(shadowOf(service).isForegroundStopped());
  }

  @Test
  public void aStartPromotesTheServiceAgainAfterItLeftTheForeground() {
    MusicPlaybackService service =
        Robolectric.buildService(MusicPlaybackService.class).create().get();
    service.onStartCommand(new Intent(service, MusicPlaybackService.class), 0, 1);
    // Media3 takes the service out of the foreground when playback pauses.
    service.stopForeground(Service.STOP_FOREGROUND_DETACH);
    assertTrue(shadowOf(service).isForegroundStopped());

    service.onStartCommand(new Intent(service, MusicPlaybackService.class), 0, 2);

    assertFalse(
        "the second start did not call startForeground()", shadowOf(service).isForegroundStopped());
  }

  private static void reportAppProcessImportance(Context context, int importance) {
    ActivityManager.RunningAppProcessInfo process = new ActivityManager.RunningAppProcessInfo();
    process.processName = context.getPackageName();
    process.importance = importance;
    ActivityManager activityManager =
        (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    shadowOf(activityManager).setProcesses(Collections.singletonList(process));
  }
}
