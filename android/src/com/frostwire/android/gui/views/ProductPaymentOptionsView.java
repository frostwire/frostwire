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
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.frostwire.android.R;

/**
 * Created on 7/7/16.
 * @author gubatron
 * @author marcelinkaaa
 * @author aldenml
 *
 */
public class ProductPaymentOptionsView extends LinearLayout {
    private ProductPaymentOptionsViewListener listener;

    public ProductPaymentOptionsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View.inflate(getContext(), R.layout.view_product_payment_options, this);
        BuyButtonClickListener clickListener = new BuyButtonClickListener();

        final TextView automaticRenewalBuyButton = (TextView) findViewById(R.id.view_product_payment_options_buy_automatic_renewal_button);
        automaticRenewalBuyButton.setOnClickListener(clickListener);

        final TextView oneTimeBuyButton = (TextView) findViewById(R.id.view_product_payment_options_buy_one_time_button);
        oneTimeBuyButton.setOnClickListener(clickListener);
    }

    public void setBuyButtonsListener(ProductPaymentOptionsViewListener listener) {
        this.listener = listener;
    }

    private class BuyButtonClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            if (listener != null) {
                switch (v.getId()) {
                    case R.id.view_product_payment_options_buy_automatic_renewal_button:
                        listener.onBuyAutomaticRenewal();
                        break;
                    case R.id.view_product_payment_options_buy_one_time_button:
                        listener.onBuyOneTime();
                        break;
                }
            }
        }
    }
}