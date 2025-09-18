/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

    @Override
    public boolean isDDOSProtectionActive() {
        return false;
    }
}
