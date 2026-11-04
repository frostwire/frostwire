/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The SearchManager reports every search's results, errors and end to each of its listeners: the
 * search window and the MCP server listen at the same time, and neither may cut the other off. A
 * listener that throws is logged and skipped; the others still hear the event.
 */
class SearchManagerListenersTest {

  private final SearchManager manager = SearchManager.getInstance();
  private final List<SearchListener> added = new CopyOnWriteArrayList<>();

  @AfterEach
  void removeListeners() {
    for (SearchListener listener : added) {
      manager.removeListener(listener);
    }
  }

  @Test
  void everyListenerHearsTheResultsErrorsAndEndOfASearch() throws InterruptedException {
    long token = token();
    Recorder window = add(new Recorder(token));
    Recorder mcp = add(new Recorder(token));

    manager.perform(new OneShotPerformer(token));

    for (Recorder recorder : List.of(window, mcp)) {
      assertTrue(recorder.stopped.await(10, TimeUnit.SECONDS), "the search ended");
      assertEquals(List.of("results 2", "error 7 boom", "stopped"), recorder.events);
    }
  }

  @Test
  void aListenerThatThrowsDoesNotKeepTheOthersFromHearing() throws InterruptedException {
    long token = token();
    Recorder before = add(new Recorder(token));
    add(
        new SearchListener() {
          @Override
          public void onResults(long t, List<? extends SearchResult> results) {
            throw new IllegalStateException("broken window");
          }

          @Override
          public void onError(long t, SearchError error) {
            throw new IllegalStateException("broken window");
          }

          @Override
          public void onStopped(long t) {
            throw new IllegalStateException("broken window");
          }
        });
    Recorder after = add(new Recorder(token));

    manager.perform(new OneShotPerformer(token));

    for (Recorder recorder : List.of(before, after)) {
      assertTrue(recorder.stopped.await(10, TimeUnit.SECONDS), "the search ended");
      assertEquals(List.of("results 2", "error 7 boom", "stopped"), recorder.events);
    }
  }

  @Test
  void aRemovedListenerHearsNothingMoreAndAddingTwiceDeliversOnce() throws InterruptedException {
    long token = token();
    Recorder removed = add(new Recorder(token));
    manager.removeListener(removed);
    Recorder twice = add(new Recorder(token));
    manager.addListener(twice);

    manager.perform(new OneShotPerformer(token));

    assertTrue(twice.stopped.await(10, TimeUnit.SECONDS), "the search ended");
    assertEquals(List.of("results 2", "error 7 boom", "stopped"), twice.events);
    assertTrue(removed.events.isEmpty());
  }

  private <T extends SearchListener> T add(T listener) {
    added.add(listener);
    manager.addListener(listener);
    return listener;
  }

  private static long token() {
    return System.nanoTime() & Long.MAX_VALUE;
  }

  /** Records the events of one search; other searches in this JVM are ignored. */
  private static final class Recorder implements SearchListener {
    final long token;
    final List<String> events = new CopyOnWriteArrayList<>();
    final CountDownLatch stopped = new CountDownLatch(1);

    Recorder(long token) {
      this.token = token;
    }

    @Override
    public void onResults(long t, List<? extends SearchResult> results) {
      if (t == token) {
        events.add("results " + results.size());
      }
    }

    @Override
    public void onError(long t, SearchError error) {
      if (t == token) {
        events.add("error " + error.code() + " " + error.message());
      }
    }

    @Override
    public void onStopped(long t) {
      if (t == token) {
        events.add("stopped");
        stopped.countDown();
      }
    }
  }

  /** Finds two results and reports one error. */
  private static final class OneShotPerformer implements ISearchPerformer {
    private final long token;
    private volatile SearchListener listener;
    private volatile boolean stopped;

    OneShotPerformer(long token) {
      this.token = token;
    }

    @Override
    public long getToken() {
      return token;
    }

    @Override
    public void perform() {
      listener.onResults(token, List.of(new Named("a"), new Named("b")));
      listener.onError(token, new SearchError(7, "boom"));
    }

    @Override
    public void crawl(CrawlableSearchResult sr) {}

    @Override
    public void stop() {
      stopped = true;
    }

    @Override
    public boolean isStopped() {
      return stopped;
    }

    @Override
    public SearchListener getListener() {
      return listener;
    }

    @Override
    public void setListener(SearchListener listener) {
      this.listener = listener;
    }

    @Override
    public boolean isDDOSProtectionActive() {
      return false;
    }

    @Override
    public boolean isCrawler() {
      return false;
    }
  }

  private record Named(String name) implements SearchResult {
    @Override
    public String getDisplayName() {
      return name;
    }

    @Override
    public String getDetailsUrl() {
      return null;
    }

    @Override
    public long getCreationTime() {
      return 0;
    }

    @Override
    public String getSource() {
      return "test";
    }

    @Override
    public com.frostwire.licenses.License getLicense() {
      return null;
    }

    @Override
    public String getThumbnailUrl() {
      return null;
    }
  }
}
