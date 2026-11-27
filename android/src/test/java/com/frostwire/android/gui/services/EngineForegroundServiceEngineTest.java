/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Intent;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.DataStoreManager;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.libtorrent.TestEngine;
import com.frostwire.android.search.AndroidRelayStack;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

/**
 * The foreground service's work with libtorrent, over a real running engine: resuming it when the
 * service starts, pausing it when the service stops, and the notified-hashes storage the service
 * keeps. The work the service posts to its threads is collected and run by the test.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class EngineForegroundServiceEngineTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private final List<Runnable> downloaderWork = new ArrayList<>();
  private final List<Runnable> highPriorityWork = new ArrayList<>();
  private final List<Runnable> miscWork = new ArrayList<>();
  private TestEngine local;
  private Engine engine;
  private TransferManager transfers;
  private MockedStatic<SystemUtils> system;
  private MockedStatic<Engine> engineStatic;
  private MockedStatic<ConfigurationManager> configStatic;
  private MockedStatic<TransferManager> transfersStatic;
  private MockedStatic<AndroidRelayStack> relay;
  private MockedStatic<DataStoreManager> stores;
  private ServiceController<EngineForegroundService> controller;
  private EngineForegroundService service;

  @Before
  public void setUp() throws Exception {
    local = TestEngine.start(tmp.newFolder("engine").toPath());
    engine = mock(Engine.class);
    transfers = mock(TransferManager.class);
    ConfigurationManager config = mock(ConfigurationManager.class);
    when(config.getBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED)).thenReturn(true);
    system = mockStatic(SystemUtils.class);
    system.when(SystemUtils::isPrimaryExternalStorageMounted).thenReturn(true);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            call -> {
              Runnable work = call.getArgument(1);
              switch ((SystemUtils.HandlerThreadName) call.getArgument(0)) {
                case DOWNLOADER:
                  downloaderWork.add(work);
                  break;
                case HIGH_PRIORITY:
                  highPriorityWork.add(work);
                  break;
                default:
                  miscWork.add(work);
              }
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
    stores = mockStatic(DataStoreManager.class);
    controller = Robolectric.buildService(EngineForegroundService.class);
    service = controller.create().get();
    service.onStartCommand(new Intent(service, EngineForegroundService.class), 0, 1);
  }

  @After
  public void tearDown() {
    controller.destroy();
    stores.close();
    relay.close();
    transfersStatic.close();
    configStatic.close();
    engineStatic.close();
    system.close();
    local.close();
  }

  private static void runAll(List<Runnable> work) {
    for (Runnable task : new ArrayList<>(work)) {
      task.run();
    }
    work.clear();
  }

  @Test
  public void startingTheServiceResumesTheRunningEngineAndRestoresTheTransfers() {
    local.engine.pause();
    assertTrue(local.engine.isPaused());
    assertTrue(service.isStarting());
    int queued = downloaderWork.size();
    // already starting: asking again queues nothing
    service.startServices(false);
    assertEquals(queued, downloaderWork.size());

    runAll(downloaderWork);

    assertFalse("resumed by the start task", local.engine.isPaused());
    verify(transfers).ensureTorrentsRestored();
    verify(transfers).forceReannounceTorrents();
    verify(transfers, never()).reset();
    assertTrue(service.isStarted());
  }

  @Test
  public void startingAfterAShutdownStartsTheStoppedEngineAgainAndResetsTheTransferList()
      throws Exception {
    runAll(downloaderWork);
    assertTrue(service.isStarted());
    // the engine was shut down: libtorrent's session is gone and the service is not started
    local.engine.stop();
    assertFalse(local.engine.isRunning());
    java.lang.reflect.Method updateState =
        EngineForegroundService.class.getDeclaredMethod("updateState", byte.class);
    updateState.setAccessible(true);
    updateState.invoke(service, IEngineService.STATE_UNSTARTED);

    service.startServices(true);
    runAll(downloaderWork);

    verify(transfers).reset();
    TestEngine.await(local.engine::isRunning, "the engine started again");
    assertFalse(local.engine.isPaused());
    assertTrue(service.isStarted());
  }

  @Test
  public void startTaskThatLostItsGenerationOrNetworkLeavesTheEngineAlone() {
    local.engine.pause();
    relay.when(AndroidRelayStack::isNetworkAllowed).thenReturn(false);

    runAll(downloaderWork);

    assertTrue("the start task gave up without the network", local.engine.isPaused());
    verify(transfers, never()).ensureTorrentsRestored();
  }

  @Test
  public void aNetworkThatGoesAwayWhileTheEngineIsResumingPausesItAgain() {
    local.engine.pause();
    // allowed when the task starts, gone right after the engine was resumed
    relay.when(AndroidRelayStack::isNetworkAllowed).thenReturn(true, false);

    runAll(downloaderWork);

    assertTrue("paused again", local.engine.isPaused());
    verify(transfers, never()).ensureTorrentsRestored();
    assertFalse(service.isStarted());
  }

  @Test
  public void stoppingTheServicePausesTheEngineAndTellsTheTransferList() {
    assertFalse(local.engine.isPaused());
    downloaderWork.clear();

    service.stopServices(true);
    assertTrue(service.isStopping());
    runAll(downloaderWork);

    verify(transfers).onShutdown(true);
    assertTrue(local.engine.isPaused());
    assertTrue(service.isDisconnected());
  }

  @Test
  public void stoppingTheServiceWithoutADisconnectEndsStopped() {
    downloaderWork.clear();

    service.stopServices(false);
    runAll(downloaderWork);

    verify(transfers).onShutdown(false);
    assertEquals(IEngineService.STATE_STOPPED, service.getState());
  }

  @Test
  public void theNotifiedStorageIsCreatedInTheBackgroundAndRemembersFinishedTransfers() {
    // NotifiedStorage is built on the high priority thread, with the real libtorrent bloom filter
    runAll(highPriorityWork);
    stores.when(() -> DataStoreManager.getString(any(), any(), any())).thenReturn(null);
    String hash = "0123456789abcdef0123456789abcdef01234567";

    java.io.File saved = new java.io.File(tmp.getRoot(), "downloads/a.mp3");
    service.notifyDownloadFinished("a", saved, hash);
    // the same transfer finishing again is not announced twice
    service.notifyDownloadFinished("a", saved, hash);

    stores.verify(() -> DataStoreManager.putString(any(), any(), any()));
  }

  @Test
  public void theEnginesHomeFolderIsWhatTheRelayStackStartsFrom() {
    runAll(downloaderWork);
    relay.when(AndroidRelayStack::isParticipationEnabled).thenReturn(true);
    miscWork.clear();

    service.ensureRelayStack(true, null);
    runAll(miscWork);

    relay.verify(
        () ->
            AndroidRelayStack.start(
                any(),
                org.mockito.ArgumentMatchers.eq(local.ctx.homeDir),
                any(BTEngine.class),
                any(),
                any()));
  }
}
