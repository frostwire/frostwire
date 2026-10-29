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

package com.devspark.appmsg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Queue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * AppMsg is shown from background threads; MsgManager's queue and animations must only change on
 * the main thread, or the main thread can display a queued message before its fade-in animation is
 * loaded and start a null animation (frostwire/frostwire#1372).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MsgManagerThreadingTest {

  @Before
  public void installPlatform() {
    // MsgManager asks the platform which thread is the UI thread; MainApplication would set the
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
  public void showingFromABackgroundThreadLeavesTheQueueToTheMainThread() throws Exception {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    AppMsg msg = AppMsg.makeText(activity, "added", AppMsg.STYLE_CONFIRM);
    int before = queue().size();

    Thread caller = new Thread(msg::show);
    caller.start();
    caller.join();

    assertEquals("the queue changed off the main thread", before, queue().size());
    shadowOf(Looper.getMainLooper()).idle();
    assertTrue("the message was not displayed", msg.isShowing());
  }

  private static Queue<?> queue() throws Exception {
    Field field = MsgManager.class.getDeclaredField("msgQueue");
    field.setAccessible(true);
    return (Queue<?>) field.get(MsgManager.getInstance());
  }
}
