/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

import com.frostwire.android.util.SystemUtils;

/**
 * @author gubatron
 * @author aldenml
 */
public final class TimerService {
    private TimerService() {
    }
    public static TimerSubscription subscribe(TimerObserver observer, int intervalSec) {
        TimerSubscription subscription = new TimerSubscription(observer);
        //LOG.info("subscribe(" + observer.getClass().getCanonicalName() + ") has created a new TimerSubscription@" + subscription.hashCode());
        long interval = intervalSec * 1000L;

        SystemUtils.postToUIThreadDelayed(new TimerTask(subscription, interval), interval);
        return subscription;
    }

    public static void reSubscribe(TimerObserver observer, TimerSubscription mTimerSubscription, int intervalSec) {
        mTimerSubscription.setObserver(observer);
        //LOG.info("reSubscribe(mTimerSubscription=@" + mTimerSubscription.hashCode() + ", intervalSec=" + intervalSec + ")");
        long intervalInMs = intervalSec * 1000L;
        SystemUtils.postToUIThreadDelayed(new TimerTask(mTimerSubscription, intervalInMs), intervalInMs);
    }

    private static final class TimerTask implements Runnable {
        private final TimerSubscription subscription;
        private final long interval;

        TimerTask(TimerSubscription subscription, long interval) {
            this.subscription = subscription;
            this.interval = interval;
        }

        @Override
        public void run() {
            if (subscription.isSubscribed()) {
                //LOG.info("TimerTask.run() TimerSubscription@" + subscription.hashCode() + " is still subscribed. Observer=" + subscription.observerClassName);
                subscription.onTime();
                SystemUtils.postToUIThreadDelayed(this, interval);
            }
        }
    }
}
