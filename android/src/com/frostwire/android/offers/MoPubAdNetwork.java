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

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.mopub.common.MoPub;
import com.mopub.common.MoPubReward;
import com.mopub.common.SdkConfiguration;
import com.mopub.common.logging.MoPubLog;
import com.mopub.common.privacy.ConsentDialogListener;
import com.mopub.common.privacy.ConsentStatus;
import com.mopub.common.privacy.ConsentStatusChangeListener;
import com.mopub.common.privacy.PersonalInfoManager;
import com.mopub.common.util.Reflection;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubInterstitial;
import com.mopub.mobileads.MoPubRewardedVideo;
import com.mopub.mobileads.MoPubRewardedVideos;
import com.mopub.network.Networking;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;

import static com.frostwire.android.util.Asyncs.async;

/**
 * Created on Nov/8/16 (2016 US election day)
 *
 * @author aldenml
 * @author gubatron
 */

public class MoPubAdNetwork extends AbstractAdNetwork implements ConsentStatusChangeListener {
    private static final Logger LOG = Logger.getLogger(MoPubAdNetwork.class);
    private static final boolean DEBUG_MODE = Offers.DEBUG_MODE;
    public static final String UNIT_ID_AUDIO_PLAYER = "c737d8a55b2e41189aa1532ae0520ad1";
    public static final String UNIT_ID_HOME = "8174d0bcc3684259b3fdbc8e1310682e"; // aka 300×250 Search Screen
    public static final String UNIT_ID_PREVIEW_PLAYER_VERTICAL = "a8be0cad4ad0419dbb19601aef3a18d2";
    public static final String UNIT_ID_PREVIEW_PLAYER_HORIZONTAL = "2fd0fafe3d3c4d668385a620caaa694e";
    public static final String UNIT_ID_SEARCH_HEADER = "be0b959f15994fd5b56c997f63530bd0";
    //private static final String TEST_UNIT_REWARDED_VIDEO_GAME_PLAYABLE = "15173ac6d3e54c9389b9a5ddca69b34b";
    //private static final String TEST_UNIT_REWARDED_VIDEO = "920b6145fb1546cf8b5cf2ac34638bb7";
    public static final String UNIT_ID_REWARDED_VIDEO = "4e4f31e5067049998664b5ec7b9451e1";

    private final Bundle npaBundle = new Bundle();
    private boolean starting = false;
    private Map<String, String> placements;
    private Map<String, MoPubInterstitial> interstitials;

    @Override
    public void initialize(Activity activity) {
        if (abortInitialize(activity)) {
            LOG.info("initialize() aborted");
            return;
        }
        if (activity == null) {
            LOG.info("initialize() activity is null, aborted");
            return;
        }
        if (starting) {
            LOG.info("initialize() aborted, starting.");
            return;
        }
        starting = true;
        initPlacementMappings(UIUtils.isTablet(activity.getResources()));
        SdkConfiguration.Builder builder = new SdkConfiguration.Builder(UNIT_ID_SEARCH_HEADER);
        if (Offers.DEBUG_MODE) {
            builder.withLogLevel(MoPubLog.LogLevel.DEBUG);
        }
        SdkConfiguration sdkConfiguration = builder.build();
        fixExecutor(true);

        // TEMP HACK: quick sleep to avoid ANR from MoPub
        // https://twittercommunity.com/t/android-mopub-5-4-0-anr/115804/17
        /*
         * "main" prio=5 tid=1 Blocked
         *   | group="main" sCount=1 dsCount=0 obj=0x7664e4b8 self=0xb727b0b8
         *   | sysTid=12543 nice=0 cgrp=default sched=0/0 handle=0xb6f4cb34
         *   | state=S schedstat=( 0 0 0 ) utm=117 stm=24 core=2 HZ=100
         *   | stack=0xbe41f000-0xbe421000 stackSize=8MB
         *   | held mutexes=
         *
         *   at com.mopub.network.Networking.getRequestQueue (Networking.java:69)
         * - waiting to lock <0x02e88a82> (a java.lang.Class<com.mopub.network.Networking>) held by thread 25 (tid=25)
         *
         *   at com.mopub.network.AdLoader.fetchAd (AdLoader.java:255)
         *
         *   at com.mopub.network.AdLoader.loadNextAd (AdLoader.java:154)
         * - locked <0x09c2c593> (a java.lang.Object)
         *
         *   at com.mopub.mobileads.AdViewController.fetchAd (AdViewController.java:519)
         *
         *   at com.mopub.mobileads.AdViewController.loadNonJavascript (AdViewController.java:270)
         *
         *   at com.mopub.mobileads.AdViewController.internalLoadAd (AdViewController.java:250)
         *
         *   at com.mopub.mobileads.AdViewController.loadAd (AdViewController.java:232)
         *
         *   at com.mopub.mobileads.MoPubView.loadAd (MoPubView.java:108)
         *
         *   at com.frostwire.android.offers.MopubBannerView.loadMoPubBanner (MopubBannerView.java:181)
         *
         *   at com.frostwire.android.gui.adapters.PromotionsAdapter.getMopubBannerView (PromotionsAdapter.java:231)
         */
        Networking.getRequestQueue(activity);
        // END OF TEMP HACK BEFORE MoPub 5.4.1 is released

        MoPub.initializeSdk(activity, sdkConfiguration, () -> {
            fixExecutor(false);
            LOG.info("MoPub initialization finished");
            starting = false;
            start();
            async(MoPubAdNetwork::loadConsentDialogAsync, this);
            loadNewInterstitial(activity);
        });
        LOG.info("initialize() MoPub.initializeSdk invoked, starting=" + starting + ", started=" + started());
    }

    private void fixExecutor(boolean change) {
        try {
            LOG.info("MoPub -> fixExecutor with change=" + change);
            Field f = Reflection.getPrivateField(AsyncTask.class, "sDefaultExecutor");

            Field modifiersField = Field.class.getDeclaredField("accessFlags");
            modifiersField.setAccessible(true);
            modifiersField.setInt(f, f.getModifiers() & ~Modifier.FINAL);

            if (change) {
                f.set(null, AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                f.set(null, AsyncTask.SERIAL_EXECUTOR);
            }
        } catch (Exception e) {
            LOG.info("MoPub -> fixExecutor error change=" + change + " msg=" + e.getMessage());
        }
    }

    private static void loadConsentDialogAsync(MoPubAdNetwork mopubAdNetwork) {
        PersonalInfoManager personalInfoManager = MoPub.getPersonalInformationManager();
        //personalInfoManager.forceGdprApplies(); //uncomment to test in the US

        if (personalInfoManager != null && personalInfoManager.shouldShowConsentDialog()) {
            personalInfoManager.subscribeConsentStatusChangeListener(mopubAdNetwork);
            personalInfoManager.loadConsentDialog(new MoPubAdNetwork.MoPubConsentDialogListener(personalInfoManager));
        }
    }

    private void initPlacementMappings(boolean isTablet) {
        placements = new HashMap<>();
        if (!isTablet) {
            placements.put(Offers.PLACEMENT_INTERSTITIAL_MAIN, "399a20d69bdc449a8e0ca171f82179c8");
        } else {
            placements.put(Offers.PLACEMENT_INTERSTITIAL_MAIN, "cebdbc56b37c4d31ba79e861d1cb0de4");
        }
    }

    @Override
    public boolean showInterstitial(Activity activity, String placement, boolean shutdownActivityAfterwards, boolean dismissActivityAfterward) {
        if (interstitials == null || interstitials.isEmpty()) {
            return false;
        }
        MoPubInterstitial interstitial = interstitials.get(placement);
        MoPubInterstitialListener listener = (MoPubInterstitialListener) interstitial.getInterstitialAdListener();
        if (listener != null) {
            listener.shutdownAppAfter(shutdownActivityAfterwards);
            listener.dismissActivityAfterwards(dismissActivityAfterward);
            listener.wasPlayingMusic(MusicUtils.isPlaying());
        }
        if (listener == null) {
            LOG.warn("showInterstitial() failed, no listener set, check your logic");
            return false;
        }

        // reflection hack to check if MoPub interstitial is destroyed (.isDestroyed is not public)
        if (!interstitial.isReady()) {
            try {
                Method isDestroyedMethod = interstitial.getClass().getDeclaredMethod("isDestroyed");
                isDestroyedMethod.setAccessible(true);
                boolean isDestroyed = (boolean) isDestroyedMethod.invoke(interstitial);
                isDestroyedMethod.setAccessible(false);

                if (isDestroyed) {
                    loadNewInterstitial(activity);
                }
            } catch (Throwable t) {
                LOG.error(t.getMessage(), t);
            }
            return false;
        }
        return interstitial.show();
    }

    @Override
    public void loadNewInterstitial(final Activity activity) {
        if (!started() || !enabled()) {
            LOG.info("loadNewInterstitial() aborted. Network not started or not enabled");
            return; //not ready
        }
        if (placements.isEmpty()) {
            LOG.warn("check your logic, can't call loadNewInterstitial() before initialize()");
            return;
        }
        interstitials = new HashMap<>();
        Set<String> placementKeys = placements.keySet();
        for (String placement : placementKeys) {
            loadMoPubInterstitial(activity, placement);
        }
    }

    public void loadRewardedVideo() {
        if (!started() || !enabled()) {
            LOG.info("loadRewardedVideo() aborted. Network not started or not enabled");
            return; //not ready
        }
        MoPubRewardedVideos.setRewardedVideoListener(MoPubRewardedVideoListener.instance());
        MoPubRewardedVideos.loadRewardedVideo(UNIT_ID_REWARDED_VIDEO);
        LOG.info("loadRewardedVideo() called");
    }

    public void loadMoPubInterstitial(final Activity activity, final String placement) {
        if (activity == null) {
            LOG.info("Aborted loading interstitial (" + placement + "), no Activity");
            return;
        }
        if (!started() || !enabled()) {
            LOG.info("loadMoPubInterstitial(placement=" + placement + ") aborted. Network not started or not enabled");
            return;
        }
        LOG.info("Loading " + placement + " interstitial");
        try {
            final MoPubInterstitial moPubInterstitial = new MoPubInterstitial(activity, placements.get(placement));
            MoPubInterstitialListener moPubListener = new MoPubInterstitialListener(this, placement);
            moPubInterstitial.setInterstitialAdListener(moPubListener);
            interstitials.put(placement, moPubInterstitial);
            async(moPubInterstitial, MoPubAdNetwork::loadMoPubInterstitialAsync);
        } catch (Throwable e) {
            LOG.warn("loadMoPubInterstitial(activity, placement): Mopub Interstitial couldn't be loaded", e);
        }
    }

    private static void loadMoPubInterstitialAsync(final MoPubInterstitial moPubInterstitial) {
        try {
            Looper.prepare();
            moPubInterstitial.load();
        } catch (Throwable e) {
            LOG.warn("loadMoPubInterstitialAsync(moPubInterstitial): Mopub Interstitial couldn't be loaded", e);
        }
    }

    @Override
    public String getShortCode() {
        return Constants.AD_NETWORK_SHORTCODE_MOPUB;
    }

    @Override
    public String getInUsePreferenceKey() {
        return Constants.PREF_KEY_GUI_USE_MOPUB;
    }

    @Override
    public boolean isDebugOn() {
        return DEBUG_MODE;
    }

    @Override
    public void stop(Context context) {
        super.stop(context);
        starting = false;
        destroyInterstitials();
    }

    public void destroyInterstitials() {
        if (placements == null || interstitials == null || placements.isEmpty() || interstitials.isEmpty()) {
            return;
        }
        Set<String> placementKeys = placements.keySet();
        for (String key : placementKeys) {
            MoPubInterstitial interstitial = interstitials.get(key);
            if (interstitial != null) {
                try {
                    interstitial.destroy();
                } catch (Throwable t) {
                    LOG.warn(t.getMessage(), t);
                }
            }
        }
    }

    @Override
    public void onConsentStateChange(@NonNull ConsentStatus oldConsentStatus,
                                     @NonNull ConsentStatus newConsentStatus,
                                     boolean canCollectPersonalInformation) {
        if (!canCollectPersonalInformation) {
            npaBundle.putString("npa", "1");
        } else {
            npaBundle.remove("npa");
        }
    }

    public static final class MoPubConsentDialogListener implements ConsentDialogListener {
        private final WeakReference<PersonalInfoManager> pmRef;

        public MoPubConsentDialogListener(PersonalInfoManager pm) {
            pmRef = Ref.weak(pm);
        }

        @Override
        public void onConsentDialogLoaded() {
            if (!Ref.alive(pmRef)) {
                return;
            }
            PersonalInfoManager personalInfoManager = pmRef.get();
            personalInfoManager.showConsentDialog();
        }

        @Override
        public void onConsentDialogLoadFailed(@NonNull MoPubErrorCode moPubErrorCode) {
            MoPubLog.i("Consent dialog failed to load.");
        }
    }
}
