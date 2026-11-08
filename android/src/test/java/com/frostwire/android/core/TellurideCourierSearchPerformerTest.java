/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.telluride.TellurideSearchResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Telluride on Android reports through the performer's search listener, like any engine, instead of
 * writing into the results list adapter (frostwire/frostwire#1002).
 */
public class TellurideCourierSearchPerformerTest {

  private static final class RecordingListener implements SearchListener {
    final List<SearchResult> results = new ArrayList<>();
    final List<Long> resultTokens = new ArrayList<>();
    final List<Long> stopped = new ArrayList<>();

    @Override
    public void onResults(long token, List<? extends SearchResult> results) {
      resultTokens.add(token);
      this.results.addAll(results);
    }

    @Override
    public void onError(long token, SearchError error) {}

    @Override
    public void onStopped(long token) {
      stopped.add(token);
    }
  }

  @Test
  public void resultsGoToTheListenerThenTheSearchStops() {
    TellurideCourierSearchPerformer performer =
        new TellurideCourierSearchPerformer(42, "https://example.com/v", false);
    RecordingListener listener = new RecordingListener();
    performer.setListener(listener);
    TellurideCourierCallback<?> callback =
        new TellurideCourierCallback<>(performer, "https://example.com/v");
    TellurideSearchResult video =
        new TellurideSearchResult("1", "A video", "Cloud: example", "https://example.com/v", "", 0);

    callback.onResults(List.of(video), false);

    assertEquals(List.of(42L), listener.resultTokens);
    assertSame(video, listener.results.get(0));
    assertEquals(List.of(42L), listener.stopped);
  }

  @Test
  public void noResultsOrAnErrorOnlyStopsTheSearch() {
    TellurideCourierSearchPerformer performer =
        new TellurideCourierSearchPerformer(7, "https://example.com/v", false);
    RecordingListener listener = new RecordingListener();
    performer.setListener(listener);
    TellurideCourierCallback<?> callback =
        new TellurideCourierCallback<>(performer, "https://example.com/v");

    callback.onResults((List<TellurideSearchResult>) null, true);
    callback.onResults(new ArrayList<>(), false);

    assertTrue(listener.results.isEmpty());
    assertEquals(List.of(7L, 7L), listener.stopped);
  }

  @Test
  public void anAbortedQueryReportsNothing() {
    TellurideCourierSearchPerformer performer =
        new TellurideCourierSearchPerformer(9, "https://example.com/v", false);
    RecordingListener listener = new RecordingListener();
    performer.setListener(listener);
    TellurideCourierCallback<?> callback =
        new TellurideCourierCallback<>(performer, "https://example.com/v");
    TellurideSearchResult video =
        new TellurideSearchResult("1", "A video", "Cloud: example", "https://example.com/v", "", 0);

    callback.abort();
    callback.onResults(List.of(video), false);

    assertTrue(listener.results.isEmpty());
    assertTrue(listener.stopped.isEmpty());
  }
}
