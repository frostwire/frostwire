/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Intent;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.core.TellurideCourier;
import com.frostwire.android.gui.AndroidSearchEngines;
import com.frostwire.android.gui.MainApplication;
import com.frostwire.android.search.AndroidRelayStack;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.engines.SearchEngine;
import com.frostwire.search.engines.SearchEngineID;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/**
 * Engine is a process-wide singleton whose flags (shut down, disconnected, pending start) carry
 * over from call to call, so its lifecycle is exercised as one story, in a Robolectric sandbox (SDK
 * 32) no other test shares. The foreground service is a mock.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 32, application = Application.class)
public class EngineTest {

  @Test
  public void theEngineLifecycleFollowsTheForegroundService() {
    Application app = ApplicationProvider.getApplicationContext();
    Engine engine = Engine.instance();
    assertSame(engine, Engine.instance());
    List<Runnable> misc = new ArrayList<>();
    EngineForegroundService service = mock(EngineForegroundService.class);
    SearchEngine distributed = mock(SearchEngine.class);
    try (MockedStatic<MainApplication> main = mockStatic(MainApplication.class);
        MockedStatic<SystemUtils> system = mockStatic(SystemUtils.class);
        MockedStatic<AndroidRelayStack> relay = mockStatic(AndroidRelayStack.class);
        MockedStatic<EngineForegroundService> services = mockStatic(EngineForegroundService.class);
        MockedStatic<TellurideCourier> telluride = mockStatic(TellurideCourier.class);
        MockedStatic<AndroidSearchEngines> engines = mockStatic(AndroidSearchEngines.class)) {
      main.when(MainApplication::context).thenReturn(app);
      system.when(() -> SystemUtils.isAppInForeground(any())).thenReturn(true);
      system
          .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
          .thenAnswer(
              call -> {
                misc.add(call.getArgument(1));
                return null;
              });
      relay.when(AndroidRelayStack::isNetworkAllowed).thenReturn(true);
      services.when(EngineForegroundService::getInstance).thenReturn(null);
      engines
          .when(() -> AndroidSearchEngines.byId(SearchEngineID.DISTRIBUTED_ID))
          .thenReturn(distributed);

      // a fresh engine with no service reports an invalid state and never claims to be running
      assertEquals(IEngineService.STATE_INVALID, engine.getState());
      assertFalse(engine.isStarted());
      assertFalse(engine.isStarting());
      assertFalse(engine.isStopped());
      assertFalse(engine.isStopping());
      assertFalse(engine.isDisconnected());
      assertFalse(engine.wasShutdown());
      assertNull(engine.getMediaPlayer());
      assertNotNull(engine.getThreadPool());
      assertSame(app, engine.getApplication());
      engine.notifyDownloadFinished("nothing to notify", new File("/x"));

      // the app is not in the foreground yet: Android 12+ would refuse the service, so it waits
      system.when(() -> SystemUtils.isAppInForeground(any())).thenReturn(false);
      engine.onApplicationCreate(app);
      misc.get(0).run();
      assertNull(Shadows.shadowOf(app).getNextStartedService());
      misc.clear();
      system.when(() -> SystemUtils.isAppInForeground(any())).thenReturn(true);

      // application start posts the service start, which promotes through startForegroundService
      engine.onApplicationCreate(app);
      assertEquals(1, misc.size());
      misc.get(0).run();
      assertTrue(engine.getMediaPlayer() instanceof ApolloMediaPlayer);
      Intent started = Shadows.shadowOf(app).getNextStartedService();
      assertEquals(EngineForegroundService.class.getName(), started.getComponent().getClassName());

      // an offline engine remembers it was disconnected instead of starting
      relay.when(AndroidRelayStack::isNetworkAllowed).thenReturn(false);
      engine.startServices();
      assertTrue(engine.isDisconnected());
      assertNull(Shadows.shadowOf(app).getNextStartedService());
      // back online without a live service: the start is remembered and the service requested
      relay.when(AndroidRelayStack::isNetworkAllowed).thenReturn(true);
      engine.resumeServicesIfDisconnected();
      assertFalse(engine.isDisconnected());
      assertNotNull(Shadows.shadowOf(app).getNextStartedService());
      engine.resumeServicesIfDisconnected(); // nothing disconnected any more

      // the service comes up: the remembered start is delivered to it
      when(service.acceptsStarts()).thenReturn(true);
      engine.onForegroundServiceCreated(null);
      engine.onForegroundServiceCreated(service);
      verify(service).startServices(false);

      // with a live service, state questions and notifications are delegated to it
      services.when(EngineForegroundService::getInstance).thenReturn(service);
      when(service.getState()).thenReturn(IEngineService.STATE_STARTED);
      when(service.isStarted()).thenReturn(true);
      when(service.isStarting()).thenReturn(true);
      when(service.isStopped()).thenReturn(true);
      when(service.isStopping()).thenReturn(true);
      when(service.isDisconnected()).thenReturn(true);
      assertEquals(IEngineService.STATE_STARTED, engine.getState());
      assertTrue(engine.isStarted());
      assertTrue(engine.isStarting());
      assertTrue(engine.isStopped());
      assertTrue(engine.isStopping());
      assertTrue(engine.isDisconnected());
      File file = new File("/storage/emulated/0/Download/FrostWire/a.mp3");
      engine.notifyDownloadFinished("a.mp3", file, "aa11");
      verify(service).notifyDownloadFinished("a.mp3", file, "aa11");
      engine.notifyDownloadFinished("b.mp3", file);
      verify(service).notifyDownloadFinished("b.mp3", file, null);
      engine.startServices();
      verify(service, times(2)).startServices(false);

      // a service that refuses starts is not used
      when(service.acceptsStarts()).thenReturn(false);
      assertEquals(IEngineService.STATE_INVALID, engine.getState());
      when(service.acceptsStarts()).thenReturn(true);

      // search readiness: nothing to do when already ready
      when(distributed.isReady()).thenReturn(true);
      relay.when(AndroidRelayStack::isParticipationEnabled).thenReturn(true);
      engine.ensureDistributedSearchReady(0);
      verify(service, never()).ensureRelayStack(anyBoolean(), any());
      // not ready: the relay stack is asked to start and the wait gives up at the deadline
      when(distributed.isReady()).thenReturn(false);
      when(service.isRelayStackRunning()).thenReturn(false);
      doAnswer(
              call -> {
                ((Runnable) call.getArgument(1)).run();
                return null;
              })
          .when(service)
          .ensureRelayStack(anyBoolean(), any());
      engine.ensureDistributedSearchReady(0);
      verify(service).ensureRelayStack(anyBoolean(), any());
      // not ready at first, ready after a short wait: the engine polls until the deadline
      relay.when(AndroidRelayStack::isParticipationEnabled).thenReturn(true);
      when(distributed.isReady()).thenReturn(false, false, true);
      engine.ensureDistributedSearchReady(2_000);
      // participation switched off: nothing to wait for
      relay.when(AndroidRelayStack::isParticipationEnabled).thenReturn(false);
      engine.ensureDistributedSearchReady(10_000);

      // an explicit stop reaches the service, remembers the shutdown, and stops the service
      engine.stopServices(true);
      telluride.verify(TellurideCourier::abortCurrentQuery);
      verify(service).stopServices(true);
      assertTrue(engine.wasShutdown());
      assertNotNull(Shadows.shadowOf(app).getNextStoppedService());
      // a later disconnect broadcast does not turn the explicit stop into an auto-resume
      engine.shutdown();
      engine.stopServices(true);
      verify(service, times(1)).stopServices(true);
      engine.ensureDistributedSearchReady(10_000);

      // starting again after a shutdown tells the service it is a restart
      engine.startServices();
      verify(service).startServices(true);
      assertFalse(engine.wasShutdown());

      // the service going away clears the engine's reference and restarts a pending start
      services.when(EngineForegroundService::getInstance).thenReturn(null);
      engine.onForegroundServiceDestroyed(service);
      engine.startServices();
      assertNotNull(Shadows.shadowOf(app).getNextStartedService());
      engine.onForegroundServiceDestroyed(service);
    }
  }

  @Test
  public void aPythonRuntimeThatCannotStartIsRetriedLater() {
    List<Runnable> misc = new ArrayList<>();
    List<Long> delays = new ArrayList<>();
    try (MockedStatic<SystemUtils> system = mockStatic(SystemUtils.class)) {
      system
          .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
          .thenAnswer(
              call -> {
                misc.add(call.getArgument(1));
                return null;
              });
      system
          .when(() -> SystemUtils.postToHandlerDelayed(any(), any(Runnable.class), anyLong()))
          .thenAnswer(
              call -> {
                delays.add(call.getArgument(2));
                return null;
              });
      // on the UI thread the start hops to a background handler
      system.when(SystemUtils::isUIThread).thenReturn(true);
      Engine.startPython();
      assertEquals(1, misc.size());
      // there the Python runtime cannot be created in a unit test: the start is retried in 10s
      system.when(SystemUtils::isUIThread).thenReturn(false);
      Engine.startPython();
      assertEquals(List.of(10000L), delays);
    }
  }
}
