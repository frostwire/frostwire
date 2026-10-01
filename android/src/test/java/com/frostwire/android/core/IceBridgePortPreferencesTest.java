/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Behavioral port policy tests without Android runtime, JNI, or socket dependencies. */
public class IceBridgePortPreferencesTest {

  @Test
  public void firstUpgradeReplacesLegacyDefaultWithAutomatic() {
    assertEquals("0", IceBridgePortPreferences.migrateLegacyRudpPort("6889", false));
    assertEquals("0", IceBridgePortPreferences.migrateLegacyRudpPort(" 6889 ", false));
    assertNull(IceBridgePortPreferences.migrateLegacyRudpPort(null, false));
  }

  @Test
  public void otherFixedPortsAndAutomaticArePreservedOnUpgrade() {
    for (String value : new String[] {"0", "1", "42000", "65535"}) {
      assertEquals(value, IceBridgePortPreferences.migrateLegacyRudpPort(value, false));
    }
  }

  @Test
  public void persistedMarkerPreservesLaterManualLegacyPort() {
    String upgraded = IceBridgePortPreferences.migrateLegacyRudpPort("6889", false);
    assertEquals("0", IceBridgePortPreferences.migrateLegacyRudpPort(upgraded, true));
    assertEquals("6889", IceBridgePortPreferences.migrateLegacyRudpPort("6889", true));
  }

  @Test
  public void malformedAndOutOfRangeRudpValuesFallBackToAutomatic() {
    for (String value : new String[] {null, "", " ", "auto", "1.5", "-1", "65536", "2147483648"}) {
      assertEquals(-1, IceBridgePortPreferences.parsePort(value, true));
      assertEquals(0, IceBridgePortPreferences.configuredPort(value, true, 0));
      assertEquals(6888, IceBridgePortPreferences.configuredPort(value, false, 6888));
    }
  }

  @Test
  public void zeroIsAcceptedOnlyForRudp() {
    assertEquals(0, IceBridgePortPreferences.parsePort(" 0 ", true));
    assertEquals(-1, IceBridgePortPreferences.parsePort("0", false));
    assertEquals(0, IceBridgePortPreferences.configuredPort("0", true, 0));
    assertEquals(6888, IceBridgePortPreferences.configuredPort("0", false, 6888));
  }

  @Test
  public void positivePortsRemainValidForBothTransports() {
    for (int port : new int[] {1, 6888, 6889, 42000, 65535}) {
      String value = " " + port + " ";
      assertEquals(port, IceBridgePortPreferences.parsePort(value, true));
      assertEquals(port, IceBridgePortPreferences.parsePort(value, false));
      assertEquals(port, IceBridgePortPreferences.configuredPort(value, true, 0));
      assertEquals(port, IceBridgePortPreferences.configuredPort(value, false, 6888));
    }
  }
}
