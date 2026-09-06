/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.services;

import android.app.Application;
import com.frostwire.android.core.TellurideCourier;
import com.frostwire.android.search.AndroidRelayStack;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Constructor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class EngineParticipationTest {
    @Test
    public void networkRecoveryDoesNotUndoExplicitStop() throws Exception {
        Engine engine = engine();
        EngineForegroundService service = mock(EngineForegroundService.class);
        when(service.acceptsStarts()).thenReturn(true);
        try (MockedStatic<EngineForegroundService> services = mockStatic(EngineForegroundService.class);
             MockedStatic<AndroidRelayStack> relay = mockStatic(AndroidRelayStack.class)) {
            services.when(EngineForegroundService::getInstance).thenReturn(service);
            relay.when(AndroidRelayStack::isNetworkAllowed).thenReturn(true);
            engine.shutdown();
            engine.stopServices(true);
            engine.resumeServicesIfDisconnected();
            engine.ensureDistributedSearchReady(1);
            assertTrue(engine.wasShutdown());
            assertFalse(engine.isDisconnected());
            verify(service).stopServices(false);
            verify(service, never()).startServices(anyBoolean());
        }
    }

    @Test
    public void networkPauseCanResumeButNeverReusesRetiredService() throws Exception {
        Engine engine = engine();
        EngineForegroundService old = mock(EngineForegroundService.class);
        EngineForegroundService replacement = mock(EngineForegroundService.class);
        when(replacement.acceptsStarts()).thenReturn(true);
        when(old.acceptsStarts()).thenReturn(true);
        try (MockedStatic<EngineForegroundService> services = mockStatic(EngineForegroundService.class);
             MockedStatic<AndroidRelayStack> relay = mockStatic(AndroidRelayStack.class);
             MockedStatic<TellurideCourier> courier = mockStatic(TellurideCourier.class)) {
            services.when(EngineForegroundService::getInstance).thenReturn(old);
            relay.when(AndroidRelayStack::isNetworkAllowed).thenReturn(true);
            engine.stopServices(true);
            assertTrue(engine.isDisconnected());
            when(old.acceptsStarts()).thenReturn(false);
            when(old.isStarted()).thenReturn(true);
            assertFalse(engine.isStarted());
            engine.resumeServicesIfDisconnected();
            verify(old, never()).startServices(anyBoolean());
            engine.onForegroundServiceCreated(replacement);
            verify(replacement).startServices(true);
            assertFalse(engine.wasShutdown());
        }
    }

    @Test
    public void disallowedNetworkPreventsStartBeforeServiceWork() throws Exception {
        Engine engine = engine();
        EngineForegroundService service = mock(EngineForegroundService.class);
        try (MockedStatic<EngineForegroundService> services = mockStatic(EngineForegroundService.class);
             MockedStatic<AndroidRelayStack> relay = mockStatic(AndroidRelayStack.class)) {
            services.when(EngineForegroundService::getInstance).thenReturn(service);
            relay.when(AndroidRelayStack::isNetworkAllowed).thenReturn(false);
            engine.startServices();
            assertTrue(engine.isDisconnected());
            verify(service, never()).startServices(anyBoolean());
        }
    }

    private static Engine engine() throws Exception {
        Constructor<Engine> constructor = Engine.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Engine engine = spy(constructor.newInstance());
        doReturn(mock(Application.class)).when(engine).getApplication();
        return engine;
    }
}
