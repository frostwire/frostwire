/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import java.util.function.LongSupplier;

/** Single-flight startup, monotonic deadlines, and bounded retry state without owning resources. */
public final class RelayStartupTracker {
  public enum State {
    STOPPED,
    STARTING,
    DRAINING,
    RETRY_WAIT,
    FAILED,
    RUNNING
  }

  public enum Phase {
    QUEUED,
    IDENTITY,
    INDEX,
    KARMA,
    SERVER,
    TRANSPORT,
    DISCOVERY
  }

  public record Snapshot(State state, Phase phase, long seconds, String failure) {}

  private static final long START_TIMEOUT_MS = 90_000;
  private static final int MAX_ATTEMPTS = 3;
  private final LongSupplier clock;
  private long token;
  private long deadline;
  private long retryAt;
  private int attempts;
  private boolean busy;
  private State state = State.STOPPED;
  private Phase phase = Phase.QUEUED;
  private String failure = "";

  public RelayStartupTracker(LongSupplier clock) {
    this.clock = clock;
  }

  public synchronized long begin(boolean manual) {
    if (busy) return 0;
    if (!manual && state == State.RUNNING) return 0;
    if (manual) attempts = 0;
    if (attempts >= MAX_ATTEMPTS || (!manual && clock.getAsLong() < retryAt)) return 0;
    busy = true;
    attempts++;
    token++;
    deadline = clock.getAsLong() + START_TIMEOUT_MS;
    state = State.STARTING;
    phase = Phase.QUEUED;
    failure = "";
    return token;
  }

  public synchronized boolean permitted(long attempt) {
    expire();
    return token == attempt && (state == State.RUNNING || (busy && state == State.STARTING));
  }

  public synchronized void phase(long attempt, Phase phase) {
    if (permitted(attempt)) this.phase = phase;
  }

  public synchronized void failed(long attempt, Throwable error) {
    if (token == attempt && state != State.DRAINING) {
      failure = error.getClass().getSimpleName();
      // Missing runtime classes/native symbols are actionable diagnostics, not user data.
      if (error instanceof LinkageError && error.getMessage() != null) {
        String symbol = error.getMessage();
        failure += ": " + symbol.substring(0, Math.min(symbol.length(), 160));
      }
    }
  }

  public synchronized boolean finish(long attempt, boolean running) {
    if (token != attempt) return false;
    if (!busy) return state == State.RUNNING;
    expire();
    busy = false;
    if (running && state == State.STARTING) {
      state = State.RUNNING;
      attempts = 0;
      retryAt = 0;
    } else {
      if (failure.isEmpty()) failure = "StartupFailed";
      retryAt = clock.getAsLong() + attempts * 5_000L;
      state = attempts < MAX_ATTEMPTS ? State.RETRY_WAIT : State.FAILED;
    }
    return state == State.RUNNING;
  }

  /** Commit a healthy result atomically; a rejected result must still finish cleanup. */
  public synchronized boolean accept(long attempt) {
    expire();
    if (token != attempt || !busy || state != State.STARTING) return false;
    busy = false;
    state = State.RUNNING;
    attempts = 0;
    retryAt = 0;
    return true;
  }

  public synchronized boolean retryDue() {
    return state == State.RETRY_WAIT && !busy && clock.getAsLong() >= retryAt;
  }

  public synchronized boolean busy() {
    return busy;
  }

  public synchronized void cancel() {
    token++;
    busy = false;
    attempts = 0;
    retryAt = 0;
    state = State.STOPPED;
    failure = "";
  }

  public synchronized Snapshot snapshot() {
    expire();
    long target = state == State.STARTING ? deadline : state == State.RETRY_WAIT ? retryAt : 0;
    long seconds = Math.max(0, (target - clock.getAsLong() + 999) / 1000);
    return new Snapshot(state, phase, seconds, failure);
  }

  private void expire() {
    if (state == State.STARTING && clock.getAsLong() >= deadline) {
      state = State.DRAINING;
      failure = "Timeout";
    }
  }
}
