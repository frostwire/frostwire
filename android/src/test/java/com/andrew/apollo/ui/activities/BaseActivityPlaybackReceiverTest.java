/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

/**
 * The music screens' playback receiver (GitHub #1456) was registered in onStart but unregistered
 * only in onDestroy, so a stopped screen kept reloading on every broadcast and each restart added
 * another registration; and it was exported, so any app could make the screens reload.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class BaseActivityPlaybackReceiverTest {

  private static final class Receiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {}
  }

  private static List<ShadowApplication.Wrapper> registrations(BroadcastReceiver receiver) {
    Application app = ApplicationProvider.getApplicationContext();
    return shadowOf(app).getRegisteredReceivers().stream()
        .filter(w -> w.broadcastReceiver == receiver)
        .collect(Collectors.toList());
  }

  @Test
  public void theReceiverOnlyHearsThisApp() {
    Context context = ApplicationProvider.getApplicationContext();
    Receiver receiver = new Receiver();

    BaseActivity.registerPlaybackStatus(context, receiver);

    List<ShadowApplication.Wrapper> registered = registrations(receiver);
    assertEquals(1, registered.size());
    assertTrue(
        "registered not exported",
        (registered.get(0).flags & ContextCompat.RECEIVER_NOT_EXPORTED) != 0);
  }

  @Test
  public void stoppingUnregistersSoRestartsDontStack() {
    Context context = ApplicationProvider.getApplicationContext();
    Receiver receiver = new Receiver();

    BaseActivity.registerPlaybackStatus(context, receiver);
    BaseActivity.unregisterPlaybackStatus(context, receiver);
    BaseActivity.registerPlaybackStatus(context, receiver);
    BaseActivity.unregisterPlaybackStatus(context, receiver);
    BaseActivity.unregisterPlaybackStatus(context, receiver); // a second stop is harmless

    assertEquals(0, registrations(receiver).size());
  }

  /** Source check: the activity itself needs the music browser's fragments. */
  @Test
  public void theActivityPairsRegisterWithStartAndUnregisterWithStop() throws Exception {
    String source =
        new String(
                Files.readAllBytes(
                    Path.of(System.getProperty("user.dir"))
                        .resolve("apollo/src/com/andrew/apollo/ui/activities/BaseActivity.java")),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    String onStop = source.substring(source.indexOf("protectedvoidonStop()"));
    onStop = onStop.substring(0, onStop.indexOf("@Override"));
    assertTrue(onStop.contains("unregisterPlaybackStatus(this,mPlaybackStatus)"));
    String onStart = source.substring(source.indexOf("protectedvoidonStart()"));
    onStart = onStart.substring(0, onStart.indexOf("@Override"));
    assertTrue(onStart.contains("registerPlaybackStatus(this,mPlaybackStatus)"));
  }
}
