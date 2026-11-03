/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

/**
 * The stdout line an IceBridge child prints once its control API is listening, naming the port it
 * bound.
 *
 * <p>A launcher that picks a free port, releases it and hands the number to the child races every
 * other process for that port until the child binds it. The launcher instead starts the child with
 * {@code --control-http-port 0}, so the child's own bind picks the port, and reads the port from
 * this line (frostwire/frostwire#1478). The control API cannot report its own port: a client needs
 * the port to reach it.
 *
 * <p>The child and the launcher ship in different jars ({@code icebridge.jar} and {@code
 * frostwire.jar}), so this class must stay out of the daemon-only exclusions in {@code
 * desktop/build.gradle}.
 */
public final class IceBridgeControlPortAnnouncement {

  /** Starts the announcement line; the bound port follows it in decimal. */
  public static final String PREFIX = "ICEBRIDGE_CONTROL_PORT=";

  private IceBridgeControlPortAnnouncement() {}

  /** The announcement line for {@code port}, without a line terminator. */
  public static String line(int port) {
    if (port <= 0 || port > 65535) {
      throw new IllegalArgumentException("control port out of range: " + port);
    }
    return PREFIX + port;
  }

  /**
   * The port from the first announcement line in {@code output}, or 0 when no complete announcement
   * line is there yet. Only lines ended by {@code '\n'} count, so a line the child is still writing
   * is not read as a shorter port.
   *
   * @throws IllegalArgumentException if a complete announcement line does not name a valid port
   */
  public static int parse(CharSequence output) {
    String text = output.toString();
    int start = 0;
    int end;
    while ((end = text.indexOf('\n', start)) >= 0) {
      int lineEnd = end > start && text.charAt(end - 1) == '\r' ? end - 1 : end;
      if (text.startsWith(PREFIX, start) && start + PREFIX.length() <= lineEnd) {
        String value = text.substring(start + PREFIX.length(), lineEnd);
        int port;
        try {
          port = Integer.parseInt(value);
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("malformed control port announcement: " + value, e);
        }
        if (port <= 0 || port > 65535) {
          throw new IllegalArgumentException("announced control port out of range: " + port);
        }
        return port;
      }
      start = end + 1;
    }
    return 0;
  }
}
