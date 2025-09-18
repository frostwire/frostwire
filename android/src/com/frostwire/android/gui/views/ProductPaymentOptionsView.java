/*
 *     Created by Angel Leon (@gubatron), Marcelina Knitter (marcelinkaaa),
 *  *            Alden Torres (aldenml)
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

package com.frostwire.android.gui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.frostwire.android.R;

/**
 * Presents the options to pay for a given product.
 * <p>
 * Created on 7/7/16.
 *
 * @author gubatron
 * @author marcelinkaaa
 * @author aldenml
 */
public class ProductPaymentOptionsView extends LinearLayout {

    public enum PayButtonType {
        SUBSCRIPTION(0),
        ONE_TIME(1),
        REWARD_VIDEO(2);
        public final int offset;

        PayButtonType(int arrayOffset) {
            offset = arrayOffset;
        }
    }

    private OnBuyListener listener;
    private TextView autoRenewalTextView = null;
    private View[] buttons = null;
    private View[] progressBars = null;
    private View[] paymentOptionsLayouts = null;

    public ProductPaymentOptionsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View.inflate(getContext(), R.layout.view_product_payment_options, this);
        TextView selectOptionViewTextView = findViewById(R.id.view_product_payment_options_select_option_below_text);
        selectOptionViewTextView.setVisibility(View.GONE);
        autoRenewalTextView = findViewById(R.id.view_product_payment_options_automatic_renewal);
        buttons = new View[]{
                findViewById(R.id.view_product_payment_options_buy_automatic_renewal_button),
                findViewById(R.id.view_product_payment_options_buy_one_time_button),
                findViewById(R.id.view_product_payment_options_watch_rewarded_video_button)
        };
        progressBars = new View[]{
                findViewById(R.id.view_product_payment_options_buy_automatic_renewal_progressbar),
                findViewById(R.id.view_product_payment_options_buy_one_time_progressbar),
                findViewById(R.id.view_product_payment_options_watch_rewarded_video_progressbar)
        };
        initClickListeners();
        paymentOptionsLayouts = new View[]{
                findViewById(R.id.view_product_payment_options_buy_automatic_renewal_layout),
                findViewById(R.id.view_product_payment_options_buy_one_time_layout),
                findViewById(R.id.view_product_payment_options_watch_rewarded_video_layout)
        };
    }

    public void setOnBuyListener(OnBuyListener listener) {
        this.listener = listener;
    }

    /**
     * More like "Progress circles"
     */
    public void stopProgressBar() {
        stopProgressBar(PayButtonType.SUBSCRIPTION);
        stopProgressBar(PayButtonType.ONE_TIME);
        stopProgressBar(PayButtonType.REWARD_VIDEO);
    }

    private void initClickListeners() {
        final BuyButtonClickListener clickListener = new BuyButtonClickListener();
        buttons[PayButtonType.SUBSCRIPTION.offset].setOnClickListener(clickListener);
        buttons[PayButtonType.ONE_TIME.offset].setOnClickListener(clickListener);
        buttons[PayButtonType.REWARD_VIDEO.offset].setOnClickListener(clickListener);
    }

    public void stopProgressBar(PayButtonType buttonType) {
        switch (buttonType) {
            case SUBSCRIPTION:
                buttons[PayButtonType.SUBSCRIPTION.offset].setVisibility(View.VISIBLE);
                progressBars[PayButtonType.SUBSCRIPTION.offset].setVisibility(View.GONE);
                break;

            case ONE_TIME:
                buttons[PayButtonType.ONE_TIME.offset].setVisibility(View.VISIBLE);
                progressBars[PayButtonType.ONE_TIME.offset].setVisibility(View.GONE);
                break;

            case REWARD_VIDEO:
                buttons[PayButtonType.REWARD_VIDEO.offset].setVisibility(View.VISIBLE);
                progressBars[PayButtonType.REWARD_VIDEO.offset].setVisibility(View.GONE);
                break;
        }
    }

    public void startProgressBar(PayButtonType buttonType) {
        switch (buttonType) {
            case SUBSCRIPTION:
                buttons[PayButtonType.SUBSCRIPTION.offset].setVisibility(View.GONE);
                progressBars[PayButtonType.SUBSCRIPTION.offset].setVisibility(View.VISIBLE);
                break;

            case ONE_TIME:
                buttons[PayButtonType.ONE_TIME.offset].setVisibility(View.GONE);
                progressBars[PayButtonType.ONE_TIME.offset].setVisibility(View.VISIBLE);
                break;

            case REWARD_VIDEO:
                buttons[PayButtonType.REWARD_VIDEO.offset].setVisibility(View.GONE);
                progressBars[PayButtonType.REWARD_VIDEO.offset].setVisibility(View.VISIBLE);
                break;
        }
    }

    public void refreshOptionsVisibility(final ProductCardView selectedProductCard) {
        if (selectedProductCard == null) {
            return;
        }
        PaymentOptionsVisibility paymentOptionsVisibility = selectedProductCard.getPaymentOptionsVisibility();
        if (paymentOptionsLayouts == null || paymentOptionsVisibility == null) {
            return;
        }
        if (paymentOptionsLayouts[PayButtonType.ONE_TIME.offset] != null) {
            paymentOptionsLayouts[PayButtonType.ONE_TIME.offset].setVisibility(paymentOptionsVisibility.oneTimeOption ? View.VISIBLE : View.GONE);
        }
        if (paymentOptionsLayouts[PayButtonType.SUBSCRIPTION.offset] != null) {
            paymentOptionsLayouts[PayButtonType.SUBSCRIPTION.offset].setVisibility(paymentOptionsVisibility.subscriptionOption ? View.VISIBLE : View.GONE);
            if (paymentOptionsVisibility.subscriptionOption) {
                String subscriptionPeriodString = selectedProductCard.getSubscriptionPeriodString().toLowerCase();
                subscriptionPeriodString = subscriptionPeriodString.substring(0, 1).toUpperCase() + subscriptionPeriodString.substring(1);
                String autoRenewalString = getResources().getString(R.string.automatic_renewal) + " " + subscriptionPeriodString;
                autoRenewalTextView.setText(autoRenewalString);
            }
        }
        if (paymentOptionsLayouts[PayButtonType.REWARD_VIDEO.offset] != null) {
            paymentOptionsLayouts[PayButtonType.REWARD_VIDEO.offset].setVisibility(paymentOptionsVisibility.rewardOption ? View.VISIBLE : View.GONE);
        }
    }

    private class BuyButtonClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            if (listener != null && v != null && v.getId() == R.id.view_product_payment_options_watch_rewarded_video_button) {
                startProgressBar(PayButtonType.REWARD_VIDEO);
                listener.onRewardedVideo();
            }
        }
    }

    public interface OnBuyListener {

        void onRewardedVideo();
    }
}
