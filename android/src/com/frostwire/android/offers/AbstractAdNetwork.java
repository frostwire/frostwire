/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.offers;

import android.content.Context;

import com.frostwire.util.Logger;

/**
 * Created on 11/9/16.
 * @author aldenml
 * @author gubatron
 */
public abstract class AbstractAdNetwork implements AdNetwork {

    private static final Logger LOG = Logger.getLogger(AbstractAdNetwork.class);

    private long lastStopped = -1;
    private boolean started;

    @Override
    public void stop(Context context) {
        started = false;
        lastStopped = System.currentTimeMillis();
        LOG.info(getClass().getSimpleName() + ".stop() - " + getShortCode() + " stopped");
    }

    @Override
    public void enable(boolean enabled) {
        Offers.AdNetworkHelper.enable(this, enabled);
    }

    @Override
    public boolean enabled() {
        return Offers.AdNetworkHelper.enabled(this);
    }

    @Override
    public final boolean started() {
        return started;
    }

    public final void start() {
        started = true;
        LOG.info(getClass().getSimpleName() + ".start() - " + getShortCode() + " started");
    }

    @Override
    public int hashCode() {
        return getInUsePreferenceKey().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        try {
            AdNetwork otherNetwork = (AdNetwork) obj;
            return otherNetwork.getShortCode().equals(this.getShortCode());
        } catch (Throwable t) {
            return false;
        }
    }

    protected final boolean abortInitialize(Context context) {
        if (!enabled()) {
            if (!started()) {
                LOG.info(getClass().getSimpleName() + " initialize(): aborted. AdNetwork Not enabled.");
            } else {
                // initialize can be called multiple times, we may have to stop
                // this network if we started it using a default value.
                stop(context);
            }
            return true;
        } else if (started()) {
            LOG.info(getClass().getSimpleName() + " initialize(): aborted. AdNetwork already initialized.");
            return true;
        } else if (!started()) {
            // We might have been recently stopped and they're trying to request our
            // initialization by mistake.
            // e.g. Issue with MainActivity.onResume() called before an
            // ad-listener's onDismiss() is called. MoPub has this problem.
            long now = System.currentTimeMillis();
            boolean tooEarlyToReinizialize = now - lastStopped < 2000;
            LOG.info(getClass().getSimpleName() + " abortInitialize()? now - lastStopped = " + (now - lastStopped) + " < 2000 == " + tooEarlyToReinizialize);
            return tooEarlyToReinizialize;
        }
        return false;
    }


}
