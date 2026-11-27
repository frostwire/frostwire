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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.app.Service;
import android.content.Intent;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.ConfigurationRepository;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.search.AndroidRelayStack;
import com.frostwire.android.util.SystemUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNotificationManager;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class EngineForegroundServiceLifecycleTest {
  private final List<Runnable> engineWork = new ArrayList<>();
  private final List<Runnable> uiWork = new ArrayList<>();
  private Engine engine;
  private ConfigurationManager config;
  private TransferManager transfers;
  private MockedStatic<SystemUtils> system;
  private MockedStatic<Engine> engineStatic;
  private MockedStatic<ConfigurationManager> configStatic;
  private MockedStatic<TransferManager> transfersStatic;
  private MockedStatic<AndroidRelayStack> relay;
  private ServiceController<EngineForegroundService> controller;
  private EngineForegroundService service;

  @Before
  public void setUp() {
    engine = mock(Engine.class);
    config = mock(ConfigurationManager.class);
    transfers = mock(TransferManager.class);
    when(config.getBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED)).thenReturn(true);
    system = mockStatic(SystemUtils.class);
    system.when(SystemUtils::isPrimaryExternalStorageMounted).thenReturn(true);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            call -> {
              engineWork.add(call.getArgument(1));
              return null;
            });
    system
        .when(() -> SystemUtils.postToUIThread(any(Runnable.class)))
        .thenAnswer(
            call -> {
              uiWork.add(call.getArgument(0));
              return null;
            });
    engineStatic = mockStatic(Engine.class);
    engineStatic.when(Engine::instance).thenReturn(engine);
    configStatic = mockStatic(ConfigurationManager.class);
    configStatic.when(ConfigurationManager::instance).thenReturn(config);
    transfersStatic = mockStatic(TransferManager.class);
    transfersStatic.when(TransferManager::instance).thenReturn(transfers);
    relay = mockStatic(AndroidRelayStack.class);
    relay.when(AndroidRelayStack::isNetworkAllowed).thenReturn(true);
    controller = Robolectric.buildService(EngineForegroundService.class);
    service = controller.create().get();
  }

  @After
  public void tearDown() {
    controller.destroy();
    relay.close();
    transfersStatic.close();
    configStatic.close();
    engineStatic.close();
    system.close();
  }

  private int start(Intent intent) {
    return service.onStartCommand(intent, 0, 1);
  }

  @Test
  public void creatingTheServiceRegistersItAndListensForPreferences() {
    assertSame(service, EngineForegroundService.getInstance());
    verify(config)
        .registerOnPreferenceChange(any(ConfigurationRepository.OnPreferenceChangeListener.class));
    assertEquals(IEngineService.STATE_UNSTARTED, service.getState());
    assertNull(service.onBind(new Intent()));
    assertNull(service.getMediaPlayer());
    assertFalse(service.isRelayStackRunning());
    assertNull(service.getRelayStack());
    assertNotNull(service.getRelayStartupStatus());
    assertFalse("not promoted to the foreground yet", service.acceptsStarts());
  }

  @Test
  public void startCommandPromotesTheServiceAndStartsTheEngineWork() {
    Intent intent = new Intent(service, EngineForegroundService.class);
    assertEquals(Service.START_STICKY, start(intent));
    assertTrue(service.acceptsStarts());
    verify(engine).onForegroundServiceCreated(service);
    assertTrue(service.isStarting());
    assertFalse(service.isStarted());
    // the cancel-notifications task, the start of the engine and the daemon are queued
    assertFalse(engineWork.isEmpty());
    // starting again while starting changes nothing
    int queued = engineWork.size();
    service.startServices();
    assertEquals(queued, engineWork.size());
  }

  @Test
  public void aStickyRestartWithoutAnIntentIsNotRestartedAgain() {
    assertEquals(Service.START_NOT_STICKY, start(null));
    assertTrue(service.acceptsStarts());
  }

  @Test
  public void aShutdownIntentShutsTheEngineDownAndStopsTheService() {
    Intent shutdown = new Intent("com.frostwire.android.engine.SHUTDOWN");
    assertEquals(Service.START_NOT_STICKY, start(shutdown));
    verify(engine).shutdown();
    assertTrue(Shadows.shadowOf(service).isStoppedBySelf());
  }

  @Test
  public void aServiceStartedAfterAShutdownStopsItself() {
    when(engine.wasShutdown()).thenReturn(true);
    assertEquals(Service.START_NOT_STICKY, start(new Intent()));
    assertTrue(Shadows.shadowOf(service).isStoppedBySelf());
  }

  @Test
  public void startServicesIsSkippedWithoutTheTermsOrStorageOrNetwork() {
    start(new Intent());
    // restore to a not-starting state by using a fresh service
    controller.destroy();
    controller = Robolectric.buildService(EngineForegroundService.class);
    service = controller.create().get();
    service.startServices();
    assertEquals("not in the foreground yet", IEngineService.STATE_UNSTARTED, service.getState());

    start(new Intent());
    assertTrue(service.isStarting());

    controller.destroy();
    when(config.getBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED)).thenReturn(false);
    controller = Robolectric.buildService(EngineForegroundService.class);
    service = controller.create().get();
    start(new Intent());
    assertFalse("terms not accepted", service.isStarting());

    controller.destroy();
    when(config.getBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED)).thenReturn(true);
    system.when(SystemUtils::isPrimaryExternalStorageMounted).thenReturn(false);
    controller = Robolectric.buildService(EngineForegroundService.class);
    service = controller.create().get();
    start(new Intent());
    assertFalse("storage not mounted", service.isStarting());

    controller.destroy();
    system.when(SystemUtils::isPrimaryExternalStorageMounted).thenReturn(true);
    relay.when(AndroidRelayStack::isNetworkAllowed).thenReturn(false);
    controller = Robolectric.buildService(EngineForegroundService.class);
    service = controller.create().get();
    start(new Intent());
    assertFalse("network not allowed", service.isStarting());
  }

  @Test
  public void stoppingServicesMovesThroughStoppingAndIsIdempotent() {
    start(new Intent());
    engineWork.clear();
    service.stopServices(true);
    assertTrue(service.isStopping());
    assertFalse(service.isStarting());
    assertEquals("the engine pause is queued", 1, engineWork.size());
    service.stopServices(false);
    assertEquals("already stopping", 1, engineWork.size());
    assertFalse("a stopped service rejects further starts", service.acceptsStarts());
  }

  @Test
  public void aTimedOutForegroundServicePausesTheEngineAndStops() {
    service.onTimeout(1, 1);
    verify(engine).stopServices(true);
    assertTrue(Shadows.shadowOf(service).isStoppedBySelf());
  }

  @Test
  public void aTimeoutStopsTheServiceEvenWhenPausingFails() {
    doThrow(new IllegalStateException("engine gone")).when(engine).stopServices(anyBoolean());
    service.onTimeout(1, 1);
    assertTrue(Shadows.shadowOf(service).isStoppedBySelf());
  }

  @Test
  public void destroyingTheServiceUnregistersAndClearsTheInstance() {
    start(new Intent());
    controller.destroy();
    assertNull(EngineForegroundService.getInstance());
    verify(config)
        .unregisterOnPreferenceChange(
            any(ConfigurationRepository.OnPreferenceChangeListener.class));
    verify(engine).onForegroundServiceDestroyed(service);
    assertFalse(service.acceptsStarts());
    // tearDown destroys again; a second destroy must not throw
  }

  @Test
  public void networkPreferencesRestartOrStopTheEngineAndRelay() {
    ArgumentCaptor<ConfigurationRepository.OnPreferenceChangeListener> captor =
        ArgumentCaptor.forClass(ConfigurationRepository.OnPreferenceChangeListener.class);
    verify(config).registerOnPreferenceChange(captor.capture());
    ConfigurationRepository.OnPreferenceChangeListener listener = captor.getValue();

    relay.when(AndroidRelayStack::isNetworkAllowed).thenReturn(false);
    listener.onPreferenceChanged(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY);
    verify(engine).stopServices(true);

    relay.when(AndroidRelayStack::isNetworkAllowed).thenReturn(true);
    listener.onPreferenceChanged(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY);
    verify(engine).resumeServicesIfDisconnected();

    listener.onPreferenceChanged(Constants.PREF_KEY_ICEBRIDGE_ENABLED);
    listener.onPreferenceChanged("some.other.key");
    verify(engine, never()).shutdown();
  }

  @Test
  public void ensuringTheRelayStackReportsBackWhenParticipationIsOff() {
    start(new Intent());
    relay.when(AndroidRelayStack::isParticipationEnabled).thenReturn(false);
    Runnable done = mock(Runnable.class);
    uiWork.clear();
    service.ensureRelayStack(false, done);
    assertEquals(1, uiWork.size());
    uiWork.get(0).run();
    verify(done).run();
    service.ensureRelayStack(true, null);
  }

  @Test
  public void aRelayStartAttemptThatCannotReachTheEngineIsRecordedAsFailed() {
    start(new Intent());
    relay.when(AndroidRelayStack::isParticipationEnabled).thenReturn(true);
    engineWork.clear();
    Runnable done = mock(Runnable.class);
    service.ensureRelayStack(false, done);
    assertNotNull(service.getRelayStartupStatus());
    assertFalse(engineWork.isEmpty());
    for (Runnable r : new ArrayList<>(engineWork)) {
      r.run();
    }
    uiWork.forEach(Runnable::run);
    verify(done).run();
    assertFalse(service.isRelayStackRunning());
    // the startup tick on the main looper is harmless
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  }

  @Test
  public void aBusyRelayStartupAnswersTheCallerImmediately() {
    start(new Intent());
    relay.when(AndroidRelayStack::isParticipationEnabled).thenReturn(true);
    service.ensureRelayStack(false, null);
    Runnable done = mock(Runnable.class);
    uiWork.clear();
    service.ensureRelayStack(false, done);
    assertEquals(1, uiWork.size());
  }

  @Test
  public void aFinishedDownloadPostsANotificationOnce() {
    File file = new File("/storage/emulated/0/Download/FrostWire/a.mp3");
    when(transfers.getDownloadsToReview()).thenReturn(2);
    service.notifyDownloadFinished("a.mp3", file, "aa11");
    ShadowNotificationManager manager =
        Shadows.shadowOf(
            (android.app.NotificationManager)
                service.getSystemService(android.content.Context.NOTIFICATION_SERVICE));
    assertNotNull(manager.getNotification(Constants.NOTIFICATION_DOWNLOAD_TRANSFER_FINISHED));
    // vibration setting is read for the channel
    verify(config).vibrateOnFinishedDownload();
  }

  @Test
  public void aNotificationFailureWhilePostingIsSwallowed() {
    when(transfers.getDownloadsToReview()).thenThrow(new IllegalStateException("boom"));
    service.notifyDownloadFinished("a", new File("/a"), "aa11");
  }

  @Test
  public void theServiceNeverReportsStartedBeforeTheEngineHasRun() {
    assertFalse(service.isStarted());
    assertFalse(service.isStopped());
    assertFalse(service.isDisconnected());
  }
}
