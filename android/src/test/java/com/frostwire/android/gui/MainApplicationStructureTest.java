/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Structural tests for MainApplication. Pure JVM — no Robolectric, no Android framework. */
public class MainApplicationStructureTest {

  @Test
  public void mainApplication_doesNotExtendMultiDexApplication() {
    Class<?> cls = MainApplication.class.getSuperclass();
    while (cls != null && cls != Object.class) {
      assertTrue(
          "MainApplication must NOT extend MultiDexApplication (minSdk=26)",
          !cls.getName().equals("androidx.multidex.MultiDexApplication"));
      cls = cls.getSuperclass();
    }
  }

  /** frostwire/frostwire#1388: WorkManager is gone; nothing may configure or start it again. */
  @Test
  public void mainApplication_isNoLongerAWorkManagerConfigurationProvider() {
    for (Class<?> iface : MainApplication.class.getInterfaces()) {
      assertFalse(iface.getName().startsWith("androidx.work."));
    }
  }
}
