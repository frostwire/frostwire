/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Jose Molina (@votaguz), Marcelina Knitter (@marcelinkaaa)
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
import android.view.View;

import com.frostwire.android.R;
import com.frostwire.android.gui.activities.TransferDetailActivity;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * @author gubatron
 * @author aldenml
 * @author votaguz
 * @author marcelinkaaa
 */
public class TransferDetailsMenuAction extends MenuAction {

    private final String infohash;
    private WeakReference<View> clickedViewRef; // used for possible toast message

    public TransferDetailsMenuAction(Context context, int stringId, String infoHash) {
        super(context, R.drawable.contextmenu_icon_file, stringId, UIUtils.getAppIconPrimaryColor(context));
        this.infohash = infoHash;
    }

    @Override
    public void onClick(Context context) {
        if (infohash != null && !"".equals(infohash)) {
            Intent intent = new Intent(getContext(), TransferDetailActivity.class);
            intent.putExtra("infoHash", infohash);
            context.startActivity(intent);
        } else if (Ref.alive(clickedViewRef)) {
            UIUtils.showShortMessage(clickedViewRef.get(), R.string.could_not_open_transfer_detail_invalid_infohash);
        }
    }

    public TransferDetailsMenuAction setClickedView(View clickedView) {
        clickedViewRef = Ref.weak(clickedView);
        return this;
    }
}
