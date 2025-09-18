/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.android.gui.adapters.menu;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;

/**
 * Created on 3/21/15 (on a plane from Ft. Lauderdale to San Francisco)
 *
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public final class SendBitcoinTipAction extends MenuAction {

    private final String bitcoinUrl;

    public SendBitcoinTipAction(Context context, String bitcoinUrl) {
        super(context,
                R.drawable.contextmenu_icon_donation_bitcoin,
                R.string.send_bitcoin_tip,
                UIUtils.getAppIconPrimaryColor(context));
        this.bitcoinUrl = bitcoinUrl;
    }

    @Override
    public void onClick(Context context) {
        try {
            String bitcoinUriPrefix = "bitcoin:";
            Intent intent = new Intent(Intent.ACTION_VIEW);
            String uri = (!bitcoinUrl.startsWith(bitcoinUriPrefix) ? bitcoinUriPrefix : "") + bitcoinUrl;
            intent.setData(Uri.parse(uri));
            context.startActivity(intent);
        } catch (Throwable e) {
            UIUtils.showLongMessage(getContext(), R.string.you_need_a_bitcoin_wallet_app);
        }
    }
}
