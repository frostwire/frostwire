/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.frostwire.android.core.ConfigurationManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** The platform AppSettings is a thin adapter over the ConfigurationManager. */
public class AndroidSettingsTest {

  private MockedStatic<ConfigurationManager> statics;
  private ConfigurationManager cm;
  private final AndroidSettings settings = new AndroidSettings();

  @Before
  public void setUp() {
    cm = mock(ConfigurationManager.class);
    statics = Mockito.mockStatic(ConfigurationManager.class);
    statics.when(ConfigurationManager::instance).thenReturn(cm);
  }

  @After
  public void tearDown() {
    statics.close();
  }

  @Test
  public void readsAreForwardedPerType() {
    when(cm.getString("s")).thenReturn("value");
    when(cm.getInt("i")).thenReturn(7);
    when(cm.getLong("l")).thenReturn(1L << 40);
    when(cm.getBoolean("b")).thenReturn(true);
    assertEquals("value", settings.string("s"));
    assertEquals(7, settings.int32("i"));
    assertEquals(1L << 40, settings.int64("l"));
    assertTrue(settings.bool("b"));
    assertFalse(settings.bool("other"));
  }

  @Test
  public void writesAreForwardedPerType() {
    settings.string("s", "v");
    settings.int32("i", 3);
    settings.int64("l", 4L);
    settings.bool("b", true);
    verify(cm).setString("s", "v");
    verify(cm).setInt("i", 3);
    verify(cm).setLong("l", 4L);
    verify(cm).setBoolean("b", true);
  }
}
