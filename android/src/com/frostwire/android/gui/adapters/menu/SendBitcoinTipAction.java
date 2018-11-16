/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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
 */
public final class SendBitcoinTipAction extends MenuAction {

    private final String bitcoinUrl;

    public SendBitcoinTipAction(Context context, String bitcoinUrl) {
        super(context, R.drawable.contextmenu_icon_donation_bitcoin, R.string.send_bitcoin_tip);
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
