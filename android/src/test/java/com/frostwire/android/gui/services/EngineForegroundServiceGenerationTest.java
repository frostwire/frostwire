/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.services;

import android.app.Application;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.search.AndroidRelayStack;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class EngineForegroundServiceGenerationTest {
    @Test
    public void stoppedServiceRejectsAlreadyQueuedEngineAndRelayStarts() throws Exception {
        EngineForegroundService service = new EngineForegroundService();
        Field instance = EngineForegroundService.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previous = instance.get(null);
        instance.set(null, service);
        Field foreground = EngineForegroundService.class.getDeclaredField("foregroundReady");
        foreground.setAccessible(true);
        foreground.setBoolean(service, true);
        List<Runnable> work = new ArrayList<>();
        ConfigurationManager configuration = mock(ConfigurationManager.class);
        when(configuration.getBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED)).thenReturn(true);
        try (MockedStatic<SystemUtils> system = mockStatic(SystemUtils.class);
             MockedStatic<Engine> engine = mockStatic(Engine.class);
             MockedStatic<ConfigurationManager> config = mockStatic(ConfigurationManager.class);
             MockedStatic<AndroidRelayStack> relay = mockStatic(AndroidRelayStack.class)) {
            engine.when(Engine::instance).thenReturn(mock(Engine.class));
            config.when(ConfigurationManager::instance).thenReturn(configuration);
            relay.when(AndroidRelayStack::isNetworkAllowed).thenReturn(true);
            relay.when(AndroidRelayStack::isParticipationEnabled).thenReturn(true);
            system.when(SystemUtils::isPrimaryExternalStorageMounted).thenReturn(true);
            system.when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
                    .thenAnswer(call -> { work.add(call.getArgument(1)); return null; });
            service.startServices();
            service.startServices();
            assertEquals("Engine starts are coalesced", 1, work.size());
            service.ensureRelayStack(false, null);
            service.stopServices(false);
            work.get(0).run();
            work.get(1).run();
            // No BTEngine static mock: BTEngine.<clinit> requires the native
            // library, which the unit JVM cannot load. The generation guard is
            // proven by acceptsStarts() false and no further queued work below.
            assertFalse(service.acceptsStarts());
            int queued = work.size();
            service.startServices();
            assertEquals(queued, work.size());
        } finally {
            instance.set(null, previous);
        }
    }
}
