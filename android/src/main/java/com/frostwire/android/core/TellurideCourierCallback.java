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
import com.frostwire.android.gui.adapters.SearchResultListAdapter;
import com.frostwire.android.gui.fragments.SearchFragment;
import com.frostwire.android.gui.views.AbstractListAdapter;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.telluride.TellurideSearchResult;
import com.frostwire.util.Logger;

import java.util.List;
import java.util.Objects;

public class TellurideCourierCallback<T extends AbstractListAdapter> {
    private final T adapter;
    private final String url;
    private final TellurideCourier.SearchPerformer searchPerformer;
    private boolean hasAborted = false;

    private static Logger LOG = Logger.getLogger(TellurideCourierCallback.class);

    public TellurideCourierCallback(TellurideCourier.SearchPerformer<T> searchPerformer, String pageUrl, T adapter) {
        this.searchPerformer = searchPerformer;
        this.url = pageUrl;
        this.adapter = adapter;
    }

    void onSearchResultListAdapterResults(List<TellurideSearchResult> results, boolean errored) {
        SystemUtils.postToUIThread(() -> {
            LOG.info("onSearchResultListAdapterResults: " + adapter.getClass().getName());

            if (results == null || results.isEmpty() || errored) {
                adapter.clear();
                if (SearchMediator.instance().getListener() != null && searchPerformer != null) {
                    SearchMediator.instance().getListener().onStopped(searchPerformer.getToken());
                }
                return;
            }
            if (adapter instanceof com.frostwire.android.gui.dialogs.TellurideSearchResultDownloadDialog.TellurideSearchResultDownloadDialogAdapter) {
                LOG.info("onSearchResultListAdapterResults: TellurideSearchResultDownloadDialogAdapter, aborting.");
                return;
            }
            SearchResultListAdapter srlAdapter = (SearchResultListAdapter) adapter;
            // Screw our listener, make the adapter do what we want.
            adapter.clear();
            if (results != null && !results.isEmpty()) {
                srlAdapter.addResults(results); // adds to full list of results
                srlAdapter.setFileType(Constants.FILE_TYPE_AUDIO, false, null);
                srlAdapter.setFileType(Constants.FILE_TYPE_VIDEOS, true,
                        () -> {
                            SearchResultListAdapter.FilteredSearchResults filteredSearchResults = srlAdapter.getFilteredSearchResults();
                            srlAdapter.updateVisualListWithAllMediaTypeFilteredSearchResults(filteredSearchResults.mediaTypeFiltered, false);
                            SearchFragment.instance().refreshFileTypeCounters(false, filteredSearchResults);
                            SearchMediator.instance().getListener().onStopped(searchPerformer.getToken());
                        });

            }
        });
    }

    void onResults(List<TellurideSearchResult> results, boolean errored) {
        // This comes in too fast, gotta let the UI get there
        if (adapter instanceof SearchResultListAdapter || (results == null || results.isEmpty() || errored)) {
            onSearchResultListAdapterResults(results, errored);
        } else {
            if (results != null && !results.isEmpty() && adapter != null) {
                for (TellurideSearchResult result : results) {
                    adapter.addItem(result);
                }
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
        TellurideCourierCallback that = (TellurideCourierCallback) o;
        return Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }
}