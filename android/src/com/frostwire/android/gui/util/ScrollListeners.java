/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

/**
 * Created on 9/26/17.
 * @author Marcelinkaaa
 * @author gubatron
 * @author aldenml
 */

public final class ScrollListeners  {

    public static final class ComposedOnScrollListener implements AbsListView.OnScrollListener {
        private final AbsListView.OnScrollListener[] listeners;

        public ComposedOnScrollListener(AbsListView.OnScrollListener... listeners) {
            this.listeners = listeners;
        }

        @Override
        public void onScrollStateChanged(AbsListView absListView, int i) {
            if (listeners != null && listeners.length > 0) {
                for (AbsListView.OnScrollListener listener : listeners) {
                    try {
                        listener.onScrollStateChanged(absListView, i);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        @Override
        public void onScroll(AbsListView absListView, int i, int i1, int i2) {
            if (listeners != null && listeners.length > 0) {
                for (AbsListView.OnScrollListener listener : listeners) {
                    try {
                        listener.onScroll(absListView, i, i1, i2);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    public static class FastScrollDisabledWhenIdleOnScrollListener implements AbsListView.OnScrollListener {

        @Override
        public void onScrollStateChanged(AbsListView absListView, int scrollState) {
            absListView.setFastScrollEnabled(scrollState != SCROLL_STATE_IDLE);
        }

        @Override
        public void onScroll(AbsListView absListView, int i, int i1, int i2) {

        }
    }
}
