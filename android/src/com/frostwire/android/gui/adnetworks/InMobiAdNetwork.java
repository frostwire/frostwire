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
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.logging.Logger;
import com.frostwire.util.Ref;
import com.inmobi.monetization.IMInterstitial;

import java.lang.ref.WeakReference;

public class InMobiAdNetwork implements AdNetwork {
    private static final Logger LOG = Logger.getLogger(InMobiAdNetwork.class);
    private InMobiListener inmobiListener;
    private IMInterstitial inmobiInterstitial;
    private boolean started = false;

    public InMobiAdNetwork() {}

    public void initialize(final Activity activity) {
        if (!enabled()) {
            return;
        }

        if (!started) {
            Offers.THREAD_POOL.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        // this initialize call is very expensive, this is why we should be invoked in a thread.
                        //LOG.info("InMobi.initialize()...");
                        com.inmobi.commons.InMobi.initialize(activity, Constants.INMOBI_INTERSTITIAL_PROPERTY_ID);
                        //InMobi.setLogLevel(InMobi.LOG_LEVEL.DEBUG);
                        //LOG.info("InMobi.initialized.");
                        started = true;
                        //LOG.info("Load InmobiInterstitial.");
                        loadNewInterstitial(activity);
                    } catch (Throwable t) {
                        t.printStackTrace();
                        started = false;
                    }
                }
            });
        }
    }

    @Override
    public void stop(Context context) {
    }

    public boolean enabled() {
        ConfigurationManager config;
        boolean isInMobiEnabled = false;
        try {
            config = ConfigurationManager.instance();
            isInMobiEnabled = (config.getBoolean(Constants.PREF_KEY_GUI_SUPPORT_FROSTWIRE) && config.getBoolean(Constants.PREF_KEY_GUI_USE_INMOBI));
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return isInMobiEnabled;
    }

    public boolean showInterstitial(final WeakReference<Activity> activityWeakReference,
                                    boolean shutdownActivityAfterwards,
                                    boolean dismissActivityAfterward) {
        if (!started || !enabled() || inmobiInterstitial == null || inmobiListener == null) {
            return false;
        }

        inmobiListener.shutdownAppAfter(shutdownActivityAfterwards);
        inmobiListener.dismissActivityAfterwards(dismissActivityAfterward);

        if (inmobiInterstitial.getState().equals(IMInterstitial.State.READY)) {
            try {
                inmobiInterstitial.show();

                if (Ref.alive(activityWeakReference)) {
                    loadNewInterstitial(activityWeakReference.get());
                }

                LOG.info("InMobi Interstitial shown.");
                return true;
            } catch (Throwable e) {
                LOG.error("InMobi Interstitial failed on .show()!", e);
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean started() {
        return started;
    }

    @Override
    public void loadNewInterstitial(final Activity activity) {
        if (!started) {
            return; //not ready
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    inmobiInterstitial = new IMInterstitial(activity, Constants.INMOBI_INTERSTITIAL_PROPERTY_ID);
                    inmobiListener = new InMobiListener(activity);
                    inmobiInterstitial.setIMInterstitialListener(inmobiListener);
                    inmobiInterstitial.loadInterstitial();
                } catch (Throwable t) {
                    // don't crash, keep going.
                    // possible android.util.AndroidRuntimeException: android.content.pm.PackageManager$NameNotFoundException: com.google.android.webview
                }
            }
        });
    }
}
