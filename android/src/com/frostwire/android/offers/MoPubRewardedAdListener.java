/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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

import androidx.annotation.NonNull;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.core.Constants;
import com.frostwire.util.Logger;
import com.mopub.common.MoPubReward;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubRewardedAds;

import java.util.Set;

import static com.frostwire.android.util.Asyncs.async;

// NOTE:
// Unity's RewardedAdListener is implemented in UnityAdNetwork.java
public final class MoPubRewardedAdListener implements com.mopub.mobileads.MoPubRewardedAdListener {
    private static final Logger LOG = Logger.getLogger(MoPubRewardedAdListener.class);
    private boolean wasPlayingMusic;
    private static final MoPubRewardedAdListener INSTANCE = new MoPubRewardedAdListener();

    private MoPubRewardedAdListener() {
    }

    public static MoPubRewardedAdListener instance() {
        return INSTANCE;
    }

    @Override
    public void onRewardedAdLoadSuccess(@NonNull String adUnitId) {
        LOG.info("onRewardedAdLoadSuccess() !!!!");
    }

    @Override
    public void onRewardedAdLoadFailure(@NonNull String adUnitId, @NonNull MoPubErrorCode errorCode) {
        LOG.info("onRewardedAdLoadFailure: " + errorCode.toString());
        MoPubRewardedAds.loadRewardedAd(MoPubAdNetwork.UNIT_ID_REWARDED_AD);
    }

    @Override
    public void onRewardedAdStarted(@NonNull String adUnitId) {
        LOG.info("onRewardedAdStarted() started reward Ad playback");
        wasPlayingMusic = MusicUtils.isPlaying();

        if (wasPlayingMusic) {
            MusicUtils.pause();
        }
    }

    @Override
    public void onRewardedAdShowError(@NonNull String adUnitId, @NonNull MoPubErrorCode errorCode) {
        LOG.info("onRewardedAdShowError: " + errorCode.toString());
        if (wasPlayingMusic) {
            MusicUtils.play();
        }
    }

    @Override
    public void onRewardedAdClicked(@NonNull String adUnitId) {
        LOG.info("onRewardedAdClicked: adUnitId=" + adUnitId);
    }

    @Override
    public void onRewardedAdClosed(@NonNull String adUnitId) {
        LOG.info("onRewardedAdClosed: adUnitId=" + adUnitId);
        if (wasPlayingMusic) {
            MusicUtils.play();
        }
    }

    @Override
    public void onRewardedAdCompleted(@NonNull Set<String> adUnitIds, @NonNull MoPubReward reward) {
        LOG.info("onRewardedAdCompleted: adUnitId=" + adUnitIds);
        async(Offers::pauseAdsAsync, Constants.MIN_REWARD_AD_FREE_MINUTES);
        if (wasPlayingMusic) {
            MusicUtils.play();
        }
    }
}
