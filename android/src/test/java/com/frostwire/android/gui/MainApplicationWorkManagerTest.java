/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui;

import androidx.work.Configuration;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Structural tests for MainApplication WorkManager configuration.
 *
 * Verifies class structure without instantiating MainApplication or calling
 * getWorkManagerConfiguration() — both of which require Looper/Android runtime.
 *
 * Pure JVM — no Robolectric, no Android framework.
 */
public class MainApplicationWorkManagerTest {

    @Test
    public void mainApplication_implementsConfigurationProvider() {
        boolean implementsProvider = false;
        for (Class<?> iface : MainApplication.class.getInterfaces()) {
            if (iface.getName().equals("androidx.work.Configuration$Provider")) {
                implementsProvider = true;
                break;
            }
        }
        assertTrue("MainApplication must implement WorkManager Configuration.Provider",
                implementsProvider);
    }

    @Test
    public void mainApplication_hasGetWorkManagerConfigurationMethod() throws Exception {
        Method m = MainApplication.class.getDeclaredMethod("getWorkManagerConfiguration");
        assertNotNull(m);
        assertTrue("getWorkManagerConfiguration must be public",
                Modifier.isPublic(m.getModifiers()));
        assertTrue("getWorkManagerConfiguration must return Configuration",
                Configuration.class.isAssignableFrom(m.getReturnType()));
    }

    @Test
    public void mainApplication_doesNotExtendMultiDexApplication() {
        Class<?> cls = MainApplication.class.getSuperclass();
        while (cls != null && cls != Object.class) {
            assertTrue("MainApplication must NOT extend MultiDexApplication (minSdk=26)",
                    !cls.getName().equals("androidx.multidex.MultiDexApplication"));
            cls = cls.getSuperclass();
        }
    }
}
