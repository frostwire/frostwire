/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 * Licensed under GPL v3. See LICENSE file.
 */
package com.limegroup.gnutella.gui;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Keeps startup and public endpoint wiring behind the child's successful UDP bind. */
public final class IceBridgeStartup {
  private IceBridgeStartup() {}

  @FunctionalInterface
  public interface StartChild {
    boolean start() throws IOException, InterruptedException;
  }

  /** Returns zero on failed/cancelled startup; a healthy local child must report its bound port. */
  public static int startLocal(StartChild child, IntSupplier boundPort, BooleanSupplier active)
      throws IOException, InterruptedException {
    if (!active.getAsBoolean() || !child.start() || !active.getAsBoolean()) return 0;
    int port = boundPort.getAsInt();
    if (port <= 0 || port > 65535) {
      throw new IllegalStateException("Healthy IceBridge child did not report a bound UDP port");
    }
    return port;
  }

  /** Remote auto mode has no local endpoint to announce. Check cancellation between services. */
  public static void announce(int port, BooleanSupplier active, IntConsumer... services) {
    if (port <= 0 || port > 65535) return;
    for (IntConsumer service : services) {
      if (!active.getAsBoolean()) return;
      service.accept(port);
    }
  }

  /** Environment overrides select the requested bind port without rewriting the saved setting. */
  public static int requestedPort(int configuredPort, String environmentPort) {
    if (environmentPort != null && !environmentPort.isEmpty()) {
      try {
        int port = Integer.parseInt(environmentPort);
        if (port >= 0 && port <= 65535) return port;
      } catch (NumberFormatException ignored) {
      }
    }
    return configuredPort;
  }
}
