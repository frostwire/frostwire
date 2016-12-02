/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.util;

import android.widget.AbsListView;

import com.frostwire.android.gui.services.Engine;
import com.frostwire.util.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created on 11/23/16.
 * @author gubatron
 * @author aldenml
 */

public final class DirectionDetectorScrollListener {
    @SuppressWarnings("unused")
    private static final Logger LOG = Logger.getLogger(DirectionDetectorScrollListener.class);

    public static AbsListView.OnScrollListener createOnScrollListener(final ScrollDirectionListener scrollDirectionListener) {
        return new AbsListView.OnScrollListener() {
            private int lastFirstVisibleItem;
            private int lastVisibleItemCount;
            private AtomicBoolean enabled = new AtomicBoolean(true);
            private AtomicBoolean enabledScrolldown = new AtomicBoolean(true);
            private AtomicBoolean enabledScrollup = new AtomicBoolean(false);
            @Override
            public void onScrollStateChanged(AbsListView absListView, int scrollState) {
            }
            @Override
            public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!enabled.get() || scrollDirectionListener == null) {
                    return;
                }
                // layout changes during scrolling
                if (visibleItemCount != lastVisibleItemCount) {
                    // more vertical space might mean we're going down
                    lastVisibleItemCount = visibleItemCount;
                    disable(250L, enabled);
                    return;
                }
                boolean scrollingDown = firstVisibleItem > 2 && firstVisibleItem >= lastFirstVisibleItem;
                boolean scrollingUp = firstVisibleItem < (totalItemCount - visibleItemCount) && firstVisibleItem <= lastFirstVisibleItem;
                lastFirstVisibleItem = firstVisibleItem;
                if (enabledScrolldown.get() && scrollingDown && !scrollingUp) {
                    disable(400L, enabledScrollup);
                    scrollDirectionListener.onScrollDown();
                } else if (enabledScrollup.get() && scrollingUp && !scrollingDown) {
                    disable(400L, enabledScrolldown);
                    scrollDirectionListener.onScrollUp();
                }
            }
            /** sets the given flag to false temporarily for interval milliseconds */
            private void disable(final long interval, final AtomicBoolean flag) {
                // stop listening for 500 seconds to be able to detect rate of change.
                flag.set(false);
                Engine.instance().getThreadPool().submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(interval);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        flag.set(true);
                    }
                });
            }
        };
    }
}
