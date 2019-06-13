/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.search;

import com.frostwire.util.Logger;

import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractSearchPerformer implements SearchPerformer {
    private static final Logger LOG = Logger.getLogger(AbstractSearchPerformer.class);
    private final long token;
    protected boolean stopped;
    private SearchListener listener;

    public AbstractSearchPerformer(long token) {
        this.token = token;
        this.stopped = false;
    }

    @Override
    public long getToken() {
        return token;
    }

    @Override
    public void stop() {
        stopped = true;
        try {
            if (listener != null) {
                listener.onStopped(token);
            }
        } catch (Throwable e) {
            LOG.warn("Error sending finished signal to listener: " + e.getMessage());
        }
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

    protected void onResults(List<? extends SearchResult> results) {
        try {
            if (results != null && !stopped) {
                listener.onResults(token, results);
            }
        } catch (Throwable e) {
            LOG.warn("Error sending results to listener: " + e.getMessage());
        }
    }
}
