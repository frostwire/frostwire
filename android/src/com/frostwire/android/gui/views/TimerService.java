/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2022, FrostWire(R). All rights reserved.
 *
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

        SystemUtils.postToUIThread(new TimerTask(subscription, interval), interval);
        return subscription;
    }

    public static void reSubscribe(TimerObserver observer, TimerSubscription mTimerSubscription, int intervalSec) {
        mTimerSubscription.setObserver(observer);
        //LOG.info("reSubscribe(mTimerSubscription=@" + mTimerSubscription.hashCode() + ", intervalSec=" + intervalSec + ")");
        long intervalInMs = intervalSec * 1000L;
        SystemUtils.postToUIThread(new TimerTask(mTimerSubscription, intervalInMs), intervalInMs);
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
                SystemUtils.postToUIThread(this, interval);
            }
        }
    }
}
