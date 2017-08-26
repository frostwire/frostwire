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

import com.frostwire.android.R;

/**
 * Created on 7/7/16.
 *
 * @author gubatron
 * @author marcelinkaaa
 * @author aldenml
 */
public class ProductPaymentOptionsView extends LinearLayout {

    private OnBuyListener listener;

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
        automaticRenewalLayout.setOnClickListener(clickListener);
        final View oneTimeBuyLayout = findViewById(R.id.view_product_payment_options_buy_one_time_layout);
        oneTimeBuyLayout.setOnClickListener(clickListener);
    }

    public OnBuyListener getOnBuyListener() {
        return listener;
    }

    public void setOnBuyListener(OnBuyListener listener) {
        this.listener = listener;
    }

    public void stopProgressBar() {
        stopProgressBar(true);
        stopProgressBar(false);
    }

    private void stopProgressBar(boolean subscription) {
        View[] views = getButtonAndProgressBarViews(subscription);
        views[0].setVisibility(View.VISIBLE);
        views[1].setVisibility(View.GONE);
    }

    private void startProgressBar(boolean subscription) {
        View[] views = getButtonAndProgressBarViews(subscription);
        views[0].setVisibility(View.GONE);
        views[1].setVisibility(View.VISIBLE);
    }

    private View[] getButtonAndProgressBarViews(boolean subscription) {
        View[] views = subscription ?
                new View[]{
                        findViewById(R.id.view_product_payment_options_buy_automatic_renewal_button),
                        findViewById(R.id.view_product_payment_options_progressbar_automatic_renewal)
                } :
                new View[]{
                        findViewById(R.id.view_product_payment_options_buy_one_time_button),
                        findViewById(R.id.view_product_payment_options_progressbar_one_time)
                };

        return views;
    }

    private class BuyButtonClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            if (listener != null) {
                switch (v.getId()) {
                    case R.id.view_product_payment_options_buy_automatic_renewal_layout:
                        startProgressBar(true);
                        listener.onAutomaticRenewal();
                        break;
                    case R.id.view_product_payment_options_buy_one_time_layout:
                        startProgressBar(false);
                        listener.onOneTime();
                        break;
                }
            }
        }
    }

    public interface OnBuyListener {

        void onAutomaticRenewal();

        void onOneTime();
    }
}
