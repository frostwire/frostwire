/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.mopub.mobileads;

import android.app.Activity;
import android.content.Context;

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.util.Logger;

import java.util.Map;
import java.util.Random;

import io.presage.IADHandler;
import io.presage.Presage;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 * Created on 4/10/2017 - ogury 2.0.5
 * Updated 04/26/2017 - ogury 2.1.1
 */

public final class OguryInterstitialAdapter extends CustomEventInterstitial {

    private CustomEventInterstitialListener interstitialListener;
    private static boolean OGURY_STARTED = false;
    private static boolean OGURY_ENABLED = false;
    private static final Logger LOG = Logger.getLogger(OguryInterstitialAdapter.class);
    private final OguryIADHandler oguryInterstitialHandler;

    public OguryInterstitialAdapter() {
        super();
        oguryInterstitialHandler = new OguryIADHandler();
        int oguryThreshold = ConfigurationManager.instance().getInt(Constants.PREF_KEY_GUI_OGURY_THRESHOLD);
        int diceRoll = new Random().nextInt(100) + 1;
        OGURY_ENABLED = diceRoll < oguryThreshold;
        LOG.info("OguryInterstitialAdapter() - OGURY_ENABLED -> " + OGURY_ENABLED + " (dice roll: " + diceRoll + " < threshold: " + oguryThreshold + ")");
    }

    @Override
    protected void loadInterstitial(Context context, CustomEventInterstitialListener customEventInterstitialListener, Map<String, Object> map, Map<String, String> map1) {
        if (!OGURY_ENABLED) {
            LOG.info("OguryInterstitialAdapter.loadInterstitial() aborted, ogury disabled.");
            return;
        }
        if (customEventInterstitialListener == null) {
            LOG.error("OguryInterstitialAdapter.loadInterstitial() aborted. CustomEventInterstitialListener was null.");
            return;
        }
        startOgury(context); // starts only once
        interstitialListener = customEventInterstitialListener;
        Presage.getInstance().load(oguryInterstitialHandler);
    }

    @Override
    protected void showInterstitial() {
        if (OGURY_ENABLED && Presage.getInstance().canShow()) {
            Presage.getInstance().adToServe(oguryInterstitialHandler);
            LOG.info("Showing Ogury-Mopub interstitial");
            interstitialListener.onInterstitialShown();
        } else {
            LOG.info("Ogury-Mopub show interstitial failed, ad not loaded yet");
        }
    }

    @Override
    protected void onInvalidate() {
        interstitialListener = null;
    }

    private void startOgury(Context context) {
        if (OGURY_STARTED || !OGURY_ENABLED) {
            return;
        }
        try {
            // presage internally picks the application context
            Presage.getInstance().setContext(context);
            Presage.getInstance().start();
            OGURY_STARTED = true;
            LOG.info("Ogury started from Mopub-Ogury adapter");
        } catch (Throwable e) {
            OGURY_STARTED = false;
            LOG.error("Could not start Ogury from Mopub-Ogury adapter", e);
        }
    }

    private class OguryIADHandler implements IADHandler {

        @Override
        public void onAdAvailable() {
        }

        @Override
        public void onAdNotAvailable() {
            if (interstitialListener == null) {
                return;
            }
            interstitialListener.onInterstitialFailed(MoPubErrorCode.NETWORK_NO_FILL);
        }

        @Override
        public void onAdLoaded() {
            if (interstitialListener == null) {
                return;
            }
            interstitialListener.onInterstitialLoaded();
        }

        @Override
        public void onAdClosed() {
            if (interstitialListener == null) {
                return;
            }
            interstitialListener.onInterstitialDismissed();
        }
        @Override
        public void onAdError(int code) {
            if (interstitialListener == null) {
                return;
            }
            interstitialListener.onInterstitialFailed(MoPubErrorCode.NETWORK_NO_FILL);
        }

        @Override
        public void onAdDisplayed() {
            if (interstitialListener == null) {
                return;
            }
            interstitialListener.onInterstitialShown();
        }
    }
}
