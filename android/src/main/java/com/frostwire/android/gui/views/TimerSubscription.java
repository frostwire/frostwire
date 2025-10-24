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

package com.frostwire.android.gui.views;

import android.app.Fragment;
import android.view.View;

import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * @author gubatron
 * @author aldenml
 */
public final class TimerSubscription {

    private static final Logger LOG = Logger.getLogger(TimerSubscription.class);

    private WeakReference<TimerObserver> observer;

    String observerClassName;

    private boolean unsubscribed;

    TimerSubscription(TimerObserver observer) {
        setObserver(observer);
    }

    void setObserver(TimerObserver observer) {
        this.observer = Ref.weak(observer);
        this.observerClassName = observer.getClass().getCanonicalName();
        this.unsubscribed = false;
    }

    public boolean isSubscribed() {
        if (!unsubscribed && !Ref.alive(observer)) {
            unsubscribe();
        }

        return !unsubscribed;
    }

    public void unsubscribe() {
        unsubscribed = true;
        Ref.free(observer);
    }

    public void onTime() {
        if (isSubscribed()) {
            try {
                if (Ref.alive(observer)) {
                    onTime(observer.get());
                } else {
                    unsubscribe();
                }
            } catch (Throwable e) {
                unsubscribe();
                LOG.error("Error notifying observer, performed automatic unsubscribe", e);
            }
        }
    }

    private static void onTime(TimerObserver observer) {
        boolean call = true;
        if (observer instanceof View) {
            // light version of visible check
            call = ((View) observer).getVisibility() != View.GONE;
        }
        if (observer instanceof Fragment) {
            call = ((Fragment) observer).isVisible();
            if (observer instanceof AbstractFragment) {
                call = !((AbstractFragment) observer).isPaused();
            }
        }
        if (observer instanceof AbstractActivity) {
            call = !((AbstractActivity) observer).isPaused();
        }
        if (call) {
            observer.onTime();
            //LOG.debug("ON TIME: class-" + observer.getClass().getName());
        }
    }
}
