/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public final class SendFiatTipAction extends MenuAction {

    private final String paypalUrl;

    public SendFiatTipAction(Context context, String paypalUrl) {
        super(context, R.drawable.contextmenu_icon_donation_fiat, R.string.send_tip_donation, UIUtils.getAppIconPrimaryColor(context));
        this.paypalUrl = paypalUrl;
    }

    @Override
    public void onClick(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(paypalUrl));
            context.startActivity(intent);
        } catch (Throwable e) {
            UIUtils.showLongMessage(getContext(), R.string.issue_with_tip_donation_uri);
        }
    }
}
