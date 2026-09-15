/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NetworkAdvertisingTest {

  @Test
  void flagsVirtualInterfaces() {
    assertTrue(NetworkAdvertising.isLikelyVirtualInterfaceName("bridge100"));
    assertTrue(NetworkAdvertising.isLikelyVirtualInterfaceName("utun4"));
    assertTrue(NetworkAdvertising.isLikelyVirtualInterfaceName("vboxnet0"));
    assertTrue(NetworkAdvertising.isLikelyVirtualInterfaceName("vmnet8"));
    assertTrue(NetworkAdvertising.isLikelyVirtualInterfaceName("docker0"));
    assertTrue(NetworkAdvertising.isLikelyVirtualInterfaceName("awdl0"));
    assertTrue(NetworkAdvertising.isLikelyVirtualInterfaceName("tun0"));
    assertTrue(NetworkAdvertising.isLikelyVirtualInterfaceName("tap0"));
  }

  @Test
  void leavesPhysicalInterfacesAlone() {
    assertFalse(NetworkAdvertising.isLikelyVirtualInterfaceName("en0"));
    assertFalse(NetworkAdvertising.isLikelyVirtualInterfaceName("en1"));
    assertFalse(NetworkAdvertising.isLikelyVirtualInterfaceName("eth0"));
    assertFalse(NetworkAdvertising.isLikelyVirtualInterfaceName("wlan0"));
    assertFalse(NetworkAdvertising.isLikelyVirtualInterfaceName(null));
  }
}
