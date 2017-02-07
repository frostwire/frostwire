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

package com.frostwire.android.gui.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractActivity;

/**
 * @author gubatron
 * @author aldenml
 */
public final class VPNStatusDetailActivity extends AbstractActivity {

    private static final String UNICODE_BULLET = "&#8226; ";

    public VPNStatusDetailActivity() {
        super(R.layout.view_vpn_status_detail);
    }

    @Override
    protected void initComponents(Bundle savedInstanceState) {
        final ImageView headerIcon = findView(R.id.view_vpn_status_header_icon);
        final TextView headerStatus = findView(R.id.view_vpn_status_header);
        final TextView vpnText = findView(R.id.view_vpn_status_vpn_text);
        final TextView vpnMoneyBack = findView(R.id.view_vpn_status_money_back);
        final TextView vpnPrice = findView(R.id.view_vpn_status_vpn_price);

        final TextView vpnBullet = findView(R.id.view_vpn_status_bullet_textview);
        vpnBullet.setText(fromHtml(R.string.you_dont_need_a_vpn_to_use_frostwire_bullet_html));

        appendBullet(R.id.view_vpn_status_vpn_client_feature_1);
        appendBullet(R.id.view_vpn_status_vpn_client_feature_2);
        appendBullet(R.id.view_vpn_status_vpn_client_feature_3);
        appendBullet(R.id.view_vpn_status_vpn_client_feature_4);

        final Button getVPNButtonTop = findView(R.id.view_vpn_status_get_vpn_button_top);
        final Button learnVPNButton = findView(R.id.view_vpn_status_learn_more_button);
        final Button getVPNButtonBottom = findView(R.id.view_vpn_status_get_vpn_button_bottom);

        boolean isProtectedConnection = isProtectedConnection();
        // By default the layout has icon and title set to unprotected.
        if (isProtectedConnection) {
            // Current Status Icon
            headerIcon.setImageResource(R.drawable.vpn_icon_on_info);
            // Current Status Title, color is set in layout
            headerStatus.setText(R.string.protected_connection);
            vpnMoneyBack.setVisibility(View.GONE);
            vpnPrice.setVisibility(View.GONE);
            // Current Status Text
            vpnText.setText(fromHtml(R.string.protected_connections_visibility_bullet_html));
            // getVPNButtonTop/learnVPNButton
            getVPNButtonTop.setVisibility(View.GONE);
            learnVPNButton.setText(R.string.learn_more);
            // getVPNButtonBottom
            getVPNButtonBottom.setText(R.string.visit_vpn_client);
        } else {
            // Current Status Icon
            headerIcon.setImageResource(R.drawable.vpn_icon_off_info);
            // Current Status Title
            headerStatus.setText(R.string.unprotected_connection);
            headerStatus.setTextColor(Color.RED);
            // Current Status VPN client price
            vpnMoneyBack.setText(R.string.vpn_money_back);
            vpnPrice.setText(getString(R.string.vpn_price, Constants.EXPRESSVPN_STARTING_USD_PRICE));
            // Current Status Text
            vpnText.setText(fromHtml(R.string.unprotected_connections_visibility_bullet_html));
            // getVPNButtonTop/learnVPNButton
            learnVPNButton.setVisibility(View.GONE);
            getVPNButtonTop.setText(R.string.get_express_VPN);
            // getVPNButtonBottom
            getVPNButtonBottom.setText(R.string.get_express_VPN);
        }

        OnGetVPNClickListener l = new OnGetVPNClickListener(isProtectedConnection);
        headerIcon.setOnClickListener(l);
        headerStatus.setOnClickListener(l);
        getVPNButtonTop.setOnClickListener(l);
        getVPNButtonBottom.setOnClickListener(l);
        learnVPNButton.setOnClickListener(l);
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
                i.getExtras().getString("from", "").equals("transfers")) {
            newIntent.setAction(Constants.ACTION_SHOW_TRANSFERS);
        }

        startActivity(newIntent);
        try {
            super.onBackPressed();
        } catch (Throwable ignored) {
        }
    }

    private void appendBullet(int id) {
        TextView t = findView(id);
        t.setText(fromHtml(UNICODE_BULLET + t.getText()));
    }

    private boolean isProtectedConnection() {
        Intent intent = getIntent();
        String action = intent != null ? intent.getAction() : null;
        return action != null && action.equals(Constants.ACTION_SHOW_VPN_STATUS_PROTECTED);
    }

    private Spanned fromHtml(int resId) {
        return fromHtml(getString(resId));
    }

    // once we get to API 24, we can replace the method for the new one
    @SuppressWarnings("deprecated")
    private static Spanned fromHtml(String html) {
        //noinspection deprecation
        return Html.fromHtml(html);
    }

    private static final class OnGetVPNClickListener implements View.OnClickListener {

        private final boolean isProtectedConnection;

        OnGetVPNClickListener(boolean isProtectedConnection) {
            this.isProtectedConnection = isProtectedConnection;
        }

        @Override
        public void onClick(View v) {
            if (isProtectedConnection) {
                UIUtils.openURL(v.getContext(), Constants.VPN_LEARN_MORE_URL);
            } else {
                String vpnUrl = Constants.IS_GOOGLE_PLAY_DISTRIBUTION ?
                        Constants.EXPRESSVPN_URL_BASIC :
                        Constants.EXPRESSVPN_URL_PLUS;

                UIUtils.openURL(v.getContext(), vpnUrl);
            }
        }
    }
}
