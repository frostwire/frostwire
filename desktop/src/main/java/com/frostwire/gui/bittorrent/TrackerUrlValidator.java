/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.bittorrent;

import java.util.Locale;

/** Strong validation for BitTorrent tracker announce URLs (http/https/udp). */
public final class TrackerUrlValidator {
  private TrackerUrlValidator() {}

  public static boolean isValidTrackerUrl(String url) {
    if (url == null) {
      return false;
    }
    String value = url.trim();
    if (value.isEmpty()) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
        return false;
      }
    }
    int schemeEnd = value.indexOf("://");
    if (schemeEnd <= 0) {
      return false;
    }
    String scheme = value.substring(0, schemeEnd).toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https") && !scheme.equals("udp")) {
      return false;
    }
    String rest = value.substring(schemeEnd + 3);
    int slash = rest.indexOf('/');
    String authority = slash < 0 ? rest : rest.substring(0, slash);
    String path = slash < 0 ? "" : rest.substring(slash);
    if (authority.isEmpty()) {
      return false;
    }
    int at = authority.lastIndexOf('@');
    String hostPort = at >= 0 ? authority.substring(at + 1) : authority;
    String host;
    if (hostPort.startsWith("[")) {
      int close = hostPort.indexOf(']');
      if (close < 0) {
        return false;
      }
      host = hostPort.substring(1, close);
      String after = hostPort.substring(close + 1);
      if (!after.isEmpty()) {
        if (!after.startsWith(":") || parsePort(after.substring(1)) < 0) {
          return false;
        }
      }
    } else {
      int colon = hostPort.lastIndexOf(':');
      if (colon >= 0) {
        host = hostPort.substring(0, colon);
        if (parsePort(hostPort.substring(colon + 1)) < 0) {
          return false;
        }
      } else {
        host = hostPort;
      }
    }
    if (host.isEmpty()) {
      return false;
    }
    // HTTP(S) announce URLs need a path (/announce); UDP works host:port only.
    if ((scheme.equals("http") || scheme.equals("https")) && path.length() < 2) {
      return false;
    }
    return true;
  }

  private static int parsePort(String port) {
    if (port == null || port.isEmpty() || port.length() > 5) {
      return -1;
    }
    int value = 0;
    for (int i = 0; i < port.length(); i++) {
      char c = port.charAt(i);
      if (c < '0' || c > '9') {
        return -1;
      }
      value = value * 10 + (c - '0');
      if (value > 65535) {
        return -1;
      }
    }
    return value >= 1 ? value : -1;
  }
}
