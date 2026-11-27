/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.AndroidSearchEngines;
import com.frostwire.android.gui.SearchMediator;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.views.SearchInputView;
import com.frostwire.android.gui.views.SearchProgressView;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.frostclick.Slide;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchListener;
import com.frostwire.search.engines.SearchEngine;
import com.frostwire.search.engines.SearchEngineID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SearchFragmentTest {
  private MockedStatic<SearchMediator> mediators;
  private MockedStatic<ConfigurationManager> configurations;
  private MockedStatic<TransferManager> transferManagers;
  private MockedStatic<AndroidSearchEngines> engines;
  private SearchMediator mediator;
  private ConfigurationManager configuration;
  private TransferManager transferManager;
  private SearchEngine distributed;

  @Before
  public void setUp() {
    mediator = mock(SearchMediator.class);
    configuration = mock(ConfigurationManager.class);
    transferManager = mock(TransferManager.class);
    mediators = mockStatic(SearchMediator.class);
    configurations = mockStatic(ConfigurationManager.class);
    transferManagers = mockStatic(TransferManager.class);
    engines = mockStatic(AndroidSearchEngines.class);
    mediators.when(SearchMediator::instance).thenReturn(mediator);
    configurations.when(ConfigurationManager::instance).thenReturn(configuration);
    transferManagers.when(TransferManager::instance).thenReturn(transferManager);
    distributed = mock(SearchEngine.class);
    engines
        .when(() -> AndroidSearchEngines.byId(SearchEngineID.DISTRIBUTED_ID))
        .thenReturn(distributed);
    when(configuration.getLastMediaTypeFilter()).thenReturn((int) Constants.FILE_TYPE_AUDIO);
  }

  @After
  public void tearDown() {
    mediators.close();
    configurations.close();
    transferManagers.close();
    engines.close();
  }

  private static SearchInputView input(SearchFragment f) {
    return f.requireView().findViewById(R.id.fragment_search_input);
  }

  private static SearchProgressView progress(SearchFragment f) {
    return f.requireView().findViewById(R.id.fragment_search_search_progress);
  }

  private static ProgressBar deepProgress(SearchFragment f) {
    return f.requireView().findViewById(R.id.fragment_search_deepsearch_progress);
  }

  private static ListView list(SearchFragment f) {
    return f.requireView().findViewById(R.id.fragment_search_list);
  }

  private static int visibleChildId(SearchFragment f) {
    FrameLayout frame = f.requireView().findViewById(R.id.fragment_search_framelayout);
    for (int i = 0; i < frame.getChildCount(); i++) {
      if (frame.getChildAt(i).getVisibility() == View.VISIBLE) {
        return frame.getChildAt(i).getId();
      }
    }
    return -1;
  }

  private static TestSearchFragment launch(List<Slide> slides) {
    TestSearchFragment fragment = new TestSearchFragment();
    TestSearchFragment.slidesFromServer = slides;
    FragmentTestHost.launch(fragment);
    return fragment;
  }

  private static void search(SearchFragment f, String query) {
    SearchInputView input = input(f);
    input.setText(query);
    View box = input.findViewById(R.id.view_search_input_text_input);
    ((com.frostwire.android.gui.views.ClearableEditTextView) box)
        .getAutoCompleteTextView()
        .dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
    shadowOf(Looper.getMainLooper()).idle();
  }

  private static FileSearchResult fileResult(String name) {
    FileSearchResult r = mock(FileSearchResult.class);
    when(r.getFilename()).thenReturn(name);
    when(r.getDisplayName()).thenReturn(name);
    when(r.getSource()).thenReturn("test");
    when(r.getDetailsUrl()).thenReturn("https://example.com/" + name);
    when(r.getThumbnailUrl()).thenReturn(null);
    when(r.getSize()).thenReturn(1024L);
    when(r.getCreationTime()).thenReturn(0L);
    return r;
  }

  @Test
  public void startsShowingThePromotions() {
    TestSearchFragment fragment = launch(Collections.emptyList());
    assertEquals(R.id.fragment_search_promos, visibleChildId(fragment));
    assertEquals(View.GONE, deepProgress(fragment).getVisibility());
    assertNull(fragment.getCurrentQuery());
    assertSame(fragment, SearchFragment.instance());
    verify(mediator, atLeastOnce()).setSearchListener(any());
  }

  @Test
  public void theHeaderIsTitledSearch() {
    TestSearchFragment fragment = new TestSearchFragment();
    ActivityController<FragmentTestHost> host = FragmentTestHost.launch(fragment);
    LinearLayout header = (LinearLayout) fragment.getHeader(host.get());
    assertEquals(
        fragment.getString(R.string.search),
        ((TextView) header.findViewById(R.id.view_search_header_text_title)).getText().toString());
    // five taps on the title is only counted, there are no interstitials to show
    for (int i = 0; i < 5; i++) {
      header.findViewById(R.id.view_search_header_text_title).performClick();
    }
  }

  @Test
  public void promotionsLoadedFromTheServerReplaceTheEmptyOnes() throws Exception {
    TestSearchFragment fragment = launch(new ArrayList<>());
    FragmentTestHost.settleUntil(() -> TestSearchFragment.loads > 0);
    assertTrue(TestSearchFragment.loads > 0);
  }

  @Test
  public void aQueryShowsTheSpinnerAndSearchesInTheBackground() {
    TestSearchFragment fragment = launch(Collections.emptyList());
    search(fragment, "creative commons");

    assertEquals("creative commons", fragment.getCurrentQuery());
    verify(mediator).performSearch("creative commons");
    assertEquals(R.id.fragment_search_search_progress, visibleChildId(fragment));
    assertEquals(View.VISIBLE, deepProgress(fragment).getVisibility());
  }

  @Test
  public void clearingTheQueryGoesBackToThePromotions() {
    TestSearchFragment fragment = launch(Collections.emptyList());
    search(fragment, "creative commons");

    View box = input(fragment).findViewById(R.id.view_search_input_text_input);
    com.frostwire.android.gui.views.ClearableEditTextView clearable =
        (com.frostwire.android.gui.views.ClearableEditTextView) box;
    clearable.getOnActionListener().onClear(clearable);
    shadowOf(Looper.getMainLooper()).idle();

    assertNull(fragment.getCurrentQuery());
    verify(mediator).cancelSearch();
    assertEquals(R.id.fragment_search_promos, visibleChildId(fragment));
  }

  @Test
  public void aMagnetLinkIsDownloadedInsteadOfSearched() {
    TestSearchFragment fragment = launch(Collections.emptyList());
    String magnet = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=hi";
    search(fragment, magnet);

    verify(transferManager).downloadTorrent(eq(magnet), any());
    assertEquals(
        fragment.getString(R.string.torrent_url_added), ShadowToast.getTextOfLatestToast());
    assertNull(fragment.getCurrentQuery());
    assertEquals("", input(fragment).getText());
  }

  @Test
  public void aWebPageLinkStartsATellurideSearch() {
    TestSearchFragment fragment = launch(Collections.emptyList());
    search(fragment, "https://example.com/some/video");

    verify(mediator).performTellurideSearch("https://example.com/some/video");
    assertEquals(R.id.fragment_search_list, visibleChildId(fragment));
    assertEquals(View.VISIBLE, deepProgress(fragment).getVisibility());
    // an empty space so the clear button shows
    assertEquals(" ", input(fragment).getText());
  }

  @Test
  public void resultsFromTheSearchFillTheListAndTheCounters() throws Exception {
    TestSearchFragment fragment = launch(Collections.emptyList());
    ArgumentCaptor<SearchListener> listener = ArgumentCaptor.forClass(SearchListener.class);
    verify(mediator, atLeastOnce()).setSearchListener(listener.capture());
    when(mediator.isSearchFinished()).thenReturn(false);
    search(fragment, "creative commons");

    List<FileSearchResult> results = new ArrayList<>();
    results.add(fileResult("creative commons song.mp3"));
    results.add(fileResult("creative commons album.mp3"));
    results.add(fileResult("creative commons talk.pdf"));
    listener.getValue().onResults(1, results);
    FragmentTestHost.settleUntil(() -> list(fragment).getAdapter().getCount() == 2);

    assertEquals(2, list(fragment).getAdapter().getCount());
    assertEquals(R.id.fragment_search_list, visibleChildId(fragment));
    // each tab's counter has its files
    assertEquals(
        View.VISIBLE,
        input(fragment).findViewById(R.id.view_search_input_tab_layout_file_type).getVisibility());

    // switching to the documents tab filters the same results
    input(fragment).selectTabByMediaType(Constants.FILE_TYPE_DOCUMENTS);
    FragmentTestHost.settleUntil(() -> list(fragment).getAdapter().getCount() == 1);
    assertEquals(1, list(fragment).getAdapter().getCount());
    // and the choice is remembered
    verify(configuration).setLastMediaTypeFilter(Constants.FILE_TYPE_DOCUMENTS);
  }

  @Test
  public void aStoppedSearchWithNoResultsShowsNoResults() throws Exception {
    TestSearchFragment fragment = launch(Collections.emptyList());
    ArgumentCaptor<SearchListener> listener = ArgumentCaptor.forClass(SearchListener.class);
    verify(mediator, atLeastOnce()).setSearchListener(listener.capture());
    when(mediator.getCurrentSearchToken()).thenReturn(7L);
    search(fragment, "nothing");
    when(mediator.isSearchFinished()).thenReturn(true);
    when(mediator.isSearchStopped()).thenReturn(true);

    // a stale token is ignored
    listener.getValue().onStopped(3);
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals(View.VISIBLE, deepProgress(fragment).getVisibility());

    listener.getValue().onStopped(7);
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals(View.GONE, deepProgress(fragment).getVisibility());
    assertEquals(R.id.fragment_search_search_progress, visibleChildId(fragment));
    // errors are only logged
    listener.getValue().onError(7, null);
  }

  @Test
  public void aPromotionWithoutADownloadMethodOpensItsWebPage() {
    TestSearchFragment fragment = launch(Collections.emptyList());
    Slide slide = new Slide();
    slide.method = -1;
    slide.clickURL = "https://www.frostwire.com/promo";
    fragment.startPromotionDownload(slide);
    Intent intent =
        shadowOf((Application) ApplicationProvider.getApplicationContext())
            .getNextStartedActivity();
    assertNotNull(intent);
    assertEquals("https://www.frostwire.com/promo", intent.getDataString());

    // no method and no url: nothing happens
    Slide empty = new Slide();
    empty.method = -1;
    fragment.startPromotionDownload(empty);
    assertNull(
        shadowOf((Application) ApplicationProvider.getApplicationContext())
            .getNextStartedActivity());
    verify(transferManager, never()).downloadTorrent(anyString(), any());
  }

  /** Promotions come from here instead of the server. */
  public static class TestSearchFragment extends SearchFragment {
    static volatile List<Slide> slidesFromServer = Collections.emptyList();
    static volatile int loads;

    /** Background work runs where the test's mocks are, and never touches the network. */
    @Override
    void postInBackground(SystemUtils.HandlerThreadName thread, Runnable task) {
      task.run();
    }

    @Override
    List<Slide> loadSlidesInBackground() {
      loads++;
      return slidesFromServer;
    }
  }
}
