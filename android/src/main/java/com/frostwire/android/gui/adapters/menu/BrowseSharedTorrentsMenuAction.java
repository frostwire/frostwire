/*
 *     Created by Angel Leon (@gubatron)
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

import com.frostwire.android.R;
import com.frostwire.android.gui.activities.PeerCatalogActivity;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;

import java.util.Base64;

/**
 * Offered on Distributed search results whose holder advertises a public
 * catalog (magnet flag {@code x.hc=1}): opens that holder's shared-torrent
 * catalog over the IceBridge mesh.
 *
 * @author gubatron
 */
public class BrowseSharedTorrentsMenuAction extends MenuAction {

    private final String peerPubBase64Url;

    public BrowseSharedTorrentsMenuAction(Context context, byte[] peerPub) {
        super(context,
                R.drawable.contextmenu_icon_magnet,
                R.string.peer_catalog_browse_action,
                UIUtils.getAppIconPrimaryColor(context));
        this.peerPubBase64Url = (peerPub == null || peerPub.length != 32)
                ? null
                : Base64.getUrlEncoder().withoutPadding().encodeToString(peerPub);
    }

    @Override
    public void onClick(Context context) {
        if (peerPubBase64Url == null || context == null) {
            return;
        }
        Intent intent = new Intent(context, PeerCatalogActivity.class);
        intent.putExtra(PeerCatalogActivity.EXTRA_PEER_PUB, peerPubBase64Url);
        context.startActivity(intent);
    }
}
