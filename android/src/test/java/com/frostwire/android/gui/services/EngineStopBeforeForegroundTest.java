/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.gui.MainApplication;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

/**
 * frostwire/frostwire#1387: a network change could stop the engine service right after
 * startForegroundService(), before its onStartCommand called startForeground(). Android kills the
 * app for that (ForegroundServiceDidNotStartInTimeException; every event was on Android 12). The
 * stop is now left to the service, which checks wasShutdown() right after promoting itself.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, application = Application.class)
public class EngineStopBeforeForegroundTest {

  private Application app;
  private ShadowApplication shadowApp;
  private Engine engine;

  @Before
  public void setUp() throws Exception {
    app = ApplicationProvider.getApplicationContext();
    shadowApp = shadowOf(app);
    Field appContext = MainApplication.class.getDeclaredField("appContext");
    appContext.setAccessible(true);
    appContext.set(null, app);
    // A fresh Engine per test: Engine.instance() is a process-wide singleton.
    Constructor<Engine> constructor = Engine.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    engine = constructor.newInstance();
  }

  @Test
  public void aStopWhileTheStartIsPendingIsLeftToTheService() throws Exception {
    startEngineService();
    assertNotNull("the service was not started", shadowApp.getNextStartedService());

    engine.stopServices(true);

    assertNull("stopService() before startForeground() crashes the app", stoppedService());
    assertTrue("the service must see the stop once it is promoted", engine.wasShutdown());
  }

  @Test
  public void aStopAfterTheServiceIsGoneStopsItAsBefore() throws Exception {
    startEngineService();
    engine.onForegroundServiceDestroyed(null);

    engine.stopServices(true);

    Intent stopped = stoppedService();
    assertNotNull(stopped);
    assertEquals(EngineForegroundService.class.getName(), stopped.getComponent().getClassName());
  }

  @Test
  public void aStopWithoutAPendingStartStopsTheService() {
    engine.stopServices(true);

    assertNotNull(stoppedService());
  }

  private void startEngineService() throws Exception {
    Method start = Engine.class.getDeclaredMethod("startEngineService", Context.class);
    start.setAccessible(true);
    start.invoke(engine, app);
  }

  private Intent stoppedService() {
    return shadowApp.getNextStoppedService();
  }
}
