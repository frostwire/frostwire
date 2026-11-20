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
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Proxy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * MusicUtils tells the service whether a music screen is in the foreground with NOW_IN_FOREGROUND;
 * the service used to ignore it, so notification promotion never knew.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicPlaybackServiceForegroundFlagTest {

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
  public void theForegroundExtraIsRecorded() {
    MusicPlaybackService service =
        Robolectric.buildService(MusicPlaybackService.class).create().get();
    Intent intent = new Intent(MusicPlaybackService.FOREGROUND_STATE_CHANGED);

    assertFalse(service.isMusicActivityInForeground());
    service.applyForegroundState(intent.putExtra(MusicPlaybackService.NOW_IN_FOREGROUND, true));
    assertTrue(service.isMusicActivityInForeground());
    service.applyForegroundState(intent.putExtra(MusicPlaybackService.NOW_IN_FOREGROUND, false));
    assertFalse(service.isMusicActivityInForeground());
  }

  @Test
  public void anIntentWithoutTheExtraLeavesItAlone() {
    MusicPlaybackService service =
        Robolectric.buildService(MusicPlaybackService.class).create().get();
    service.applyForegroundState(
        new Intent().putExtra(MusicPlaybackService.NOW_IN_FOREGROUND, true));

    service.applyForegroundState(new Intent(MusicPlaybackService.PREVIOUS_ACTION));

    assertTrue(service.isMusicActivityInForeground());
  }
}
