/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.event;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Process-wide, bounded, thread-safe sink for {@link IceBridgeEvent}s.
 *
 * <p>Backs the in-app IceBridge console and the {@code frostwire_logs_*} MCP tools, and is exposed
 * by the IceBridge control API as {@code GET /events} so a remote relay can be inspected too.
 *
 * <p>Design:
 *
 * <ul>
 *   <li>Fixed-capacity ring buffer (oldest evicted first) — memory is bounded regardless of traffic.
 *   <li>{@link #enabled} is a volatile fast-path; emitters check it and skip all formatting when no
 *       one is observing, so disabled logging costs almost nothing.
 *   <li>Listeners are notified outside the lock; a slow listener cannot stall producers.
 * </ul>
 *
 * <p>All public methods are safe to call from any thread.
 */
public final class IceBridgeEventLog {

  /** Default ring-buffer capacity; enough for a long debugging session, bounded in memory. */
  public static final int DEFAULT_CAPACITY = 5000;

  private static final IceBridgeEventLog INSTANCE = new IceBridgeEventLog(DEFAULT_CAPACITY);

  private final ArrayDeque<IceBridgeEvent> events = new ArrayDeque<>();
  private final int capacity;
  private final CopyOnWriteArrayList<Consumer<IceBridgeEvent>> listeners =
      new CopyOnWriteArrayList<>();
  private volatile boolean enabled = true;

  IceBridgeEventLog(int capacity) {
    this.capacity = Math.max(1, capacity);
  }

  public static IceBridgeEventLog instance() {
    return INSTANCE;
  }

  public boolean isEnabled() {
    return enabled;
  }

  /** Disabling makes {@link #record} a no-op; used to keep headless/idle costs at zero. */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public void record(IceBridgeEvent event) {
    if (event == null || !enabled) {
      return;
    }
    synchronized (this) {
      if (events.size() >= capacity) {
        events.pollFirst();
      }
      events.addLast(event);
    }
    for (Consumer<IceBridgeEvent> listener : listeners) {
      try {
        listener.accept(event);
      } catch (Throwable ignored) {
        // A misbehaving observer must never break the producer.
      }
    }
  }

  /**
   * Filtered query, newest first.
   *
   * @param minLevel lowest level to include (null = all)
   * @param categories category whitelist (null/empty = all)
   * @param text case-insensitive substring over the message (null/empty = any)
   * @param peerPubSubstr case-insensitive substring over the peer public key hex (null/empty = any)
   * @param sinceMs only events at or after this wall-clock time (0 = all)
   * @param limit maximum results (clamped to the buffer capacity)
   */
  public List<IceBridgeEvent> query(
      IceBridgeEvent.Level minLevel,
      Set<IceBridgeEvent.Category> categories,
      String text,
      String peerPubSubstr,
      long sinceMs,
      int limit) {
    int max = limit <= 0 ? capacity : Math.min(limit, capacity);
    List<IceBridgeEvent> out = new ArrayList<>();
    synchronized (this) {
      var it = events.descendingIterator();
      while (it.hasNext() && out.size() < max) {
        IceBridgeEvent e = it.next();
        if (matches(e, minLevel, categories, text, peerPubSubstr, sinceMs)) {
          out.add(e);
        }
      }
    }
    return out;
  }

  /** Most recent {@code n} events, newest first. */
  public List<IceBridgeEvent> tail(int n) {
    return query(null, null, null, null, 0, n);
  }

  public void clear() {
    synchronized (this) {
      events.clear();
    }
  }

  public synchronized int size() {
    return events.size();
  }

  public void addListener(Consumer<IceBridgeEvent> listener) {
    if (listener != null) {
      listeners.add(listener);
    }
  }

  public void removeListener(Consumer<IceBridgeEvent> listener) {
    listeners.remove(listener);
  }

  public static int capacity() {
    return INSTANCE.capacity;
  }

  /** Shared filter predicate so the console and MCP tools agree on semantics. */
  public static boolean matches(
      IceBridgeEvent e,
      IceBridgeEvent.Level minLevel,
      Set<IceBridgeEvent.Category> categories,
      String text,
      String peerPubSubstr,
      long sinceMs) {
    if (e == null) {
      return false;
    }
    if (minLevel != null && e.level().ordinal() < minLevel.ordinal()) {
      return false;
    }
    if (categories != null
        && !categories.isEmpty()
        && !categories.contains(e.category())) {
      return false;
    }
    if (sinceMs > 0 && e.timestampMs() < sinceMs) {
      return false;
    }
    if (text != null && !text.isEmpty()
        && !e.message().toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
      return false;
    }
    if (peerPubSubstr != null && !peerPubSubstr.isEmpty()
        && !e.peerPub().toLowerCase(Locale.ROOT).contains(peerPubSubstr.toLowerCase(Locale.ROOT))) {
      return false;
    }
    return true;
  }

  /** Parses a comma-separated category list, ignoring unknown names. */
  public static Set<IceBridgeEvent.Category> parseCategories(String csv) {
    Set<IceBridgeEvent.Category> out = EnumSet.noneOf(IceBridgeEvent.Category.class);
    if (csv == null || csv.isBlank()) {
      return out;
    }
    for (String part : csv.split(",")) {
      String name = part.trim().toUpperCase(Locale.ROOT);
      if (name.isEmpty()) {
        continue;
      }
      try {
        out.add(IceBridgeEvent.Category.valueOf(name));
      } catch (IllegalArgumentException ignored) {
        // Unknown category names are ignored so callers can pass user text safely.
      }
    }
    return Collections.unmodifiableSet(out);
  }

  /** Parses a level name, defaulting to {@link IceBridgeEvent.Level#DEBUG} (include everything). */
  public static IceBridgeEvent.Level parseLevel(String name) {
    if (name == null || name.isBlank()) {
      return IceBridgeEvent.Level.DEBUG;
    }
    try {
      return IceBridgeEvent.Level.valueOf(name.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return IceBridgeEvent.Level.DEBUG;
    }
  }
}
