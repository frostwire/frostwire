/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.mopub.mobileads;

import android.app.Activity;
import android.content.Context;

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.offers.Offers;
import com.frostwire.util.Logger;

import java.util.Map;

import io.presage.Presage;
import io.presage.common.AdConfig;
import io.presage.interstitial.PresageInterstitial;
import io.presage.interstitial.PresageInterstitialCallback;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on Apr/10/2017 - ogury 2.0.5
 *         Updated Apr/26/2017 - ogury 2.1.1
 *         Updated Sept/06/2017 - ogury 2.2.1 (beta)
 */
@SuppressWarnings("unused") // it is invoked via reflection by MoPub
public final class OguryInterstitialAdapter extends CustomEventInterstitial {

    private static final Logger LOG = Logger.getLogger(OguryInterstitialAdapter.class);

    private static boolean OGURY_STARTED = false;
    private static boolean OGURY_ENABLED = false;

    private PresageInterstitial placement = null;
    private CustomEventInterstitialListener interstitialListener;

    public OguryInterstitialAdapter() {
        // this class should be created only once by the mopub framework
        // both OGURY_STARTED and OGURY_ENABLED are static to minimize the
        // risks in case of getting in a multithreaded environment
        super();
        OGURY_ENABLED = UIUtils.diceRollPassesThreshold(ConfigurationManager.instance(), Constants.PREF_KEY_GUI_OGURY_THRESHOLD);
        LOG.info("OGURY_ENABLED=" + OGURY_ENABLED);
    }

    @Override
    protected void loadInterstitial(Context context,
                                    CustomEventInterstitialListener customEventInterstitialListener,
                                    Map<String, Object> localExtras,
                                    Map<String, String> serverExtras) {
        if (customEventInterstitialListener == null) {
            // this should not happen, but just in case
            LOG.error("loadInterstitial() aborted. CustomEventInterstitialListener was null.");
            return;
        }

        if (!OGURY_ENABLED) {
            LOG.info("loadInterstitial() aborted, ogury not enabled.");
            return;
        }

        if (Offers.disabledAds()) {
            OGURY_ENABLED = false;
            LOG.info("loadInterstitial() aborted, ogury not enabled. PlayStore reports no ads");
            return;
        }

        interstitialListener = customEventInterstitialListener;
        LOG.info("loadInterstitial() starting ogury");
        startOgury(context); // starts only once

        if (serverExtras != null && serverExtras.size() > 1) {
            Object firstKey = serverExtras.keySet().toArray()[0];
            Object valueFirstKey = serverExtras.get(firstKey);
            String adUnit = "" + valueFirstKey;
            AdConfig adConfig = new AdConfig(adUnit);

            if (!adUnit.equals("")) {
                try {
                    if (context instanceof Activity) {
                        placement = new PresageInterstitial((Activity) context, adConfig);
                    }
                } catch (IllegalArgumentException invalidAdUnitException) {
                    placement = null;
                }
            }
        } else {
            if (context instanceof Activity) {
                try {
                    placement = new PresageInterstitial((Activity) context);
                } catch (Throwable t) {
                    LOG.error(t.getMessage(), t);
                    placement = null;
                }
            }
        }

        if (placement != null) {
            placement.setInterstitialCallback(new OguryPresageInterstitialCallback(interstitialListener));
            placement.load();
        }
    }

    @Override
    protected void showInterstitial() {
        if (!OGURY_ENABLED) {
            LOG.info("showInterstitial() aborted, ogury disabled.");
            return;
        }

        if (interstitialListener == null) {
            // this should not happen at this point, but just in case
            LOG.error("showInterstitial() aborted. CustomEventInterstitialListener was null.");
            return;
        }

        if (placement != null && placement.isLoaded()) {
            try {
                LOG.info("showInterstitial() Showing Ogury-Mopub interstitial");
                placement.show();
            } catch (Throwable throwable) {
                LOG.error(throwable.getMessage(), throwable);
            }
        } else {
            LOG.info("showInterstitial() Ogury-Mopub canShow()=false, ad not loaded yet");
            interstitialListener.onInterstitialFailed(MoPubErrorCode.NETWORK_NO_FILL);

        }
    }

    @Override
    protected void onInvalidate() {
        placement = null;
    }

    private static void startOgury(Context context) {
        if (!OGURY_ENABLED || OGURY_STARTED) {
            if (OGURY_STARTED) {
                LOG.info("startOgury(): Ogury already started, all good");
            }
            return;
        }
        try {
            OGURY_STARTED = true;
            // presage internally picks the application context
            Presage.getInstance().start("269485", context);
            LOG.info("startOgury: Ogury started from Mopub-Ogury adapter");
        } catch (Throwable e) {
            OGURY_STARTED = false;
            LOG.error("startOgury: Could not start Ogury from Mopub-Ogury adapter", e);
        }
    }

    private static final class OguryPresageInterstitialCallback implements PresageInterstitialCallback {

        private final CustomEventInterstitialListener mopubListener;

        private OguryPresageInterstitialCallback(CustomEventInterstitialListener mopubListener) {
            this.mopubListener = mopubListener;
        }

        @Override
        public void onAdAvailable() {
        }

        @Override
        public void onAdNotAvailable() {
            if (mopubListener != null) {
                mopubListener.onInterstitialFailed(MoPubErrorCode.NETWORK_NO_FILL);
            }
        }

        @Override
        public void onAdLoaded() {
            if (mopubListener != null) {
                mopubListener.onInterstitialLoaded();
            }
        }

        @Override
        public void onAdNotLoaded() {
        }

        @Override
        public void onAdClosed() {
            if (mopubListener != null) {
                mopubListener.onInterstitialDismissed();
            }
        }

        @Override
        public void onAdError(int code) {
            if (mopubListener != null) {
                mopubListener.onInterstitialFailed(MoPubErrorCode.NETWORK_INVALID_STATE);
            }
        }

        @Override
        public void onAdDisplayed() {
            if (mopubListener != null) {
                LOG.info("ogury displayed from mopub adapter");
                mopubListener.onInterstitialShown();
            }
        }
    }
}
