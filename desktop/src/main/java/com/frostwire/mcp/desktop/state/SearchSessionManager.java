/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.state;

import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchManager;
import com.frostwire.search.SearchResult;
import com.frostwire.search.engines.SearchEngine;
import com.limegroup.gnutella.gui.search.DesktopSearchEngines;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;

public class SearchSessionManager implements SearchListener {
  private static final SearchSessionManager INSTANCE = new SearchSessionManager();
  private static final long SESSION_TTL_MS = 10 * 60 * 1000L;

  private final Map<Long, SearchSession> sessions = new ConcurrentHashMap<>();
  private final AtomicLong tokenCounter = new AtomicLong(1);

  private SearchSessionManager() {}

  public static SearchSessionManager instance() {
    return INSTANCE;
  }

  public SearchSession createSearch(String keywords, List<String> engineIds) {
    boolean filterEngines = engineIds != null && !engineIds.isEmpty();
    return startSearch(
        keywords,
        token -> {
          List<ISearchPerformer> performers = new ArrayList<>();
          for (SearchEngine engine : DesktopSearchEngines.all()) {
            if (filterEngines && !engineIds.contains(engine.getId().name())) {
              continue;
            }
            if (!engine.isEnabled()) {
              continue;
            }
            // Null when the engine isn't ready (no TPB mirror yet, relay not wired).
            ISearchPerformer performer = engine.getPerformer(token, keywords);
            if (performer != null) {
              performers.add(performer);
            }
          }
          return performers;
        });
  }

  /**
   * Starts a search run by the performers {@code performersFor} makes for its token.
   * Package-private so tests can run a search on performers of their own.
   */
  SearchSession startSearch(String keywords, LongFunction<List<ISearchPerformer>> performersFor) {
    long token = tokenCounter.getAndIncrement();
    List<ISearchPerformer> performers = performersFor.apply(token);
    SearchSession session = new SearchSession(token, keywords, performers);
    sessions.put(token, session);
    SearchManager searchManager = SearchManager.getInstance();
    for (ISearchPerformer performer : performers) {
      searchManager.perform(performer);
    }
    // The SearchManager reports a search stopped whenever none of its tasks is left, which can
    // happen while later performers are still being handed to it. Only reports after every
    // performer was handed over count; if all of them finished before that, the search is done.
    session.setSubmitted();
    if (!searchManager.isSearching(token)) {
      session.setComplete();
    }
    return session;
  }

  public SearchSession getSession(long token) {
    return sessions.get(token);
  }

  @Override
  public void onResults(long token, List<? extends SearchResult> results) {
    SearchSession session = sessions.get(token);
    if (session != null) {
      session.addResults(results);
    }
  }

  /**
   * The SearchManager calls this when no task of the search is left running. Its performers don't
   * mark themselves stopped when they finish (only stop() does), so this is when the search ends,
   * once all of its performers were handed to the SearchManager.
   */
  @Override
  public void onStopped(long token) {
    SearchSession session = sessions.get(token);
    if (session != null && session.isSubmitted()) {
      session.setComplete();
    }
  }

  @Override
  public void onError(long token, SearchError error) {
    SearchSession session = sessions.get(token);
    if (session != null) {
      session.setError(error.message());
    }
  }

  public void cleanup() {
    long now = System.currentTimeMillis();
    sessions
        .entrySet()
        .removeIf(
            entry -> {
              SearchSession session = entry.getValue();
              return session.isComplete() && (now - session.getCreatedAt()) > SESSION_TTL_MS;
            });
  }
}
