/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa)
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
import android.support.annotation.NonNull;

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import org.prebid.mobile.core.AdUnit;
import org.prebid.mobile.core.BannerAdUnit;
import org.prebid.mobile.core.Prebid;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 2/22/18.
 */


public final class PrebidInitializer {
    private final static Logger LOG = Logger.getLogger(PrebidInitializer.class);
    private boolean initialized = false;
    private boolean enabled = true;
    private final ArrayList<AdUnit> adUnits;

    private final static Object initLock = new Object();
    private static PrebidInitializer initializer = null;

    public static PrebidInitializer getInstance(Context applicationContext) {
        synchronized (initLock) {
            if (initializer == null) {
                initializer = new PrebidInitializer(applicationContext);
            }
        }
        return initializer;
    }

    private PrebidInitializer(final Context applicationContext) {
        adUnits = new ArrayList<>();

        if (Offers.disabledAds()) {
            enabled = false;
            initialized = true;
            LOG.info("PrebidInitializer initialization aborted. Offers disabled");
            return;
        }

        if (!UIUtils.diceRollPassesThreshold(ConfigurationManager.instance(), Constants.PREF_KEY_GUI_PREBID_THRESHOLD)) {
            enabled = false;
            initialized = true;
            return;
        }

        initAdUnits();

        Engine.instance().getThreadPool().execute(new BackgroundInitializer(applicationContext, adUnits, this));
    }

    public boolean initialized() {
        return initialized;
    }

    public boolean enabled() {
        return enabled;
    }

    private void initAdUnits() {
        // TODO: Verify code, configId, banner type, dimensions
        adUnits.add(new BannerAdUnit("5823281","5823281"));
        adUnits.add(new BannerAdUnit("5823300","5823300"));
        adUnits.add(new BannerAdUnit("5823309","5823309"));
    }

    /** MIGHT GO, useful for testing now, Returns a copy of the ad units */
    public ArrayList<AdUnit> getAdUnits() {
        return new ArrayList<>(adUnits);
    }

    // TODO: Have enums for placements and have getAdUnit(PlacementEnum) method

    private static class BackgroundInitializer implements Runnable {
        private final String ACCOUNT_ID = "01e786a8-b070-4fb3-a21f-a76866f15c80";
        private final WeakReference<Context> ctxRef;
        private final ArrayList<AdUnit> adUnits;
        private final PrebidInitializer initializer;

        BackgroundInitializer(@NonNull Context applicationContext,
                              @NonNull ArrayList<AdUnit> adUnits,
                              @NonNull  PrebidInitializer initializer) {
            ctxRef = Ref.weak(applicationContext);
            this.adUnits = adUnits;
            this.initializer = initializer;
        }

        @Override
        public void run() {
            if (initializer.initialized) {
                return;
            }
            if (!Ref.alive(ctxRef)) {
                initializer.initialized = false;
                return;
            }
            try {
                Prebid.init(ctxRef.get(), adUnits, ACCOUNT_ID, Prebid.AdServer.MOPUB, Prebid.Host.APPNEXUS);
                initializer.initialized = true;
            } catch (Throwable t) {
                initializer.initialized = false;
                t.printStackTrace();
            }
        }
    }
}
