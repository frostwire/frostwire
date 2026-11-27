/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import com.frostwire.android.gui.adapters.SearchResultListAdapter;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.CrawlCacheManager;
import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.CrawledSearchResult;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchManager;
import com.frostwire.search.SearchResult;
import com.frostwire.search.engines.SearchEngine;
import com.frostwire.search.engines.SearchEngineID;
import com.frostwire.search.engines.SearchEngines;
import com.frostwire.search.relay.DistributedSearchPerformer;
import com.frostwire.search.torrent.TorrentSearchResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * SearchMediator is a singleton that wires itself to the SearchManager once, so its behavior is
 * exercised as one story with a mocked manager, in a Robolectric sandbox (SDK 33) no other test
 * shares.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class SearchMediatorTest {

  @Test
  public void aSearchRunsEveryEnabledEngineFiltersResultsAndReportsWhenItStops() {
    SearchManager manager = mock(SearchManager.class);
    SearchEngines registry = mock(SearchEngines.class);
    Engine engine = mock(Engine.class);
    SearchListener ui = mock(SearchListener.class);
    SearchMediator mediator;
    ArgumentCaptor<SearchListener> managerListener = ArgumentCaptor.forClass(SearchListener.class);
    try (MockedStatic<SearchManager> managers = mockStatic(SearchManager.class);
        MockedStatic<SystemUtils> system = mockStatic(SystemUtils.class);
        MockedStatic<Engine> engines = mockStatic(Engine.class);
        MockedStatic<AndroidSearchEngines> searchEngines = mockStatic(AndroidSearchEngines.class);
        MockedStatic<CrawlCacheManager> crawl = mockStatic(CrawlCacheManager.class)) {
      system.when(SystemUtils::isUIThread).thenReturn(false); // searches run off the UI thread
      managers.when(SearchManager::getInstance).thenReturn(manager);
      engines.when(Engine::instance).thenReturn(engine);
      searchEngines.when(AndroidSearchEngines::registry).thenReturn(registry);
      mediator = SearchMediator.instance();
      assertSame(mediator, SearchMediator.instance());
      verify(manager).addListener(managerListener.capture());
      SearchListener fromManager = managerListener.getValue();
      mediator.setSearchListener(ui);
      assertSame(ui, mediator.getListener());

      // nothing searched yet
      assertTrue(mediator.isSearchStopped());
      assertEquals(0, mediator.getCurrentSearchToken());
      assertEquals(0, mediator.distributedPeersContacted());

      // blank queries are ignored
      mediator.performSearch("   ");
      mediator.performSearch(null);
      verify(manager, never()).perform(any());

      // a real search: one engine without a performer is skipped
      ISearchPerformer plain = mock(ISearchPerformer.class);
      DistributedSearchPerformer distributed = mock(DistributedSearchPerformer.class);
      when(distributed.getPeersContacted()).thenReturn(3);
      SearchEngine plainEngine = mock(SearchEngine.class);
      SearchEngine distributedEngine = mock(SearchEngine.class);
      SearchEngine brokenEngine = mock(SearchEngine.class);
      when(plainEngine.getPerformer(anyLong(), eq("hello world"))).thenReturn(plain);
      when(distributedEngine.getPerformer(anyLong(), eq("hello world"))).thenReturn(distributed);
      when(brokenEngine.getPerformer(anyLong(), anyString())).thenReturn(null);
      when(registry.enabled())
          .thenReturn(Arrays.asList(plainEngine, distributedEngine, brokenEngine));
      mediator.performSearch("hello world");
      verify(engine).ensureDistributedSearchReady(15_000);
      verify(manager).perform(plain);
      verify(manager).perform(distributed);
      assertFalse(mediator.isSearchStopped());
      assertFalse(mediator.isSearchFinished());
      assertEquals(3, mediator.distributedPeersContacted());
      long token = mediator.getCurrentSearchToken();
      assertTrue(token > 0);

      // results of another search are dropped
      SearchResult plainResult = mock(SearchResult.class);
      fromManager.onResults(token + 1, Collections.singletonList(plainResult));
      verify(ui, never()).onResults(anyLong(), any());

      // crawled results must match a keyword, other results pass
      CrawledSearchResult matching = mock(CrawledSearchResult.class);
      when(matching.getDisplayName()).thenReturn("Hello There");
      when(matching.getDetailsUrl()).thenReturn("https://example.invalid/hello");
      CrawledSearchResult unrelated = mock(CrawledSearchResult.class);
      when(unrelated.getDisplayName()).thenReturn("Completely different");
      CrawlableSearchResult parent = mock(CrawlableSearchResult.class);
      when(parent.getDisplayName()).thenReturn("An album");
      when(matching.getParent()).thenReturn(parent);
      when(unrelated.getParent()).thenReturn(parent);
      List<SearchResult> delivered = new ArrayList<>();
      org.mockito.Mockito.doAnswer(
              call -> {
                // the mediator clears the list it hands over, so copy it right away
                delivered.addAll(call.getArgument(1));
                return null;
              })
          .when(ui)
          .onResults(eq(token), any());
      fromManager.onResults(token, Arrays.asList(plainResult, matching, unrelated));
      assertEquals(Arrays.asList(plainResult, matching), delivered);
      fromManager.onError(token, mock(SearchError.class));

      // a stop for the current search is forwarded and marks it finished
      fromManager.onStopped(token + 1);
      assertFalse(mediator.isSearchFinished());
      fromManager.onStopped(token);
      assertTrue(mediator.isSearchFinished());
      verify(ui).onStopped(token);

      // no engines at all finishes the search immediately
      when(registry.enabled()).thenReturn(new ArrayList<>());
      mediator.performSearch("again");
      assertTrue(mediator.isSearchFinished());
      verify(ui, times(2)).onStopped(anyLong());
      assertEquals(
          "a new search forgets the distributed performer",
          0,
          mediator.distributedPeersContacted());

      // without a listener nothing breaks
      mediator.setSearchListener(null);
      mediator.performSearch("silent");
      fromManager.onStopped(mediator.getCurrentSearchToken());
      mediator.setSearchListener(ui);

      // Telluride page searches run the courier and the FrostClick engine
      ISearchPerformer courier = mock(ISearchPerformer.class);
      SearchEngine telluride = mock(SearchEngine.class);
      when(registry.telluride()).thenReturn(telluride);
      when(telluride.getPerformer(anyLong(), eq("https://video.invalid/watch"), anyBoolean()))
          .thenReturn(courier);
      ISearchPerformer frostclick = mock(ISearchPerformer.class);
      SearchEngine frostclickEngine = mock(SearchEngine.class);
      searchEngines
          .when(() -> AndroidSearchEngines.byId(SearchEngineID.FROSTCLICK_ID))
          .thenReturn(frostclickEngine);
      when(frostclickEngine.getPerformer(1, "https://video.invalid/watch")).thenReturn(frostclick);
      mediator.performTellurideSearch("");
      verify(manager, never()).perform(courier);
      mediator.performTellurideSearch("https://video.invalid/watch");
      verify(manager).perform(courier);
      verify(manager).perform(frostclick);
      assertFalse(mediator.isSearchFinished());

      // cancelling stops the manager and the courier
      mediator.cancelSearch();
      assertTrue(mediator.isSearchStopped());
      assertTrue(mediator.isSearchFinished());
      verify(courier).stop();

      // opened results are remembered by what identifies them
      SearchResult a = mock(SearchResult.class);
      when(a.getDisplayName()).thenReturn("A");
      SearchResult b = mock(SearchResult.class);
      when(b.getDisplayName()).thenReturn("B");
      assertFalse(mediator.hasBeenOpened(a));
      assertFalse(mediator.hasBeenOpened(null));
      SearchResultListAdapter adapter = mock(SearchResultListAdapter.class);
      mediator.markOpened(a, adapter);
      verify(adapter).safeNotifyDataSetChanged();
      mediator.markOpened(b, null);
      assertTrue(mediator.hasBeenOpened(a));
      assertTrue(mediator.hasBeenOpened(b));
      TorrentSearchResult torrent = mock(TorrentSearchResult.class);
      when(torrent.getDisplayName()).thenReturn("A");
      when(torrent.getHash()).thenReturn("0123");
      when(torrent.getDetailsUrl()).thenReturn("https://example.invalid/t");
      when(torrent.getSource()).thenReturn("src");
      assertFalse("a torrent with a hash is a different result", mediator.hasBeenOpened(torrent));
      mediator.markOpened(torrent, null);
      assertTrue(mediator.hasBeenOpened(torrent));

      // the crawl cache is delegated
      crawl.when(CrawlCacheManager::getCacheNumEntries).thenReturn(12L);
      assertEquals(12, mediator.getCacheSize());
      mediator.clearCache();
      crawl.verify(CrawlCacheManager::clearCache);
    }
  }
}
