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

import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import android.view.KeyEvent;
import androidx.media3.session.MediaSession;
import androidx.media3.test.utils.FakePlayer;
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
 * The media button and notification-action intents reach the service through onStartCommand(),
 * where Media3 hands them to the session's player. The service's override must let Media3 see them.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicPlaybackServiceMediaButtonTest {

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
  public void aMediaButtonIntentReachesThePlayer() {
    MusicPlaybackService service =
        Robolectric.buildService(MusicPlaybackService.class).create().get();
    FakePlayer player = new FakePlayer();
    service.mMediaSession = new MediaSession.Builder(service, player).setId("test").build();

    Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
    intent.setClass(service, MusicPlaybackService.class);
    intent.putExtra(
        Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY));
    service.onStartCommand(intent, 0, 1);
    shadowOf(Looper.getMainLooper()).idle();

    assertTrue("the play button never reached the player", player.getPlayWhenReady());
  }
}
