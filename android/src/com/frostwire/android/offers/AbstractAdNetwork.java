package com.frostwire.android.offers;

import android.app.Activity;
import android.content.Context;

import com.frostwire.util.Logger;

/**
 * Created by gubatron on 11/9/16.
 */
public abstract class AbstractAdNetwork implements AdNetwork {

    private static Logger LOG = Logger.getLogger(AbstractAdNetwork.class);


    public abstract void initialize(Activity activity);
    public abstract boolean showInterstitial(Activity activity, String placement, boolean shutdownActivityAfterwards, boolean dismissActivityAfterward);
    public abstract void loadNewInterstitial(Activity activity);
    public abstract String getShortCode();
    public abstract String getInUsePreferenceKey();
    public abstract boolean isDebugOn()

    @Override
    public void stop(Context context) {
        Offers.AdNetworkHelper.stop(this);
        LOG.info("stop() - " + getShortCode() + " stopped");
    }

    public void start() {
        Offers.AdNetworkHelper.start(this);
        LOG.info("start() - " + getShortCode() + " started");
    }

    @Override
    public void enable(boolean enabled) {
        Offers.AdNetworkHelper.enable(this, true);
    }

    @Override
    public boolean enabled() {
        return Offers.AdNetworkHelper.enabled(this);
    }

    @Override
    public boolean started() {
        return Offers.AdNetworkHelper.started(this);
    }

    @Override
    public int hashCode() {
        return getInUsePreferenceKey().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        try {
            AdNetwork otherNetwork = (AdNetwork) obj;
            return otherNetwork.getShortCode().equals(this.getShortCode());
        } catch (Throwable t) {
            return false;
        }
    }

}
