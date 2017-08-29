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
 * @author gubatron
 * @author aldenml
 */
public final class SendFiatTipAction extends MenuAction {

    private final String paypalUrl;

    public SendFiatTipAction(Context context, String paypalUrl) {
        super(context, R.drawable.contextmenu_icon_donation_fiat, R.string.send_tip_donation);
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
