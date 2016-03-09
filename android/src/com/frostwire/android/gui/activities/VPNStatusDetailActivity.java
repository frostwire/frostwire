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
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spanned;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.text.Html;
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

    public VPNStatusDetailActivity() {
        super(R.layout.view_vpn_status_detail);
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
        final TextView headerStatus = findView(R.id.view_vpn_status_header);
        final TextView VPNText = findView(R.id.view_vpn_status_vpn_text);
        final TextView VPNMoneyBack = findView(R.id.view_vpn_status_money_back);
        final TextView VPNPrice = findView(R.id.view_vpn_status_vpn_price);
        final TextView VPNFeatureList = findView(R.id.view_vpn_status_client_features);
        final TextView VPNBullet = findView(R.id.view_vpn_status_bullet_textview);

        final Button getVPNButtonTop = findView(R.id.view_vpn_status_get_vpn_button_top);
        final Button learnVPNButton = findView(R.id.view_vpn_status_learn_more_button);
        final Button getVPNButtonBottom = findView(R.id.view_vpn_status_get_vpn_button_bottom);


        String VPNHtmlFeatures = getString(R.string.VPN_feature_list_html);
        Spanned VPNFeaturesAsSpanned = Html.fromHtml(VPNHtmlFeatures);
        VPNFeatureList.setText(VPNFeaturesAsSpanned);

        String VPNHtmlBullet = getString(R.string.you_dont_need_a_vpn_to_use_frostwire_bullet_html);
        Spanned VPNBulletAsSpanned = Html.fromHtml(VPNHtmlBullet);
        VPNBullet.setText(VPNBulletAsSpanned);

        // By default the layout has icon and title set to unprotected.
        if (isProtectedConnection) {
            // Current Status Icon
            headerIcon.setImageResource(R.drawable.vpn_icon_on_info);
            // Current Status Title
            headerStatus.setText(R.string.protected_connection);
            headerStatus.setTextColor(getResources().getColor(R.color.approval_green));
            VPNMoneyBack.setVisibility(View.GONE);
            VPNPrice.setVisibility(View.GONE);
            // Current Status Text
            String VPNHtmlText = getString(R.string.protected_connections_visibility_bullet_html);
            Spanned VPNTextAsSpanned = Html.fromHtml(VPNHtmlText);
            VPNText.setText(VPNTextAsSpanned);
            // getVPNButtonTop/learnVPNButton
            getVPNButtonTop.setVisibility(View.GONE);
            learnVPNButton.setText(R.string.learn_more);
            // getVPNButtonBottom
            getVPNButtonBottom.setText(R.string.visit_VPN_client);
        }
        else {
            // Current Status Icon
            headerIcon.setImageResource(R.drawable.vpn_icon_off_info);
            // Current Status Title
            headerStatus.setText(R.string.unprotected_connection);
            headerStatus.setTextColor(Color.RED);
            // Current Status VPN client price
            VPNMoneyBack.setText(R.string.VPN_money_back);
            VPNPrice.setText(R.string.VPN_price);
            // Current Status Text
            String VPNHtmlText = getString(R.string.unprotected_connections_visibility_bullet_html);
            Spanned VPNTextAsSpanned = Html.fromHtml(VPNHtmlText);
            VPNText.setText(VPNTextAsSpanned);
            // getVPNButtonTop/learnVPNButton
            learnVPNButton.setVisibility(View.GONE);
            getVPNButtonTop.setText(R.string.get_express_VPN);
            // getVPNButtonBottom
            getVPNButtonBottom.setText(R.string.get_express_VPN);
        }

        final OnGetVPNClickListener onGetVPNClickListener = new OnGetVPNClickListener(this, isProtectedConnection);
        headerIcon.setOnClickListener(onGetVPNClickListener);
        headerStatus.setOnClickListener(onGetVPNClickListener);
        getVPNButtonTop.setOnClickListener(onGetVPNClickListener);
        getVPNButtonBottom.setOnClickListener(onGetVPNClickListener);
        learnVPNButton.setOnClickListener(onGetVPNClickListener);

        headerIcon.setOnLongClickListener(onGetVPNClickListener);
        headerStatus.setOnLongClickListener(onGetVPNClickListener);
        getVPNButtonTop.setOnLongClickListener(onGetVPNClickListener);
        getVPNButtonBottom.setOnLongClickListener(onGetVPNClickListener);
        learnVPNButton.setOnLongClickListener(onGetVPNClickListener);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        Intent newIntent = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // if we came from Transfers, make sure to go have the transfer fragment shown by MainActivity.
        Intent i = getIntent();
        if (i != null && i.getExtras() != null &&
                i.getExtras().containsKey("from") &&
                i.getExtras().get("from").equals("transfers")) {
            newIntent.setAction(Constants.ACTION_SHOW_TRANSFERS);
        }

        startActivity(newIntent);
        super.onBackPressed();
    }

    // Let's minimize the use of anonymous classes $1, $2 for every listener out there. DRY principle is the prime coding directive.
    private static class OnGetVPNClickListener extends ClickAdapter<VPNStatusDetailActivity> {

        private final boolean isProtectedConnection;

        public OnGetVPNClickListener(VPNStatusDetailActivity owner, boolean isProtectedConnection) {
            super(owner);
            this.isProtectedConnection = isProtectedConnection;
        }

        @Override
        public void onClick(VPNStatusDetailActivity owner, View v) {
            if (isProtectedConnection) {
                UIUtils.openURL(owner, Constants.VPN_LEARN_MORE_URL);
            }
            else {
                String vpnUrl = Constants.IS_GOOGLE_PLAY_DISTRIBUTION ?
                        Constants.EXPRESSVPN_URL_BASIC :
                        Constants.EXPRESSVPN_URL_PLUS;

                UIUtils.openURL(owner, vpnUrl);
            }

        }
    }
}