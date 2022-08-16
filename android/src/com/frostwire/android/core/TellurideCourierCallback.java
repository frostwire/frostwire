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

import com.frostwire.search.telluride.TellurideSearchResult;

import java.util.List;
import java.util.Objects;

public abstract class TellurideCourierCallback {
    private boolean hasAborted = false;
    private String url = null;

    public TellurideCourierCallback(String pageUrl) {
        setUrl(pageUrl);
    }

    abstract void onResults(List<TellurideSearchResult> results, boolean errored);

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

    private void setUrl(String url) {
        this.url = url;
    }
}