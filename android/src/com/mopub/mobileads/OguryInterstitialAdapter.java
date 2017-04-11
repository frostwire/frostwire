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

import com.frostwire.util.Logger;

import java.util.Map;

import io.presage.IADHandler;
import io.presage.Presage;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 * Created on 4/10/17.
 */

public final class OguryInterstitialAdapter extends CustomEventInterstitial {

    private CustomEventInterstitialListener interstitialListener;
    private static boolean OGURY_STARTED = false;
    private static final Logger LOG = Logger.getLogger(OguryInterstitialAdapter.class);

    public OguryInterstitialAdapter() {
        super();
    }

    @Override
    protected void loadInterstitial(Context context, CustomEventInterstitialListener customEventInterstitialListener, Map<String, Object> map, Map<String, String> map1) {
        startOgury(context); // starts only once

        interstitialListener = customEventInterstitialListener;

        Presage.getInstance().adToServe(new IADHandler() {

            @Override
            public void onAdNotFound() {
                interstitialListener.onInterstitialFailed(MoPubErrorCode.NETWORK_NO_FILL);
            }

            @Override
            public void onAdFound() {
                interstitialListener.onInterstitialLoaded();
            }

            @Override
            public void onAdClosed() {
                interstitialListener.onInterstitialDismissed();
            }
            @Override
            public void onAdError(int code) {
                interstitialListener.onInterstitialFailed(MoPubErrorCode.NETWORK_NO_FILL);
            }

            @Override
            public void onAdDisplayed() {
                interstitialListener.onInterstitialShown();
            }
        });
    }

    @Override
    protected void showInterstitial() {
        LOG.info("Showing Ogury-Mopub interstitial");
        interstitialListener.onInterstitialShown();
    }

    @Override
    protected void onInvalidate() {
        interstitialListener = null;
    }

    private void startOgury(Context context) {
        if (OGURY_STARTED) {
            return;
        }
        Context baseContext = context.getApplicationContext();
        if (context instanceof Activity) {
            baseContext = ((Activity) context).getBaseContext();
        }
        try {
            Presage.getInstance().setContext(baseContext);
            Presage.getInstance().start();
            OGURY_STARTED = true;
            LOG.info("Ogury started from Mopub-Ogury adapter");
        } catch (Throwable t) {
            OGURY_STARTED = false;
            LOG.warn("Could not start Ogury from Mopub-Ogury adapter", t);
        }
    }
}
