/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.views;

import java.lang.ref.WeakReference;

import com.frostwire.logging.Logger;
import com.frostwire.util.Ref;

/**
 * 
 * @author gubatron
 * @author aldenml
 * 
 */
public final class TimerSubscription {

    private static final Logger LOG = Logger.getLogger(TimerSubscription.class);

    private final WeakReference<TimerObserver> observerRef;

    private boolean unsubscribed;

    public TimerSubscription(TimerObserver observer) {
        this.observerRef = Ref.weak(observer);

        this.unsubscribed = false;
    }

    public boolean isUnsubscribed() {
        return unsubscribed || !Ref.alive(observerRef);
    }

    public void unsubscribe() {
        unsubscribed = true;
        Ref.free(observerRef);
    }

    public void onTime() {
        if (!isUnsubscribed()) { // double check of unsubscribed intentional 
            try {
                observerRef.get().onTime();
            } catch (Throwable e) {
                unsubscribe();
                LOG.error("Error notifying observer, performed automatic unsubscribe", e);
            }
        }
    }
}
