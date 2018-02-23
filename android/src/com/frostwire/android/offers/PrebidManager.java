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

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.util.Logger;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubView;

import org.prebid.mobile.core.AdUnit;
import org.prebid.mobile.core.BannerAdUnit;
import org.prebid.mobile.core.Prebid;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 2/22/18.
 */


public final class PrebidManager {
    private final static Logger LOG = Logger.getLogger(PrebidManager.class);
    private final String ACCOUNT_ID = "01e786a8-b070-4fb3-a21f-a76866f15c80";
    private boolean initialized = false;
    private boolean enabled = true;
    private final ArrayList<AdUnit> adUnits;
    private final HashMap<Placement, AdUnit> placementAdUnitHashMap;

    private final static Object initLock = new Object();
    private static PrebidManager manager = null;

    public enum Placement {
        SEARCH_HEADER_BANNER, //320x50 (Test)
        AUDIO_PLAYER_BANNER, //300x250 (Test)
        PREVIEW_BANNER_BIG, //300x250
        PREVIEW_BANNER_SMALL, //320x50
        INTERSTITIAL
    }

    public static PrebidManager getInstance(final Context applicationContext) {
        synchronized (initLock) {
            if (manager == null) {
                LOG.info("Creating PrebidManager singleton");
                manager = new PrebidManager(applicationContext);
            }
        }
        return manager;
    }

    private PrebidManager(final Context applicationContext) {
        adUnits = new ArrayList<>();
        placementAdUnitHashMap = new HashMap<>();
        if (Offers.disabledAds()) {
            enabled = false;
            initialized = true;
            LOG.info("PrebidManager initialization aborted. Offers disabled");
            return;
        }
        if (!UIUtils.diceRollPassesThreshold(ConfigurationManager.instance(), Constants.PREF_KEY_GUI_PREBID_THRESHOLD)) {
            enabled = false;
            initialized = true;
            return;
        }
        initAdUnits();
        initializePrebid(applicationContext);
    }

    public boolean initialized() {
        return initialized;
    }

    public boolean enabled() {
        return enabled;
    }

    private void initAdUnits() {
        // TODO: Verify code, configId, banner type, dimensions
        BannerAdUnit searchHeaderAdUnit = new BannerAdUnit("5823281", "5823281");
        searchHeaderAdUnit.addSize(320, 50);
        adUnits.add(searchHeaderAdUnit);
        placementAdUnitHashMap.put(Placement.SEARCH_HEADER_BANNER, searchHeaderAdUnit);
        BannerAdUnit audioPlayerAdUnit = new BannerAdUnit("5823300", "5823300");
        audioPlayerAdUnit.addSize(300, 250);
        adUnits.add(audioPlayerAdUnit);
        placementAdUnitHashMap.put(Placement.AUDIO_PLAYER_BANNER, searchHeaderAdUnit);
        BannerAdUnit previewBannerAdUnit = new BannerAdUnit("5823309", "5823309");
        previewBannerAdUnit.addSize(300, 250); // horizontal video preview
        previewBannerAdUnit.addSize(320, 50); // vertical audio/video preview
        adUnits.add(previewBannerAdUnit);
        placementAdUnitHashMap.put(Placement.PREVIEW_BANNER_BIG, searchHeaderAdUnit);
    }

    public void onBannerLoaded(final Context context, final MoPubView banner, final Placement placement) {
        if (!manager.initialized()) {
            LOG.info("onBannerLoaded: aborted, Prebid not ready yet for attachBids");
            return;
        } else if (!enabled()) {
            LOG.info("onBannerLoaded: aborted, Prebid disabled");
            return;
        }
        AdUnit adUnit = getAdUnit(placement);
        if (adUnit != null) {
            Prebid.attachBids(banner, adUnit.getConfigId(), context);
            LOG.info("onBannerLoaded: Prebid.attachBids invoked on placement <" + placement + ">");
        } else {
            LOG.warn("onBannerLoaded: Prebid.attachBids not invoked, invalid placement <" + placement + ">");
        }
    }

    public void onBannerFailed(Context context, MoPubView banner, Placement placement, MoPubErrorCode errorCode) {
        if (!manager.initialized()) {
            LOG.info("onBannerFailed: aborted, Prebid not ready yet for attachBids");
            return;
        } else if (!enabled()) {
            LOG.info("onBannerFailed: aborted, Prebid disabled");
            return;
        }
        AdUnit adUnit = getAdUnit(placement);
        if (adUnit != null) {
            Prebid.attachBids(banner, adUnit.getConfigId(), context);
            LOG.info("onBannerFailed: Prebid.attachBids invoked for placement <" + placement + ">");
        } else {
            LOG.warn("onBannerFailed: Prebid.attachBids not invoked, invalid placement <" + placement + ">");
        }
    }

    private AdUnit getAdUnit(final Placement placement) {
        return placementAdUnitHashMap.get(placement);
    }

    private void initializePrebid(Context applicationContext) {
        if (initialized) {
            return;
        }
        if (adUnits == null || adUnits.isEmpty()) {
            LOG.warn("Check your logic, adUnits can't be empty");
            return;
        }
        try {
            // It's ok to do this in the main thread.
            // The expensive server operation is done in a background executor inside Prebid
            Prebid.init(applicationContext, adUnits, ACCOUNT_ID, Prebid.AdServer.MOPUB, Prebid.Host.APPNEXUS);
            initialized = true;
            enabled = true;
        } catch (Throwable t) {
            initialized = false;
            t.printStackTrace();
        }
    }

}
