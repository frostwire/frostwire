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

import android.support.annotation.NonNull;
import android.widget.AbsListView;
import android.widget.ListView;

import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Created on 11/23/16.
 * @author gubatron
 * @author aldenml
 */

public final class ListViewScrollDirectionDetector {
    @SuppressWarnings("unused")
    private static final Logger LOG = Logger.getLogger(List.class);

    private final ScrollDirectionListener directionListener;
    private final ListView listView;
    private final AbsListView.OnScrollListener scrollListener;

    public ListViewScrollDirectionDetector(@NonNull ListView lv, @NonNull final ScrollDirectionListener listener) {
        listView = lv;
        directionListener = listener;

        // NOTE: I wish I could get a hold of a previous OnScrollListener on the listView, so I could
        // keep a reference and invoke it on my directionListener if it's non null.
        // For now, it's not necessary in our code base on SearchFragment's listView usage.

        scrollListener = new AbsListView.OnScrollListener() {
            private List<Integer> topPositions = new ArrayList<>();

            @Override
            public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                final int currentFirstVisibleItem = absListView.getFirstVisiblePosition();
                topPositions.add(currentFirstVisibleItem);

                int deltas = 0;
                if (topPositions.size() > 2) {

                    int lastPosition = -1;
                    for (int i : topPositions) {
                        if (lastPosition == -1) {
                            lastPosition = i;
                        } else {
                            deltas += (i - lastPosition);
                        }
                    }
                    topPositions.clear();
                } else {
                    return;
                }


                // at the start trying to scroll up
                if (scrollState == 0 && currentFirstVisibleItem == 0) {
                    LOG.info("UP!");
                    directionListener.onScrollUp();
                    return;
                }
                LOG.info("deltas -> " + deltas);
                if (deltas > 0) {
                    LOG.info("DOWN!");
                    directionListener.onScrollDown();
                } else if (deltas < 0) {
                    LOG.info("UP!");
                    directionListener.onScrollUp();
                }
            }

            @Override
            public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            }
        };

        this.listView.setOnScrollListener(scrollListener);
    }
}
