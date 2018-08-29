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
import com.google.android.gms.ads.MobileAds;
import com.mopub.mobileads.GooglePlayServicesBanner;
import com.mopub.mobileads.GooglePlayServicesInterstitial;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This isn't a real FrostWire AdNetwork as it does not yet extend AbstractAdNetwork.
 * For now it's only used to start the Google MobileAds SDK since we're integrating AdMob
 * via MoPub adapters. See {@link GooglePlayServicesInterstitial} and {@link GooglePlayServicesBanner}
 * <p>
 * We'll only implement showing the ads and handling all their events here if we ever need AdMob to
 * act on its own in our in-house AdNetwork waterfall
 *
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 * Created on 8/1/18.
 */

public final class AdMobAdNetwork {
    private static final Logger LOG = Logger.getLogger(AdMobAdNetwork.class);
    private static final AtomicBoolean ADMOB_STARTED = new AtomicBoolean(false);
    private static final String ADMOB_APP_ID = "ca-app-pub-0657224435269327~1839292928";

    /*
     * app ID:
     ca-app-pub-0657224435269327~1839292928

     In case we need this later, here are the Ad Unit Ids as defined in admob

     ad unit IDs:
     300x250 Music Player: ca-app-pub-0657224435269327/2906648371
     300x250 Search: ca-app-pub-0657224435269327/9743110343
     300x250 Video Preview: ca-app-pub-0657224435269327/7336438859
     320x50 Searchca-app-pub-0657224435269327/5768206318
     320x50 Video Previewca-app-pub-0657224435269327/4323154909
     Interstitial (Main)ca-app-pub-0657224435269327/9902784644
     */

    public static void start(Context context) {
        if (ADMOB_STARTED.compareAndSet(false, true)) {
            try {
                MobileAds.initialize(context, ADMOB_APP_ID);
                LOG.info("start(): AdMobAdNetwork started");
            } catch (Throwable t) {
                LOG.error("start(): Could not initialize AdMobAdNetwork", t);
                ADMOB_STARTED.set(false);
            }
        } else {
            LOG.info("start(): AdMobAdNetwork already started, all good");
        }
    }
}
