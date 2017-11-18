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

import android.os.Handler;
import android.os.Looper;

/**
 * 
 * @author gubatron
 * @author aldenml
 * 
 */
public final class TimerService {

    private static final Handler handler = new Handler(Looper.getMainLooper());

    private TimerService() {
    }

    public static TimerSubscription subscribe(TimerObserver observer, int intervalSec) {
        TimerSubscription subscription = new TimerSubscription(observer);
        long interval = intervalSec * 1000;

        handler.postDelayed(new TimerTask(subscription, interval), interval);

        return subscription;
    }

    private static final class TimerTask implements Runnable {

        private final TimerSubscription subscription;
        private final long interval;

        public TimerTask(TimerSubscription subscription, long interval) {
            this.subscription = subscription;
            this.interval = interval;
        }

        @Override
        public void run() {
            if (subscription.isSubscribed()) {
                subscription.onTime();
                handler.postDelayed(this, interval);
            }
        }
    }
}
