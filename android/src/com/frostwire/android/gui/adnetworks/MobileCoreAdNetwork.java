/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(TM). All rights reserved.
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

package com.frostwire.android.gui.adnetworks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.logging.Logger;
import com.frostwire.util.Ref;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;
import com.ironsource.mobilcore.AdUnitEventListener;
import com.ironsource.mobilcore.CallbackResponse;
import com.ironsource.mobilcore.MobileCore;

import java.lang.ref.WeakReference;

public class MobileCoreAdNetwork implements AdNetwork {
    private boolean started;
    private WeakReference<Activity> activityRef;
    private static final Logger LOG = Logger.getLogger(MobileCoreAdNetwork.class);

    public void initialize(Activity activity) {
        activityRef = Ref.weak(activity);
        if (!started && enabled()) {
            try {
                MobileCore.init(activity, Constants.MOBILE_CORE_DEVHASH, MobileCore.LOG_TYPE.DEBUG, MobileCore.AD_UNITS.INTERSTITIAL, MobileCore.AD_UNITS.DIRECT_TO_MARKET);
                MobileCore.setNativeAdsBannerSupport(true);
                MobileCore.setAdUnitEventListener(new AdUnitEventListener() {
                    @Override
                    public void onAdUnitEvent(MobileCore.AD_UNITS ad_units, EVENT_TYPE event_type) {
                        if (event_type.equals(EVENT_TYPE.AD_UNIT_READY) && ad_units.equals(MobileCore.AD_UNITS.NATIVE_ADS)) {
                            Offers.MOBILE_CORE_NATIVE_ADS_READY = true;

                        }
                    }
                });
                started = true;
            } catch (Throwable e) {
                e.printStackTrace();
                started = false;
            }
        } else if (started && enabled()) {
            try {
                MobileCore.refreshOffers();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void stop(Context context) {
        try {
            context.stopService(new Intent(context.getApplicationContext(),
                    com.ironsource.mobilcore.MobileCoreReport.class));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public boolean enabled() {
        ConfigurationManager config;
        boolean isMobileCoreEnabled = false;
        try {
            config = ConfigurationManager.instance();
            isMobileCoreEnabled = (config.getBoolean(Constants.PREF_KEY_GUI_SUPPORT_FROSTWIRE) && config.getBoolean(Constants.PREF_KEY_GUI_USE_MOBILE_CORE));
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return isMobileCoreEnabled;
    }

    @Override
    public boolean started() {
        return started;
    }

    @Override
    public boolean showInterstitial(final WeakReference<Activity> activityWeakReference,
                                    final boolean shutdownActivityAfterwards,
                                    final boolean dismissActivityAfterward) {
        if (Ref.alive(activityWeakReference)) {
            activityRef = activityWeakReference;
        }

        if (enabled() && started && Ref.alive(activityRef) && MobileCore.isInterstitialReady()) {
            try {
                MobileCore.showInterstitial(activityRef.get(), new CallbackResponse() {
                    @Override
                    public void onConfirmation(TYPE type) {
                        if (Ref.alive(activityRef)) {
                            final Activity activity = activityRef.get();
                            if (dismissActivityAfterward) {
                                activity.finish();
                            }
                            if (shutdownActivityAfterwards && activity instanceof MainActivity) {
                                ((MainActivity) activity).shutdown();
                            } else if (shutdownActivityAfterwards) {
                                LOG.warn("Could not shutdown");
                            }
                        }
                    }
                });
                UXStats.instance().log(UXAction.MISC_INTERSTITIAL_SHOW);
                return true;
            } catch (Throwable e) {
                e.printStackTrace();
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public void loadNewInterstitial(Activity activity) {
    }

    public boolean isDirectToMarketReady() {
        return MobileCore.isDirectToMarketReady();
    }

    public void directToMarket(Activity activity) {
        MobileCore.directToMarket(activity);
    }
}