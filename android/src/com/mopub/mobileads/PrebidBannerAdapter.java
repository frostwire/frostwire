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


package com.mopub.mobileads;

import android.content.Context;

import com.frostwire.android.offers.PrebidInitializer;

import java.util.Map;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 2/22/18.
 */


public final class PrebidBannerAdapter extends CustomEventBanner {
    @Override
    protected void loadBanner(Context context, CustomEventBannerListener customEventBannerListener, Map<String, Object> localExtras, Map<String, String> serverExtras) {
        PrebidInitializer prebidInitializer = PrebidInitializer.getInstance(context);

        if (!prebidInitializer.initialized()) {
            // too early
            customEventBannerListener.onBannerFailed(MoPubErrorCode.WARMUP);
            return;
        }

        if (!prebidInitializer.enabled()) {
            // has been disabled
            customEventBannerListener.onBannerFailed(MoPubErrorCode.CANCELLED);
            return;
        }
    }

    @Override
    protected void onInvalidate() {
    }
}
