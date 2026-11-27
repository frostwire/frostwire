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
import android.app.Service;
import android.content.Intent;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.search.AndroidRelayStack;
import com.frostwire.android.util.SystemUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

/** Android 14: no custom notification views, background sticky restarts are refused. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class EngineForegroundServiceModernAndroidTest {
  private Engine engine;
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
    ConfigurationManager config = mock(ConfigurationManager.class);
    when(config.getBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED)).thenReturn(true);
    system = mockStatic(SystemUtils.class);
    system.when(SystemUtils::isPrimaryExternalStorageMounted).thenReturn(true);
    engineStatic = mockStatic(Engine.class);
    engineStatic.when(Engine::instance).thenReturn(engine);
    configStatic = mockStatic(ConfigurationManager.class);
    configStatic.when(ConfigurationManager::instance).thenReturn(config);
    transfersStatic = mockStatic(TransferManager.class);
    transfersStatic.when(TransferManager::instance).thenReturn(mock(TransferManager.class));
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

  @Test
  public void aBackgroundStickyRestartIsStoppedWithoutPromotion() {
    system.when(() -> SystemUtils.isAppInForeground(any())).thenReturn(false);
    assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 1));
    assertTrue(Shadows.shadowOf(service).isStoppedBySelf());
    assertFalse(service.acceptsStarts());
    verify(engine, never()).onForegroundServiceCreated(any());
  }

  @Test
  public void aStartFromTheForegroundIsPromotedWithASimpleNotification() {
    system.when(() -> SystemUtils.isAppInForeground(any())).thenReturn(true);
    assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 1));
    assertTrue(service.acceptsStarts());
    assertEquals(
        Service.START_STICKY,
        service.onStartCommand(new Intent(service, EngineForegroundService.class), 0, 2));
    assertFalse(Shadows.shadowOf(service).isForegroundStopped());
  }
}
