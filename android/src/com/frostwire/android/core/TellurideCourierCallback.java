/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2022, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.core;

import com.frostwire.android.gui.LocalSearchEngine;
import com.frostwire.android.gui.adapters.SearchResultListAdapter;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.telluride.TellurideSearchResult;

import java.util.List;
import java.util.Objects;

public class TellurideCourierCallback {
    private final SearchResultListAdapter adapter;
    private final String url;
    private final TellurideCourier.SearchPerformer searchPerformer;
    private boolean hasAborted = false;

    public TellurideCourierCallback(TellurideCourier.SearchPerformer searchPerformer, String pageUrl, SearchResultListAdapter adapter) {
        this.searchPerformer = searchPerformer;
        this.url = pageUrl;
        this.adapter = adapter;
    }

    void onResults(List<TellurideSearchResult> results, boolean errored) {
        // This comes in too fast, gotta let the UI get there
        SystemUtils.postToUIThread(() -> {
            // Screw our listener, make the adapter do what we want.
            adapter.clear();

            if (results != null) {
                adapter.addResults(results); // adds to full list of results
                adapter.setFileType(Constants.FILE_TYPE_AUDIO);
                adapter.setFileType(Constants.FILE_TYPE_VIDEOS, true);
                SearchResultListAdapter.FilteredSearchResults filteredSearchResults = adapter.getFilteredSearchResults();
                adapter.updateVisualListWithAllMediaTypeFilteredSearchResults(filteredSearchResults.mediaTypeFiltered);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {

                }
                LocalSearchEngine.instance().getListener().onStopped(searchPerformer.getToken());
            } else if (results == null || errored) {
                LocalSearchEngine.instance().getListener().onStopped(searchPerformer.getToken());
            }
        });
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