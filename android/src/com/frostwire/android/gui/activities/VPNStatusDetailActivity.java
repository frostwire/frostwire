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
    private final VPNCompanyInfo VPN = VPNCompanyInfo.PIA;

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

        appendBullet(R.id.view_vpn_status_vpn_client_feature_1, VPN.view_vpn_status_vpn_client_feature_1);
        appendBullet(R.id.view_vpn_status_vpn_client_feature_2, VPN.view_vpn_status_vpn_client_feature_2);
        appendBullet(R.id.view_vpn_status_vpn_client_feature_3, VPN.view_vpn_status_vpn_client_feature_3);
        appendBullet(R.id.view_vpn_status_vpn_client_feature_4, VPN.view_vpn_status_vpn_client_feature_4);

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
            vpnText.setText(fromHtml(VPN.protected_connections_visibility_bullet_html));
            // getVPNButtonTop/learnVPNButton
            getVPNButtonTop.setVisibility(View.GONE);
            learnVPNButton.setText(R.string.learn_more);
            // getVPNButtonBottom
            getVPNButtonBottom.setText(VPN.visit_vpn_client);
        } else {
            // Current Status Icon
            headerIcon.setImageResource(R.drawable.vpn_icon_off_info);
            // Current Status Title
            headerStatus.setText(R.string.unprotected_connection);
            headerStatus.setTextColor(Color.RED);
            // Current Status VPN client price
            vpnMoneyBack.setText(VPN.vpn_money_back);
            vpnPrice.setText(getString(R.string.vpn_price, VPN.startingPrice));
            // Current Status Text
            vpnText.setText(fromHtml(VPN.unprotected_connections_visibility_bullet_html));
            // getVPNButtonTop/learnVPNButton
            learnVPNButton.setVisibility(View.GONE);
            getVPNButtonTop.setText(VPN.get_vpn_client);
            // getVPNButtonBottom
            getVPNButtonBottom.setText(VPN.get_vpn_client);
        }

        OnGetVPNClickListener l = new OnGetVPNClickListener(isProtectedConnection, VPN);
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

    private void appendBullet(int textViewId, int bulletTextId) {
        TextView t = findView(textViewId);
        String text = getResources().getString(bulletTextId);
        t.setText(fromHtml(UNICODE_BULLET + text));
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
        private final VPNCompanyInfo VPN;

        OnGetVPNClickListener(boolean isProtectedConnection, VPNCompanyInfo VPN) {
            this.isProtectedConnection = isProtectedConnection;
            this.VPN = VPN;
        }

        @Override
        public void onClick(View v) {
            if (isProtectedConnection) {
                UIUtils.openURL(v.getContext(), VPN.learnMoreURL);
            } else {
                UIUtils.openURL(v.getContext(), VPN.mainURL);
            }
        }
    }

    private enum VPNCompanyInfo {
        PIA(R.string.vpn_client_PIA, // company name res id
                "http://www.frostwire.com/vpn.pia", // main url
                "http://www.frostwire.com/vpn.pia", // learn more url
                3.33f, // starting price
                R.string.vpn_money_back_PIA, // days money-back guarantee
                R.string.unprotected_connections_visibility_bullet_html_PIA, // unprotected_connections_visibility_bullet_html
                R.string.protected_connections_visibility_bullet_html_PIA, // protected_connections_visibility_bullet_html
                R.string.visit_vpn_client_PIA, // visit_vpn_client
                R.string.get_PIA, // get_vpn_client
                R.string.vpn_client_feature_1_PIA, // view_vpn_status_vpn_client_feature_1
                R.string.vpn_client_feature_2_PIA, // view_vpn_status_vpn_client_feature_2
                R.string.vpn_client_feature_3_PIA, // view_vpn_status_vpn_client_feature_3
                R.string.vpn_client_feature_4_PIA, // view_vpn_status_vpn_client_feature_4
                R.drawable.pia), // view_vpn_status_company_graphic

        ExpressVPN(R.string.vpn_client_ExpressVPN, // company name res id
                "http://www.frostwire.com/vpn.expressvpn", // main url
                "http://www.frostwire.com/vpn.expressvpn.learnmore", // learn more url
                8.32f, // starting price
                R.string.vpn_money_back_ExpressVPN, // days money-back guarantee
                R.string.unprotected_connections_visibility_bullet_html_ExpressVPN, // unprotected_connections_visibility_bullet_html
                R.string.protected_connections_visibility_bullet_html_ExpressVPN, // protected_connections_visibility_bullet_html
                R.string.visit_vpn_client_ExpressVPN, // visit_vpn_client
                R.string.get_ExpressVPN, // get_vpn_client,
                R.string.vpn_client_feature_1_ExpressVPN, // view_vpn_status_vpn_client_feature_1
                R.string.vpn_client_feature_2_ExpressVPN, // view_vpn_status_vpn_client_feature_2
                R.string.vpn_client_feature_3_ExpressVPN, // view_vpn_status_vpn_client_feature_3
                R.string.vpn_client_feature_4_ExpressVPN, // view_vpn_status_vpn_client_feature_4
                R.drawable.express_vpn); // view_vpn_status_company_graphic

        final int companyNameStrResourceId;
        final String mainURL;
        final String learnMoreURL;
        final float startingPrice;
        final int vpn_money_back;
        final int unprotected_connections_visibility_bullet_html;
        final int protected_connections_visibility_bullet_html;
        final int visit_vpn_client;
        final int get_vpn_client;
        final int view_vpn_status_vpn_client_feature_1;
        final int view_vpn_status_vpn_client_feature_2;
        final int view_vpn_status_vpn_client_feature_3;
        final int view_vpn_status_vpn_client_feature_4;
        final int view_vpn_status_company_graphic;

        VPNCompanyInfo(int companyNameStrResourceId,
                       String mainURL,
                       String learnMoreURL,
                       float startingPrice,
                       int vpn_money_back,
                       int unprotected_connections_visibility_bullet_html,
                       int protected_connections_visibility_bullet_html,
                       int visit_vpn_client,
                       int get_vpn_client,
                       int view_vpn_status_vpn_client_feature_1,
                       int view_vpn_status_vpn_client_feature_2,
                       int view_vpn_status_vpn_client_feature_3,
                       int view_vpn_status_vpn_client_feature_4,
                       int view_vpn_status_company_graphic) {
            this.companyNameStrResourceId = companyNameStrResourceId;
            this.mainURL = mainURL;
            this.learnMoreURL = learnMoreURL;
            this.startingPrice = startingPrice;
            this.vpn_money_back = vpn_money_back;
            this.unprotected_connections_visibility_bullet_html = unprotected_connections_visibility_bullet_html;
            this.protected_connections_visibility_bullet_html = protected_connections_visibility_bullet_html;
            this.visit_vpn_client = visit_vpn_client;
            this.get_vpn_client = get_vpn_client;
            this.view_vpn_status_vpn_client_feature_1 = view_vpn_status_vpn_client_feature_1;
            this.view_vpn_status_vpn_client_feature_2 = view_vpn_status_vpn_client_feature_2;
            this.view_vpn_status_vpn_client_feature_3 = view_vpn_status_vpn_client_feature_3;
            this.view_vpn_status_vpn_client_feature_4 = view_vpn_status_vpn_client_feature_4;
            this.view_vpn_status_company_graphic = view_vpn_status_company_graphic;
        }
    }
}
