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
import com.frostwire.android.util.Asyncs;
import com.frostwire.util.Logger;
import com.mopub.common.MoPubReward;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubRewardedVideos;

import java.util.Set;

import static com.frostwire.android.util.Asyncs.async;

public final class MoPubRewardedVideoListener implements com.mopub.mobileads.MoPubRewardedVideoListener  {
    private static Logger LOG = Logger.getLogger(MoPubRewardedVideoListener.class);
    private boolean wasPlayingMusic;
    private static MoPubRewardedVideoListener INSTANCE = new MoPubRewardedVideoListener();

    private MoPubRewardedVideoListener() {
    }

    public static MoPubRewardedVideoListener instance() {
        return INSTANCE;
    }

    @Override
    public void onRewardedVideoLoadSuccess(@NonNull String adUnitId) {
        LOG.info("onRewardedVideoLoadSuccess() !!!!");
    }

    @Override
    public void onRewardedVideoLoadFailure(@NonNull String adUnitId, @NonNull MoPubErrorCode errorCode) {
        LOG.info("onRewardedVideoLoadFailure: " + errorCode.toString());
        MoPubRewardedVideos.loadRewardedVideo(MoPubAdNetwork.UNIT_ID_REWARDED_VIDEO);
    }

    @Override
    public void onRewardedVideoStarted(@NonNull String adUnitId) {
        LOG.info("onRewardedVideoStarted() started reward video playback");
        wasPlayingMusic = MusicUtils.isPlaying();

        if (wasPlayingMusic) {
            MusicUtils.pause();
        }
    }

    @Override
    public void onRewardedVideoPlaybackError(@NonNull String adUnitId, @NonNull MoPubErrorCode errorCode) {
        LOG.info("onRewardedVideoPlaybackError: " + errorCode.toString());
        if (wasPlayingMusic) {
            MusicUtils.play();
        }

    }

    @Override
    public void onRewardedVideoClicked(@NonNull String adUnitId) {

    }

    @Override
    public void onRewardedVideoClosed(@NonNull String adUnitId) {
        if (wasPlayingMusic) {
            MusicUtils.play();
        }
    }

    @Override
    public void onRewardedVideoCompleted(@NonNull Set<String> adUnitIds, @NonNull MoPubReward reward) {
        if (wasPlayingMusic) {
            MusicUtils.play();
        }
        async(Offers::pauseAdsAsync, reward.getAmount());
    }
}
