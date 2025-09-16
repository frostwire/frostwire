/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

import androidx.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created on 11/23/16.
 *
 * @author gubatron
 * @author aldenml
 */

public final class DirectionDetectorScrollListener implements AbsListView.OnScrollListener {
    private final ScrollDirectionVotes votes = new ScrollDirectionVotes();
    private final ExecutorService threadPool;
    private final ScrollDirectionListener scrollDirectionListener;
    private int lastFirstVisibleItem;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicBoolean inMotion = new AtomicBoolean(false);
    private final AtomicBoolean enabledScrollDown = new AtomicBoolean(true);
    private final AtomicBoolean enabledScrollUp = new AtomicBoolean(true);

    public DirectionDetectorScrollListener(final ScrollDirectionListener scrollDirectionListener, final ExecutorService threadPool) {
        this.threadPool = threadPool;
        this.scrollDirectionListener = scrollDirectionListener;
    }

    @Override
    public void onScrollStateChanged(AbsListView absListView, int scrollState) {
        switch (scrollState) {
            case SCROLL_STATE_IDLE:
                inMotion.set(false);
                // wait a little longer to call victory
                submitRunnable(400, () -> {
                    if (!inMotion.get()) {
                        onIdle();
                    }
                });
                break;
            case SCROLL_STATE_FLING:
                inMotion.set(true);
                onFling();
                break;
            case SCROLL_STATE_TOUCH_SCROLL:
                inMotion.set(true);
                onTouchScroll();
                break;
        }
    }

    private void submitRunnable(long delay, Runnable r) {
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (threadPool != null) {
            threadPool.execute(r);
        } else {
            new Thread(r).start();
        }
    }

    private void onFling() {
        checkCandidates();
    }

    private void onTouchScroll() {
        checkCandidates();
    }

    private void onIdle() {
        votes.reset();
    }

    @Override
    public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (!enabled.get() || scrollDirectionListener == null) {
            return;
        }
        boolean scrollingDown = firstVisibleItem > lastFirstVisibleItem;
        boolean scrollingUp = firstVisibleItem < lastFirstVisibleItem;
        lastFirstVisibleItem = firstVisibleItem;
        if (enabledScrollDown.get() && scrollingDown) {
            votes.down();
        } else if (enabledScrollUp.get() && scrollingUp) {
            votes.up();
        }
        checkCandidates();
    }

    /**
     * sets the given flag to false temporarily for interval milliseconds
     */
    private void disable(final long interval, final AtomicBoolean flag) {
        // stop listening for 500 seconds to be able to detect rate of change.
        flag.set(false);
        submitRunnable(interval, () -> flag.set(true));
    }

    private void checkCandidates() {
        int MIN_VOTES = 4;
        if (votes.total() >= MIN_VOTES) {
            // democratic check
            if (votes.delta() > votes.total() * 0.5) {
                boolean scrollingUp = votes.ups() > votes.downs();
                votes.reset();
                long DISABLE_INTERVAL_ON_EVENT = 100L;
                disable(DISABLE_INTERVAL_ON_EVENT,
                        scrollingUp ? enabledScrollUp : enabledScrollDown);
                if (scrollingUp) {
                    scrollDirectionListener.onScrollUp();
                } else {
                    scrollDirectionListener.onScrollDown();
                }
            }
        }
    }

    public interface ScrollDirectionListener {
        void onScrollUp();
        void onScrollDown();
    }

    private static final class ScrollDirectionVotes {
        private byte ups;
        private byte downs;

        ScrollDirectionVotes() {
            reset();
        }

        void reset() {
            ups = downs = 0;
        }

        void up() {
            ups++;
        }

        void down() {
            downs++;
        }

        byte total() {
            return (byte) (ups + downs);
        }

        byte delta() {
            return (byte) Math.abs(ups - downs);
        }

        byte ups() {
            return ups;
        }

        byte downs() {
            return downs;
        }

        @NonNull
        public String toString() {
            return "ScrollDirectionVotes: total=" + total() + " ups=" + ups + " downs=" + downs + " delta=" + delta();
        }
    }
}

