/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.core;

/** Port preference policy shared by the settings UI and the embedded relay stack. */
public final class IceBridgePortPreferences {

  private IceBridgePortPreferences() {}

  /** Returns -1 for invalid input; zero is allowed only for kernel-selected rUDP ports. */
  public static int parsePort(Object value, boolean rudp) {
    if (value == null) {
      return -1;
    }
    try {
      int port = Integer.parseInt(value.toString().trim());
      return port >= (rudp ? 0 : 1) && port <= 65535 ? port : -1;
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  public static int configuredPort(Object value, boolean rudp, int fallback) {
    int port = parsePort(value, rudp);
    return port >= 0 ? port : fallback;
  }

  // A later explicit choice of 6889 must survive subsequent app starts.
  static String migrateLegacyRudpPort(String value, boolean migrated) {
    return !migrated && parsePort(value, true) == 6889 ? "0" : value;
  }
}
