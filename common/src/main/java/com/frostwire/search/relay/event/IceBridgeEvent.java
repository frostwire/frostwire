/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.event;

/**
 * One IceBridge/distributed-search event, suitable for an in-app console and for MCP log queries.
 *
 * <p>Immutable; safe to hand to the EDT or serialize to JSON. {@code peerPub} is a full hex public
 * key when the event concerns a specific peer, otherwise the empty string. Messages are already
 * redacted by the emitters (never tokens, keys, or full payloads).
 */
public final class IceBridgeEvent {

  public enum Level {
    DEBUG,
    INFO,
    WARN,
    ERROR
  }

  public enum Category {
    SEARCH,
    FORWARD,
    DIGEST,
    PEER,
    REGISTRY,
    RELAY,
    METRICS,
    ERROR,
    GENERAL
  }

  private final long timestampMs;
  private final Level level;
  private final Category category;
  private final String peerPub;
  private final String message;

  public IceBridgeEvent(
      long timestampMs, Level level, Category category, String peerPub, String message) {
    this.timestampMs = timestampMs;
    this.level = level != null ? level : Level.INFO;
    this.category = category != null ? category : Category.GENERAL;
    this.peerPub = peerPub != null ? peerPub : "";
    this.message = message != null ? message : "";
  }

  public static IceBridgeEvent of(Level level, Category category, String peerPub, String message) {
    return new IceBridgeEvent(System.currentTimeMillis(), level, category, peerPub, message);
  }

  public long timestampMs() {
    return timestampMs;
  }

  public Level level() {
    return level;
  }

  public Category category() {
    return category;
  }

  public String peerPub() {
    return peerPub;
  }

  public String message() {
    return message;
  }

  /** Short peer label for display (first 12 hex chars) or empty. */
  public String shortPeerPub() {
    return peerPub.length() > 12 ? peerPub.substring(0, 12) : peerPub;
  }
}
