/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.core;

import com.frostwire.android.gui.SearchMediator;
import com.frostwire.android.gui.views.AbstractListAdapter;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.SearchListener;
import com.frostwire.search.telluride.TellurideSearchPerformer;
import com.frostwire.search.telluride.TellurideSearchResult;
import com.frostwire.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.List;
import java.util.Objects;

public class TellurideCourierCallback<
    T extends AbstractListAdapter<? super TellurideSearchResult>> {
  private final T adapter;
  private final String url;
  private final TellurideCourier.SearchPerformer<T> searchPerformer;

  /**
   * When set, results go to this performer's search listener, like any search engine's, instead of
   * into a list adapter (frostwire/frostwire#1002).
   */
  private final ISearchPerformer listenerPerformer;

  private boolean hasAborted = false;

  private static Logger LOG = Logger.getLogger(TellurideCourierCallback.class);

  public TellurideCourierCallback(
      TellurideCourier.SearchPerformer<T> searchPerformer, String pageUrl, T adapter) {
    this.searchPerformer = searchPerformer;
    this.url = pageUrl;
    this.adapter = adapter;
    this.listenerPerformer = null;
  }

  /** Reports the results to {@code performer}'s search listener, then that the search stopped. */
  TellurideCourierCallback(ISearchPerformer performer, String pageUrl) {
    this.searchPerformer = null;
    this.url = pageUrl;
    this.adapter = null;
    this.listenerPerformer = performer;
  }

  /** No results, or Telluride failed: empty the adapter and let the search screen stop waiting. */
  private void onNoResults() {
    SystemUtils.postToUIThread(
        () -> {
          if (adapter != null) {
            adapter.clear();
          }
          if (SearchMediator.instance().getListener() != null && searchPerformer != null) {
            SearchMediator.instance().getListener().onStopped(searchPerformer.getToken());
          }
        });
  }

  private static volatile Gson gson = null;

  void onResults(String result, boolean isPlaylist) {
    if (result == null) {
      onResults((List<TellurideSearchResult>) null, true);
      return;
    }
    if (gson == null) {
      gson = new GsonBuilder().create();
    }
    List<TellurideSearchResult> playlistResults =
        TellurideSearchPerformer.getValidPlaylistResults(result, gson, null, -1, url);
    onResults(playlistResults, false);
  }

  void onResults(List<TellurideSearchResult> results, boolean errored) {
    if (listenerPerformer != null) {
      SearchListener listener = listenerPerformer.getListener();
      if (listener == null || hasAborted) {
        return;
      }
      long token = listenerPerformer.getToken();
      if (!errored && results != null && !results.isEmpty()) {
        listener.onResults(token, results);
      }
      listener.onStopped(token);
      return;
    }
    if (results == null || results.isEmpty() || errored) {
      onNoResults();
    } else if (adapter != null) {
      for (TellurideSearchResult result : results) {
        adapter.addItem(result);
      }
    }
  }

  final void abort() {
    hasAborted = true;
  }

  final boolean aborted() {
    return hasAborted;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TellurideCourierCallback<?> that = (TellurideCourierCallback<?>) o;
    return Objects.equals(url, that.url);
  }

  @Override
  public int hashCode() {
    return Objects.hash(url);
  }
}
