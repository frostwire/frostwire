/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.offers;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;

import java.util.Random;


/**
 * Encapsulates the metadata required to render and act on a FrostWire support offer.
 */
public final class SupportOffer {

    private static final Random RANDOM = new Random();

    public enum Type {
        DONATION,
        VPN
    }

    public final Type type;
    @StringRes
    public final int titleRes;
    @StringRes
    public final int messageRes;
    @StringRes
    public final int actionTextRes;
    @StringRes
    public final int badgeTextRes;
    @DrawableRes
    public final int iconRes;
    public final String url;

    private SupportOffer(Type type,
                         @StringRes int titleRes,
                         @StringRes int messageRes,
                         @StringRes int actionTextRes,
                         @StringRes int badgeTextRes,
                         @DrawableRes int iconRes,
                         String url) {
        this.type = type;
        this.titleRes = titleRes;
        this.messageRes = messageRes;
        this.actionTextRes = actionTextRes;
        this.badgeTextRes = badgeTextRes;
        this.iconRes = iconRes;
        this.url = url;
    }

    public static SupportOffer random() {
        return forType(pickType());
    }

    public static SupportOffer forType(Type type) {
        if (type == Type.VPN) {
            return new SupportOffer(
                    Type.VPN,
                    R.string.support_offer_vpn_title,
                    R.string.support_offer_vpn_message,
                    R.string.support_offer_vpn_action,
                    R.string.support_offer_vpn_badge,
                    R.drawable.notification_vpn_on,
                    Constants.FROSTWIRE_VPN_URL
            );
        }
        return new SupportOffer(
                Type.DONATION,
                R.string.support_offer_donation_title,
                R.string.support_offer_donation_message,
                R.string.support_offer_donation_action,
                R.string.support_offer_donation_badge,
                R.drawable.contextmenu_icon_donation_fiat,
                Constants.FROSTWIRE_GIVE_URL + "android-support"
        );
    }

    private static Type pickType() {
        ConfigurationManager cm = ConfigurationManager.instance();
        int threshold = cm.getInt(Constants.PREF_KEY_GUI_SUPPORT_VPN_THRESHOLD, 50);
        if (threshold < 0) {
            threshold = 0;
        } else if (threshold > 100) {
            threshold = 100;
        }
        int roll = RANDOM.nextInt(100);
        return roll < threshold ? Type.VPN : Type.DONATION;
    }

    public void open(Context context) {
        UIUtils.openURL(context, url);
    }
}
