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
import com.frostwire.android.gui.services.EngineService;
import com.frostwire.util.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created on 11/23/16.
 *
 * @author gubatron
 * @author aldenml
 */

public final class DirectionDetectorScrollListener {
    @SuppressWarnings("unused")
    private static final Logger LOG = Logger.getLogger(DirectionDetectorScrollListener.class);

    private static final class EventCandidates {
        private byte ups;
        private byte downs;
        EventCandidates() { reset(); }
        void reset() { ups = downs = 0; }
        void up() {          ups++;
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
        public String toString() {
            return "EventCandidates: total=" + total() + " ups=" + ups + " downs=" + downs + " delta=" + delta();
        }
    }

    public static AbsListView.OnScrollListener createOnScrollListener(final ScrollDirectionListener scrollDirectionListener) {
        return new AbsListView.OnScrollListener() {
            private int lastFirstVisibleItem;
            private AtomicBoolean enabled = new AtomicBoolean(true);
            private AtomicBoolean inMotion = new AtomicBoolean(false);
            private AtomicBoolean enabledScrolldown = new AtomicBoolean(true);
            private AtomicBoolean enabledScrollup = new AtomicBoolean(true);
            private final EventCandidates candidates = new EventCandidates();
            private final int MIN_CANDIDATES = 4;
            private final long DISABLE_INTERVAL_ON_EVENT = 100L;


            @Override
            public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                switch (scrollState) {
                    case SCROLL_STATE_IDLE:
                        inMotion.set(false);

                        // We're not truly idle unless we're this way a little longer.
                        Runnable r = new Runnable() {
                            public void run() {
                                try {
                                    Thread.currentThread().sleep(400);
                                } catch (Throwable ignored) {}
                                if (!inMotion.get()) {
                                    onIdle();
                                }
                            }
                        };
                        Engine.instance().getThreadPool().submit(r);
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

            private void onFling() {
                checkCandidates();
            }

            private void onTouchScroll() {
                checkCandidates();
            }

            private void onIdle() {
                candidates.reset();
            }

            @Override
            public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!enabled.get() || scrollDirectionListener == null) {
                    return;
                }

                boolean scrollingDown = firstVisibleItem > lastFirstVisibleItem; //firstVisibleItem > 4 && firstVisibleItem >= lastFirstVisibleItem;
                boolean scrollingUp = firstVisibleItem < lastFirstVisibleItem; //firstVisibleItem < (totalItemCount - visibleItemCount) && firstVisibleItem <= lastFirstVisibleItem;
                lastFirstVisibleItem = firstVisibleItem;
                if (enabledScrolldown.get() && scrollingDown) {
                    candidates.down();
                } else if (enabledScrollup.get() && scrollingUp) {
                    candidates.up();
                }

                checkCandidates();
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

            private void checkCandidates() {
                if (candidates.total() >= MIN_CANDIDATES) {
                    // democratic check
                    if (candidates.delta() > candidates.total() * 0.5) {
                        boolean scrollingUp = candidates.ups() > candidates.downs();
                        candidates.reset();
                        disable(DISABLE_INTERVAL_ON_EVENT,
                                scrollingUp ? enabledScrollup : enabledScrolldown);
                        if (scrollingUp) {
                            scrollDirectionListener.onScrollUp();
                        } else {
                            scrollDirectionListener.onScrollDown();
                        }
                    }
                }
            }

        };
    }
}
