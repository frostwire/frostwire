/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.activities;

import android.app.ActionBar;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.ClickAdapter;


/**
 *
 * @author gubatron
 * @author aldenml
 *
 */
public class VPNStatusDetailActivity extends AbstractActivity {

    private final OnGetVPNClickListener onGetVPNClickListener;

    public VPNStatusDetailActivity() {
        super(R.layout.view_vpn_status_detail);
        onGetVPNClickListener = new OnGetVPNClickListener(this);
    }

    @Override
    protected void initComponents(Bundle savedInstanceState) {
        final ActionBar bar = getActionBar();
        if (bar != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setIcon(android.R.color.transparent);
        }

        final Intent intent = getIntent();
        final boolean isProtectedConnection = intent.getAction() != null &&
                intent.getAction().equals(Constants.ACTION_SHOW_VPN_STATUS_PROTECTED);
        final ImageView headerIcon = findView(R.id.view_vpn_status_header_icon);
        final TextView headerTitle = findView(R.id.view_vpn_status_header_title);
        final Button button = findView(R.id.view_vpn_status_get_vpn_button);

        // By default the layout has icon and title set to unprotected.
        if (isProtectedConnection) {
            // Current Status Icon
            headerIcon.setImageResource(R.drawable.notification_vpn_on);
            // Current Status Title
            headerTitle.setText(R.string.protected_connection);
        }

        headerIcon.setOnClickListener(onGetVPNClickListener);
        headerTitle.setOnClickListener(onGetVPNClickListener);
        button.setOnClickListener(onGetVPNClickListener);

        headerIcon.setOnLongClickListener(onGetVPNClickListener);
        headerTitle.setOnLongClickListener(onGetVPNClickListener);
        button.setOnLongClickListener(onGetVPNClickListener);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Let's minimize the use of anonymous classes $1, $2 for every listener out there. DRY principle is the prime coding directive.
    private static class OnGetVPNClickListener extends ClickAdapter<VPNStatusDetailActivity> {

        public OnGetVPNClickListener(VPNStatusDetailActivity owner) {
            super(owner);
        }

        @Override
        public void onClick(VPNStatusDetailActivity owner, View v) {
            UIUtils.openURL(owner, Constants.PIA_URL);
        }
    }
}