/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.event;

/**
 * Thin static facade over {@link IceBridgeEventLog} so relay/distributed-search code can emit
 * observable events without depending on the log's storage details.
 *
 * <p>Every method short-circuits when the log is disabled, so emitters can call these
 * unconditionally on hot paths without paying for message formatting. Callers must still avoid
 * concatenating expensive strings before the call; pass cheap parts.
 */
public final class IceBridgeEvents {

  private IceBridgeEvents() {}

  public static boolean enabled() {
    return IceBridgeEventLog.instance().isEnabled();
  }

  public static void search(String peerPub, String message) {
    emit(IceBridgeEvent.Level.INFO, IceBridgeEvent.Category.SEARCH, peerPub, message);
  }

  public static void forward(String peerPub, String message) {
    emit(IceBridgeEvent.Level.INFO, IceBridgeEvent.Category.FORWARD, peerPub, message);
  }

  public static void digest(String peerPub, String message) {
    emit(IceBridgeEvent.Level.INFO, IceBridgeEvent.Category.DIGEST, peerPub, message);
  }

  public static void peer(String peerPub, String message) {
    emit(IceBridgeEvent.Level.INFO, IceBridgeEvent.Category.PEER, peerPub, message);
  }

  public static void registry(String peerPub, String message) {
    emit(IceBridgeEvent.Level.INFO, IceBridgeEvent.Category.REGISTRY, peerPub, message);
  }

  public static void relay(String peerPub, String message) {
    emit(IceBridgeEvent.Level.INFO, IceBridgeEvent.Category.RELAY, peerPub, message);
  }

  public static void metrics(String message) {
    emit(IceBridgeEvent.Level.INFO, IceBridgeEvent.Category.METRICS, "", message);
  }

  public static void general(String message) {
    emit(IceBridgeEvent.Level.INFO, IceBridgeEvent.Category.GENERAL, "", message);
  }

  public static void warn(IceBridgeEvent.Category category, String peerPub, String message) {
    emit(IceBridgeEvent.Level.WARN, category, peerPub, message);
  }

  /** Records a failure. Never includes a stack trace; the message carries class + reason only. */
  public static void error(String message, Throwable t) {
    if (!enabled()) {
      return;
    }
    String detail =
        t == null
            ? message
            : message + " (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")";
    emit(IceBridgeEvent.Level.ERROR, IceBridgeEvent.Category.ERROR, "", detail);
  }

  private static void emit(
      IceBridgeEvent.Level level,
      IceBridgeEvent.Category category,
      String peerPub,
      String message) {
    IceBridgeEventLog log = IceBridgeEventLog.instance();
    if (!log.isEnabled()) {
      return;
    }
    log.record(IceBridgeEvent.of(level, category, peerPub, message));
  }
}
