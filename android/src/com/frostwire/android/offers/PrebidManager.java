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
import org.prebid.mobile.core.TargetingParams;

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
    private boolean initialized = false;
    private boolean enabled = true;
    private final ArrayList<AdUnit> adUnits;
    private final HashMap<Placement, AdUnit> placementAdUnitHashMap;

    private final static Object initLock = new Object();
    private static PrebidManager manager = null;

    public enum Placement {
        SEARCH_HEADER_BANNER_320_50(     "13003736", "7eabcb1d-7376-4ab5-b85c-ce1c0cb12ff1", 320, 250),
        AUDIO_PLAYER_BANNER_300_250(     "13003143", "e33c1226-2f3a-4ceb-a916-b03f3f7636c0", 300, 250),
        PREVIEW_BANNER_LANDSCAPE_300_250("13003723", "70c3cef1-5040-45e1-92dc-3136229f233c", 300, 250),
        PREVIEW_BANNER_PORTRAIT_320_50  ("13003738", "f51741c4-3a6e-4705-a6b9-0acad35bcff9", 320, 50);

        //INTERSTITIAL_MOBILE("13003741","<config pending>"),
        //INTERSTITIAL_TABLET("13003742","<config pending>")

        private final String placementIdCode;
        private final String configId;
        private final int width;
        private final int height;

        Placement(String placementIdCode, String configId, int width, int height) {
            this.placementIdCode = placementIdCode;
            this.configId = configId;
            this.width = width;
            this.height = height;
        }
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
            LOG.info("PrebidManager initialization aborted. Dice roll failed");
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
        initBanner(Placement.SEARCH_HEADER_BANNER_320_50);
        initBanner(Placement.AUDIO_PLAYER_BANNER_300_250);
        initBanner(Placement.PREVIEW_BANNER_PORTRAIT_320_50);
        initBanner(Placement.PREVIEW_BANNER_LANDSCAPE_300_250);
    }

    private void initBanner(Placement placement) {
        BannerAdUnit bannerAdUnit = new BannerAdUnit(placement.placementIdCode, placement.configId);
        bannerAdUnit.addSize(placement.width, placement.height);
        adUnits.add(bannerAdUnit);
        placementAdUnitHashMap.put(placement, bannerAdUnit);
    }

    // TODO: Interstitial Placement support -> initInterstitial(Placement)

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
            Prebid.attachBids(banner, adUnit.getCode(), context);
            LOG.info("onBannerLoaded: Prebid.attachBids invoked on placement <" + placement + ">");
        } else {
            LOG.warn("onBannerLoaded: Prebid.attachBids not invoked, invalid placement <" + placement + ">");
        }
    }

    public void onBannerFailed(final Context context, final MoPubView banner, final Placement placement, MoPubErrorCode errorCode) {
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
            LOG.info("onBannerFailed: Prebid.attachBids invoked for placement <" + placement + ">, errorCode: " + errorCode);
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
            String ACCOUNT_ID = "01e786a8-b070-4fb3-a21f-a76866f15c80";
            TargetingParams.setLocationDecimalDigits(2);
            TargetingParams.setLocationEnabled(true);
            Prebid.init(applicationContext, adUnits, ACCOUNT_ID, Prebid.AdServer.MOPUB, Prebid.Host.APPNEXUS);
            initialized = true;
            enabled = true;
            LOG.info("initializePrebid(): success");
        } catch (Throwable t) {
            initialized = false;
            t.printStackTrace();
        }
    }

}
