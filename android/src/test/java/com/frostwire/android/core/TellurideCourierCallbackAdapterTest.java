/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import com.frostwire.android.gui.SearchMediator;
import com.frostwire.android.gui.views.AbstractListAdapter;
import com.frostwire.search.SearchListener;
import com.frostwire.search.telluride.TellurideSearchResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** The Telluride callback of the search screen fills the results adapter itself. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TellurideCourierCallbackAdapterTest {

  private static final String URL = "https://example.com/v";

  @SuppressWarnings("unchecked")
  private final AbstractListAdapter<TellurideSearchResult> adapter =
      mock(AbstractListAdapter.class);

  private TellurideCourier.SearchPerformer<AbstractListAdapter<TellurideSearchResult>> performer(
      long token) {
    return new TellurideCourier.SearchPerformer<>(token, URL, adapter);
  }

  private static TellurideSearchResult video(String id) {
    return new TellurideSearchResult(id, "Video " + id, "Cloud: example", URL, "", 0);
  }

  @Test
  public void resultsAreAddedToTheAdapterOneByOne() {
    TellurideCourierCallback<AbstractListAdapter<TellurideSearchResult>> callback =
        new TellurideCourierCallback<>(performer(1), URL, adapter);
    TellurideSearchResult a = video("a");
    TellurideSearchResult b = video("b");

    callback.onResults(List.of(a, b), false);

    verify(adapter).addItem(a);
    verify(adapter).addItem(b);
  }

  @Test
  public void noResultsClearTheAdapterAndStopTheSearchOnTheUiThread() {
    TellurideCourier.SearchPerformer<AbstractListAdapter<TellurideSearchResult>> performer =
        performer(11);
    TellurideCourierCallback<AbstractListAdapter<TellurideSearchResult>> callback =
        new TellurideCourierCallback<>(performer, URL, adapter);
    SearchListener listener = mock(SearchListener.class);
    SearchMediator mediator = mock(SearchMediator.class);
    when(mediator.getListener()).thenReturn(listener);

    try (MockedStatic<SearchMediator> statics = Mockito.mockStatic(SearchMediator.class)) {
      statics.when(SearchMediator::instance).thenReturn(mediator);
      callback.onResults(new ArrayList<TellurideSearchResult>(), false);
      callback.onResults((List<TellurideSearchResult>) null, true);
      callback.onResults(List.of(video("x")), true);
      ShadowLooper.idleMainLooper();
    }

    verify(adapter, never()).addItem(Mockito.any());
    verify(adapter, Mockito.times(3)).clear();
    verify(listener, Mockito.times(3)).onStopped(11L);
  }

  @Test
  public void aMissingSearchListenerStillClearsTheAdapter() {
    TellurideCourierCallback<AbstractListAdapter<TellurideSearchResult>> callback =
        new TellurideCourierCallback<>(performer(2), URL, adapter);
    SearchMediator mediator = mock(SearchMediator.class);
    try (MockedStatic<SearchMediator> statics = Mockito.mockStatic(SearchMediator.class)) {
      statics.when(SearchMediator::instance).thenReturn(mediator);
      callback.onResults((List<TellurideSearchResult>) null, true);
      ShadowLooper.idleMainLooper();
    }
    verify(adapter).clear();
  }

  @Test
  public void aNullPlaylistTextIsAnErrorAndAPlaylistTextBecomesRows() {
    TellurideCourierCallback<AbstractListAdapter<TellurideSearchResult>> callback =
        new TellurideCourierCallback<>(performer(3), URL, adapter);
    String playlist =
        "{\"type\":\"playlist\",\"extractor\":\"example\",\"entries\":["
            + "{\"id\":\"e1\",\"title\":\"One\",\"webpage_url\":\"https://example.com/1\"}]}";

    callback.onResults(playlist, true);

    org.mockito.ArgumentCaptor<TellurideSearchResult> added =
        org.mockito.ArgumentCaptor.forClass(TellurideSearchResult.class);
    verify(adapter).addItem(added.capture());
    assertEquals("One", added.getValue().getDisplayName());
    assertEquals("https://example.com/1", added.getValue().getDetailsUrl());
  }

  @Test
  public void callbacksForTheSamePageAreEqual() {
    TellurideCourierCallback<AbstractListAdapter<TellurideSearchResult>> a =
        new TellurideCourierCallback<>(performer(1), URL, adapter);
    TellurideCourierCallback<AbstractListAdapter<TellurideSearchResult>> b =
        new TellurideCourierCallback<>(performer(2), URL, adapter);
    TellurideCourierCallback<AbstractListAdapter<TellurideSearchResult>> other =
        new TellurideCourierCallback<>(performer(3), URL + "2", adapter);
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertFalse(a.equals(other));
    assertFalse(a.equals(null));
    assertFalse(a.equals("a string"));
    assertTrue(a.equals(a));
  }

  @Test
  public void performerExposesItsStateAndNotifiesTheListenerWhenStopped() {
    TellurideCourier.SearchPerformer<AbstractListAdapter<TellurideSearchResult>> p = performer(77);
    SearchListener listener = mock(SearchListener.class);
    assertEquals(77, p.getToken());
    assertFalse(p.isStopped());
    assertFalse(p.isCrawler());
    assertFalse(p.isDDOSProtectionActive());
    p.setListener(listener);
    assertEquals(listener, p.getListener());
    p.crawl(null);

    p.stop();

    assertTrue(p.isStopped());
    verify(listener).onStopped(77L);
  }

  @Test
  public void aListenerThatFailsWhenStoppedDoesNotStopTheStop() {
    TellurideCourier.SearchPerformer<AbstractListAdapter<TellurideSearchResult>> p = performer(5);
    SearchListener listener = mock(SearchListener.class);
    Mockito.doThrow(new IllegalStateException("boom")).when(listener).onStopped(5L);
    p.setListener(listener);

    p.stop();

    assertTrue(p.isStopped());
  }
}
