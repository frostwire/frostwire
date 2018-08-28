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
import android.support.annotation.NonNull;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.mopub.common.MoPub;
import com.mopub.common.SdkConfiguration;
import com.mopub.common.logging.MoPubLog;
import com.mopub.common.privacy.ConsentDialogListener;
import com.mopub.common.privacy.ConsentStatus;
import com.mopub.common.privacy.ConsentStatusChangeListener;
import com.mopub.common.privacy.PersonalInfoManager;
import com.mopub.common.util.Reflection;
import com.mopub.mobileads.AdMobBannerAdapter;
import com.mopub.mobileads.AdMobInterstitialAdapter;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubInterstitial;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
    private final Bundle npaBundle = new Bundle();
    private boolean starting = false;
    private Map<String,String> placements;
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
        SdkConfiguration sdkConfiguration = new SdkConfiguration.Builder(UNIT_ID_SEARCH_HEADER)
                .withMediationSettings(
                        new AdMobBannerAdapter.GooglePlayServicesMediationSettings(npaBundle),
                        new AdMobInterstitialAdapter.GooglePlayServicesMediationSettings(npaBundle))
                .build();
        fixExecutor(true);
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

    public void loadMoPubInterstitial(final Activity activity, final String placement) {
        if (activity == null) {
            LOG.info("Aborted loading interstitial ("+placement+"), no Activity");
            return;
        }
        if (!started() || !enabled()) {
            LOG.info("loadMoPubInterstitial(placement="+placement+") aborted. Network not started or not enabled");
            return;
        }
        LOG.info("Loading " + placement + " interstitial");
        try {
            final MoPubInterstitial moPubInterstitial = new MoPubInterstitial(activity, placements.get(placement));
            MoPubInterstitialListener moPubListener = new MoPubInterstitialListener(this, placement);
            moPubInterstitial.setInterstitialAdListener(moPubListener);
            interstitials.put(placement, moPubInterstitial);
            moPubInterstitial.load();
        } catch (Throwable e) {
            LOG.warn("Mopub Interstitial couldn't be loaded", e);
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
                interstitial.destroy();
            }
        }
    }

    @Override
    public void onConsentStateChange(@NonNull ConsentStatus oldConsentStatus,
                                     @NonNull ConsentStatus newConsentStatus,
                                     boolean canCollectPersonalInformation) {
        if (!canCollectPersonalInformation) {
            npaBundle.putString("npa","1");
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
