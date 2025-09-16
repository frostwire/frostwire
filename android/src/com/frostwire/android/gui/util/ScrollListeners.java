/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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
