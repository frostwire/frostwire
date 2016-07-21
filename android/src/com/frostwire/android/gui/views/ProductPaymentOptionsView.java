/*
 * Created by Angel Leon (@gubatron), Marcelina Knitter (marcelinkaaa),
 * Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.frostwire.android.R;

/**
 * Created on 7/7/16.
 *
 * @author gubatron
 * @author marcelinkaaa
 * @author aldenml
 */
public class ProductPaymentOptionsView extends LinearLayout {

    public enum PurchaseButton {
        AutomaticRenewal,
        OneTimePurchase
    }

    private ProductPaymentOptionsViewListener listener;

    public ProductPaymentOptionsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View.inflate(getContext(), R.layout.view_product_payment_options, this);
        initClickListeners();
    }

    private void initClickListeners() {
        BuyButtonClickListener clickListener = new BuyButtonClickListener();
        final View automaticRenewalLayout = findViewById(R.id.view_product_payment_options_buy_automatic_renewal_layout);
        final View automaticRenewalButton = findViewById(R.id.view_product_payment_options_buy_automatic_renewal_button);
        final View automaticRenewalText = findViewById(R.id.view_product_payment_options_buy_automatic_renewal_text);
        final View automaticRenewalRemark = findViewById(R.id.view_product_payment_options_buy_automatic_renewal_trial_remark);
        automaticRenewalLayout.setClickable(true);
        automaticRenewalButton.setClickable(true);
        automaticRenewalText.setClickable(true);
        automaticRenewalRemark.setClickable(true);
        automaticRenewalLayout.setOnClickListener(clickListener);
        automaticRenewalButton.setOnClickListener(clickListener);
        automaticRenewalText.setOnClickListener(clickListener);
        automaticRenewalRemark.setOnClickListener(clickListener);
        final View oneTimeBuyLayout = findViewById(R.id.view_product_payment_options_buy_one_time_layout);
        final View oneTimeBuyButton = findViewById(R.id.view_product_payment_options_buy_one_time_button);
        final View oneTimeBuyText = findViewById(R.id.view_product_payment_options_buy_one_time_text);
        final View oneTimeBuyRemark = findViewById(R.id.view_product_payment_options_buy_one_time_trial_remark);
        oneTimeBuyLayout.setClickable(true);
        oneTimeBuyButton.setClickable(true);
        oneTimeBuyText.setClickable(true);
        oneTimeBuyRemark.setClickable(true);
        oneTimeBuyLayout.setOnClickListener(clickListener);
        oneTimeBuyButton.setOnClickListener(clickListener);
        oneTimeBuyText.setOnClickListener(clickListener);
        oneTimeBuyRemark.setOnClickListener(clickListener);
    }

    public void setBuyButtonsListener(ProductPaymentOptionsViewListener listener) {
        this.listener = listener;
    }

    public void hideProgressBarOnButton(PurchaseButton button) {
        View[] views = getButtonAndProgressBarViews(button);
        views[0].setVisibility(View.VISIBLE);
        views[1].setVisibility(View.GONE);
    }

    private void showProgressBarOnButton(PurchaseButton button) {
        View[] views = getButtonAndProgressBarViews(button);
        views[0].setVisibility(View.GONE);
        views[1].setVisibility(View.VISIBLE);
    }

    private View[] getButtonAndProgressBarViews(PurchaseButton button) {
        int buttonId = button == PurchaseButton.AutomaticRenewal ?
                R.id.view_product_payment_options_buy_automatic_renewal_button :
                R.id.view_product_payment_options_buy_one_time_button;
        int progressBarId = button == PurchaseButton.AutomaticRenewal ?
                R.id.view_product_payment_options_progressbar_automatic_renewal :
                R.id.view_product_payment_options_progressbar_one_time;

        TextView purchaseButton = (TextView) findViewById(buttonId);
        ProgressBar progressBar = (ProgressBar) findViewById(progressBarId);

        return new View[]{purchaseButton, progressBar};
    }

    private class BuyButtonClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            if (listener != null) {
                switch (v.getId()) {
                    case R.id.view_product_payment_options_buy_automatic_renewal_layout:
                    case R.id.view_product_payment_options_buy_automatic_renewal_button:
                    case R.id.view_product_payment_options_buy_automatic_renewal_text:
                    case R.id.view_product_payment_options_buy_automatic_renewal_trial_remark:
                        showProgressBarOnButton(PurchaseButton.AutomaticRenewal);
                        listener.onBuyAutomaticRenewal();
                        break;
                    case R.id.view_product_payment_options_buy_one_time_layout:
                    case R.id.view_product_payment_options_buy_one_time_button:
                    case R.id.view_product_payment_options_buy_one_time_text:
                    case R.id.view_product_payment_options_buy_one_time_trial_remark:
                        showProgressBarOnButton(PurchaseButton.OneTimePurchase);
                        listener.onBuyOneTime();
                        break;
                }
            }
        }
    }
}
