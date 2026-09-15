/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import java.util.Locale;

/**
 * Helpers for choosing the address FrostWire advertises to the IceBridge mesh.
 *
 * <p>A host with virtual adapters (VM bridges, VPN tunnels, container bridges) must never advertise
 * those addresses: peers and relays outside that virtual network cannot reach them. Prefer the
 * address the OS uses for the default route; only fall back to interface enumeration, and skip
 * virtual-looking names there.
 */
public final class NetworkAdvertising {
  private NetworkAdvertising() {}

  /** Interface name prefixes that are virtual/overlay and must not be advertised. */
  private static final String[] VIRTUAL_INTERFACE_PREFIXES = {
    "bridge", "utun", "vmnet", "vboxnet", "docker", "br-", "veth", "awdl", "llw", "ap", "anpi",
    "gif", "stf", "tun", "tap", "p2p"
  };

  public static boolean isLikelyVirtualInterfaceName(String interfaceName) {
    if (interfaceName == null) {
      return false;
    }
    String name = interfaceName.toLowerCase(Locale.ROOT);
    for (String prefix : VIRTUAL_INTERFACE_PREFIXES) {
      if (name.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
