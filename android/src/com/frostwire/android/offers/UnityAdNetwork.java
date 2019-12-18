package com.frostwire.android.offers;

import android.app.Activity;

import com.frostwire.android.core.Constants;
import com.frostwire.util.Logger;
import com.unity3d.ads.UnityAds;

public class UnityAdNetwork extends AbstractAdNetwork {
    private static Logger LOG = Logger.getLogger(UnityAdNetwork.class);

    @Override
    public void initialize(Activity activity) {
        if (abortInitialize(activity)) {
            return;
        }
        final String GAME_ID = "3351589";
        UnityAds.initialize(activity, GAME_ID, isDebugOn());
    }

    @Override
    public void enable(boolean enabled) {
        Offers.AdNetworkHelper.enable(this, enabled);
    }

    @Override
    public boolean enabled() {
        return Offers.AdNetworkHelper.enabled(this);
    }

    @Override
    public boolean showInterstitial(Activity activity, String placement, boolean shutdownActivityAfterwards, boolean dismissActivityAfterward) {
        LOG.warn("UnityAdNetwork.showInterstitial(): this shouldn't be happening directly, this should happen via MoPub's mediation adapter");
        return false;
    }

    @Override
    public void loadNewInterstitial(Activity activity) {
        LOG.warn("UnityAdNetwork.loadNewInterstitial(): this shouldn't be happening directly, this should happen via MoPub's mediation adapter");
    }

    @Override
    public String getShortCode() {
        return Constants.AD_NETWORK_SHORTCODE_UNITY;
    }

    @Override
    public String getInUsePreferenceKey() {
        return Constants.PREF_KEY_GUI_USE_UNITY;
    }

    @Override
    public boolean isDebugOn() {
        return Offers.DEBUG_MODE;
    }
}
