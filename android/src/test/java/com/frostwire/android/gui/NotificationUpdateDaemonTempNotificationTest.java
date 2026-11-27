/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import android.app.Application;
import android.app.Notification;
import android.content.Context;
import android.content.pm.ServiceInfo;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.MusicPlaybackService;
import com.frostwire.android.core.Constants;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The placeholder notification the music service posts while it is still starting. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class NotificationUpdateDaemonTempNotificationTest {

  /** Records startForeground calls, and answers every Context call from the real application. */
  private static final class RecordingService {
    final List<Object[]> foregroundCalls = new ArrayList<>();
    boolean fail;
    final MusicPlaybackService service;

    RecordingService(Context app) {
      service =
          mock(
              MusicPlaybackService.class,
              withSettings()
                  .defaultAnswer(
                      (InvocationOnMock call) -> {
                        if (call.getMethod().getName().equals("startForeground")) {
                          foregroundCalls.add(call.getArguments());
                          if (fail) {
                            throw new IllegalStateException("not allowed to start foreground");
                          }
                          return null;
                        }
                        if (Context.class.isAssignableFrom(call.getMethod().getDeclaringClass())) {
                          return call.getMethod().invoke(app, call.getArguments());
                        }
                        return null;
                      }));
    }
  }

  @Test
  public void theTempNotificationPromotesTheServiceWithTheMediaPlaybackType() {
    Application app = ApplicationProvider.getApplicationContext();
    RecordingService recording = new RecordingService(app);
    NotificationUpdateDaemon.showTempNotification(recording.service);
    assertEquals(1, recording.foregroundCalls.size());
    Object[] args = recording.foregroundCalls.get(0);
    assertEquals(Constants.JOB_ID_MUSIC_PLAYBACK_SERVICE, args[0]);
    Notification notification = (Notification) args[1];
    assertNotNull(notification);
    assertEquals(
        "Player starting...",
        notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
    assertTrue((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0);
    assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, args[2]);
  }

  @Test
  @Config(sdk = 28)
  public void olderAndroidPromotesWithoutAServiceType() {
    Application app = ApplicationProvider.getApplicationContext();
    RecordingService recording = new RecordingService(app);
    NotificationUpdateDaemon.showTempNotification(recording.service);
    assertEquals(2, recording.foregroundCalls.get(0).length);
  }

  @Test
  public void aRefusedPromotionIsSwallowed() {
    Application app = ApplicationProvider.getApplicationContext();
    RecordingService recording = new RecordingService(app);
    recording.fail = true;
    NotificationUpdateDaemon.showTempNotification(recording.service);
    assertEquals(1, recording.foregroundCalls.size());
  }
}
