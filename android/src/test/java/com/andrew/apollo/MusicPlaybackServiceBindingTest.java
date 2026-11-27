/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.IBinder;
import android.os.Looper;
import androidx.media3.session.MediaSession;
import androidx.test.core.app.ApplicationProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** How other components reach the service: binding, media sessions and broadcast actions. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicPlaybackServiceBindingTest {

  private ApolloServiceFixture fixture;
  private MusicPlaybackService service;

  @Before
  public void setUp() throws Exception {
    fixture = ApolloServiceFixture.start();
    FakeMediaStore.install();
    service = fixture.service;
    service.handleCommandIntent(new Intent());
    idle();
  }

  @After
  public void tearDown() throws Exception {
    fixture.stop();
  }

  private static void idle() {
    shadowOf(Looper.getMainLooper()).idle();
  }

  @Test
  public void bindingGivesTheCallerABinder() {
    IBinder binder = service.onBind(new Intent(MusicPlaybackService.SERVICECMD));

    assertNotNull(binder);
  }

  @Test
  public void aMediaControllerIsGivenTheServicesSession() {
    MediaSession.ControllerInfo controller = Mockito.mock(MediaSession.ControllerInfo.class);

    MediaSession session = service.onGetSession(controller);

    assertNotNull("the lock screen and Bluetooth devices control this session", session);
    assertSame(session, service.onGetSession(controller));
    assertEquals(session, service.getSessions().get(0));
  }

  @Test
  public void reboundingMakesTheRunningServiceTheCurrentInstance() {
    service.onRebind(new Intent());

    assertSame(service, MusicPlaybackService.getInstance());
    assertTrue(MusicPlaybackService.instanceReady());
  }

  @Test
  public void aBroadcastActionFromAnotherAppMovesThePlayer() {
    service.open(new long[] {1, 2, 3}, 0);
    idle();
    assertEquals(1, service.getAudioId());

    ApplicationProvider.<Application>getApplicationContext()
        .sendBroadcast(new Intent(MusicPlaybackService.NEXT_ACTION));
    idle();

    assertEquals(2, service.getAudioId());
  }
}
