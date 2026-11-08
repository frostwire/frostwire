/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.engines;

import com.frostwire.search.ISearchPerformer;
import java.util.function.BooleanSupplier;

/**
 * One search engine, defined once for both clients in {@link SearchEngines} (frostwire/frostwire
 * #1002). Whether it is on comes from the client's {@link SearchEngineAdapter}; how it searches is
 * its performer factory.
 */
public final class SearchEngine {

  /** Makes the performer for one search. */
  @FunctionalInterface
  interface PerformerFactory {
    ISearchPerformer create(long token, String keywords, boolean playlistMode);
  }

  private final SearchEngineID id;
  private final String name;
  private final SearchEngineAdapter adapter;
  private final BooleanSupplier ready;
  private final PerformerFactory factory;

  SearchEngine(
      SearchEngineID id,
      String name,
      SearchEngineAdapter adapter,
      BooleanSupplier ready,
      PerformerFactory factory) {
    this.id = id;
    this.name = name;
    this.adapter = adapter;
    this.ready = ready;
    this.factory = factory;
  }

  public SearchEngineID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  /** Whether this client offers the engine (e.g. the Local test engine only in debug builds). */
  public boolean isOffered() {
    return adapter.isOffered(id);
  }

  /** Offered and switched on by the user. */
  public boolean isEnabled() {
    return isOffered() && adapter.isEnabled(id);
  }

  public void setEnabled(boolean enabled) {
    adapter.setEnabled(id, enabled);
  }

  /** Whether it can search now: TPB needs its mirror, Local and Distributed their relay wiring. */
  public boolean isReady() {
    return ready.getAsBoolean();
  }

  /** The performer for a search, or null when the engine isn't ready. */
  public ISearchPerformer getPerformer(long token, String keywords) {
    return getPerformer(token, keywords, false);
  }

  /**
   * The performer for a search, or null when the engine isn't ready. {@code playlistMode} only
   * matters to Telluride.
   */
  public ISearchPerformer getPerformer(long token, String keywords, boolean playlistMode) {
    return isReady() ? factory.create(token, keywords, playlistMode) : null;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof SearchEngine other && other.id == id;
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return name;
  }
}
