/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (marcelinkaaa)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.android.billingclient.api.PurchasesUpdatedListener;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.PaymentOptionsVisibility;
import com.frostwire.android.gui.views.ProductCardView;
import com.frostwire.android.gui.views.ProductPaymentOptionsView;
import com.frostwire.android.offers.MoPubAdNetwork;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.offers.PlayStore;
import com.frostwire.android.offers.Product;
import com.frostwire.android.offers.Products;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.mopub.mobileads.MoPubRewardedVideos;

import java.lang.ref.WeakReference;

import static com.frostwire.android.util.Asyncs.async;

/**
 * @author gubatron
 * @author aldenml
 */
public final class BuyActivity extends AbstractActivity {

    private static final Logger LOG = Logger.getLogger(BuyActivity.class);

    public static final String INTERSTITIAL_MODE = "interstitial_mode";
    public static final int PURCHASE_SUCCESSFUL_RESULT_CODE = 0xaadd;
    public static final String EXTRA_KEY_PURCHASE_TIMESTAMP = "purchase_timestamp";

    private static final String LAST_SELECTED_CARD_ID_KEY = "last_selected_card_view_id";
    private static final String PAYMENT_OPTIONS_VISIBILITY_KEY = "payment_options_visibility";
    private static final String OFFER_ACCEPTED = "offer_accepted";

    private static int REWARD_FREE_AD_MINUTES = Constants.MIN_REWARD_AD_FREE_MINUTES;

    /**
     * Tasks for this branch:
     * [x] Displaying a rewarded ad.
     * [x] Logic managing the received reward to turn off all advertising and offers to remove advertisement.
     * [x] Ads should appear again everywhere and options to remove ads too after time expires
     * [x] Being able to remotely disable rewarded ad logic
     * [x] Being able to remotely set how many minutes the reward is for
     * [x] If it's FrostWire Plus, make sure only the rewarded ad option is displayed and not the other purchasable ones
     * [x] Proper integration with settings menu, shows how many minutes left we have if possible, doesn't invoke BuyActivity
     */

    private ProductCardView cardNminutes;
    private ProductCardView card30days;
    private ProductCardView card1year;
    private ProductCardView card6months;
    private ProductCardView selectedProductCard;

    /**
     * the view with the "Buy" buttons for subscription or one time, which is reused for the PlayStore purchase card views
     */
    private ProductPaymentOptionsView paymentOptionsView;

    private boolean offerAccepted;

    public BuyActivity() {
        super(R.layout.activity_buy);
        async(BuyActivity::getRewardFreeAdMinutesFromConfigAsync, new WeakReference<>(this)); // in the meantime use the default value Constants.MIN_REWARD_AD_FREE_MINUTES
    }

    private static void getRewardFreeAdMinutesFromConfigAsync(WeakReference<BuyActivity> buyActivityRef) {
        REWARD_FREE_AD_MINUTES = ConfigurationManager.instance().getInt(Constants.PREF_KEY_GUI_REWARD_AD_FREE_MINUTES);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            if (Ref.alive(buyActivityRef)) {
                buyActivityRef.get().refreshPaymentOptionsViewRewardMinutesTextView();
            }
        });
    }

    private void refreshPaymentOptionsViewRewardMinutesTextView() {
        if (paymentOptionsView != null) {
            TextView textView = paymentOptionsView.findViewById(R.id.view_product_payment_options_temporary_ad_removal_description_textview);
            String temporary_ad_removal_description = textView.getResources().getString(R.string.temporary_ad_removal_description, REWARD_FREE_AD_MINUTES);
            textView.setText(temporary_ad_removal_description);
        }
    }

    private final PurchasesUpdatedListener onPurchasesUpdatedListener = (billingResult, purchases) -> BuyActivity.this.onPurchasesUpdatedOld(billingResult.getResponseCode());

    private void purchaseProduct(int tagId) {
        Product p = (Product) selectedProductCard.getTag(tagId);
        if (p != null) {
            PlayStore playStore = PlayStore.getInstance(this);
            if (playStore == null) {
                LOG.error("purchaseProduct(tagId=" + tagId + ") failed, could not get an instance of PlayStore");
                return;
            }
            playStore.setGlobalPurchasesUpdatedListenerWeakRef(onPurchasesUpdatedListener);
            playStore.purchase(this, p);
        }
    }

    @Override
    protected void initToolbar(Toolbar toolbar) {
        toolbar.setTitle(getActionBarTitle());
    }

    @Override
    protected void initComponents(Bundle savedInstanceState) {
        final boolean interstitialMode = isInterstitial();
        offerAccepted = savedInstanceState != null &&
                savedInstanceState.containsKey(OFFER_ACCEPTED) &&
                savedInstanceState.getBoolean(OFFER_ACCEPTED, false);
        if (interstitialMode) {
            initInterstitialModeActionBar(getActionBarTitle());
        }
        initOfferLayer(interstitialMode);
        initProductCards(getLastSelectedCardViewId(savedInstanceState));
        initPaymentOptionsView(getLastPaymentOptionsViewVisibility(savedInstanceState));

        // If Google Store not ready or available, auto-select rewarded ad option
        if (card30days.getVisibility() == View.GONE ||
            card1year.getVisibility() == View.GONE ||
            card6months.getVisibility() == View.GONE) {
            cardNminutes.performClick();
        }

        if (!MoPubRewardedVideos.hasRewardedVideo(MoPubAdNetwork.UNIT_ID_REWARDED_VIDEO)) {
            Offers.MOPUB.loadRewardedVideo();
        }
    }

    private String getActionBarTitle() {
        final String titlePrefix = getString(R.string.remove_ads);
        return titlePrefix + ". " + getString(UIUtils.randomPitchResId(false)) + ".";
    }

    private void initOfferLayer(boolean interstitialMode) {
        if (!interstitialMode) {
            View offerLayout = findView(R.id.activity_buy_interstitial_linear_layout);
            offerLayout.setVisibility(View.GONE);
            return;
        }

        // user rotates screen after having already accepted the offer
        if (offerAccepted) {
            View offerLayout = findView(R.id.activity_buy_interstitial_linear_layout);
            offerLayout.setVisibility(View.GONE);
            return;
        }

        final InterstitialOfferDismissButtonClickListener dismissOfferClickListener = new InterstitialOfferDismissButtonClickListener();
        ImageButton dismissButton = findView(R.id.activity_buy_interstitial_dismiss_button);
        dismissButton.setOnClickListener(dismissOfferClickListener);

        final OfferClickListener offerClickListener = new OfferClickListener();
        View offerLayout = findView(R.id.activity_buy_interstitial_linear_layout);
        offerLayout.setOnClickListener(offerClickListener);

        final TextView randomPitch = findView(R.id.activity_buy_interstitial_random_pitch);
        randomPitch.setText(UIUtils.randomPitchResId(true));
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        scrollToSelectedCard();
    }

    @Override
    public void onBackPressed() {
        if (isInterstitial()) {
            onInterstitialActionBarDismiss();
            finish();
        } else {
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // initComponents() and setToolbar() have been called

        // from DEEP-Link House Ad pointing to app://com.frostwire.android/remove-ads
        Intent intent = getIntent();
        if (intent != null) {
            Uri data = intent.getData();
            if (data != null && "app://com.frostwire.android/remove-ads".equals(data.toString()) && cardNminutes != null) {
                cardNminutes.performClick();
            }
        }
    }

    private void onInterstitialActionBarDismiss() {
        if (isInterstitial()) {
            Intent intent = getIntent();
            boolean dismissActivityAfterward = intent.getBooleanExtra("dismissActivityAfterward", false);
            boolean shutdownActivityAfterwards = intent.getBooleanExtra("shutdownActivityAfterwards", false);

            Offers.AdNetworkHelper.dismissAndOrShutdownIfNecessary(
                    this,
                    dismissActivityAfterward,
                    shutdownActivityAfterwards,
                    false,
                    getApplication());
        }
    }

    private void initInterstitialModeActionBar(String title) {
        View v = findView(R.id.activity_buy_actionbar_interstitial);
        v.setVisibility(View.VISIBLE);

        View toolbar = findToolbar();
        toolbar.setVisibility(View.GONE);

        TextView titleTextView = findView(R.id.activity_buy_actionbar_interstitial_buy_activity_title);
        titleTextView.setText(title);

        ImageButton closeButton = findView(R.id.activity_buy_actionbar_interstitial_buy_activity_dismiss_button);
        closeButton.setOnClickListener(new InterstitialActionBarDismissButtonClickListener());
    }

    private void initProductCards(int lastSelectedCardViewId) {
        View.OnClickListener cardClickListener = new ProductCardViewOnClickListener();
        card30days = findView(R.id.activity_buy_product_card_30_days);
        card1year = findView(R.id.activity_buy_product_card_1_year);
        card6months = findView(R.id.activity_buy_product_card_6_months);
        cardNminutes = findView(R.id.activity_buy_product_card_reward);

        if (REWARD_FREE_AD_MINUTES > 0) {
            initRewardCard(cardNminutes);
            cardNminutes.setVisibility(View.VISIBLE);
            cardNminutes.setOnClickListener(cardClickListener);
        } else {
            cardNminutes.setVisibility(View.GONE);
        }

        PlayStore store = PlayStore.getInstance(this);

        if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION || Constants.IS_BASIC_AND_DEBUG) {
            initProductCard(card30days, store, Products.SUBS_DISABLE_ADS_1_MONTH_SKU, Products.INAPP_DISABLE_ADS_1_MONTH_SKU);
            initProductCard(card1year, store, Products.SUBS_DISABLE_ADS_1_YEAR_SKU, Products.INAPP_DISABLE_ADS_1_YEAR_SKU);
            initProductCard(card6months, store, Products.SUBS_DISABLE_ADS_6_MONTHS_SKU, Products.INAPP_DISABLE_ADS_6_MONTHS_SKU);
            card30days.setOnClickListener(cardClickListener);
            card1year.setOnClickListener(cardClickListener);
            card6months.setOnClickListener(cardClickListener);
        } else {
            card30days.setVisibility(View.GONE);
            card1year.setVisibility(View.GONE);
            card6months.setVisibility(View.GONE);
        }

        initLastCardSelection(lastSelectedCardViewId);
    }

    private void initLastCardSelection(int lastSelectedCardViewId) {
        switch (lastSelectedCardViewId) {
            case R.id.activity_buy_product_card_reward:
                selectedProductCard = cardNminutes;
            case R.id.activity_buy_product_card_30_days:
                selectedProductCard = card30days;
                break;
            case R.id.activity_buy_product_card_6_months:
                selectedProductCard = card6months;
                break;
            case R.id.activity_buy_product_card_1_year:
            default:
                selectedProductCard = card1year;
                break;
        }
        highlightSelectedCard();
    }

    private void initPaymentOptionsView(int paymentOptionsVisibility) {
        paymentOptionsView = findView(R.id.activity_buy_product_payment_options_view);
        paymentOptionsView.setVisibility(paymentOptionsVisibility);
        paymentOptionsView.setOnBuyListener(new ProductPaymentOptionsView.OnBuyListener() {
            @Override
            public void onAutomaticRenewal() {
                purchaseProduct(R.id.subs_product_tag_id);
            }

            @Override
            public void onOneTime() {
                purchaseProduct(R.id.inapp_product_tag_id);
            }

            @Override
            public void onRewardedVideo() {
                playRewardedVideo();
            }
        });

        if (paymentOptionsVisibility == View.VISIBLE) {
            showPaymentOptionsBelowSelectedCard();
        }
    }

    private void playRewardedVideo() {
        paymentOptionsView.startProgressBar(ProductPaymentOptionsView.PayButtonType.REWARD_VIDEO);
        Offers.showRewardedVideo(this);
    }

    private void initRewardCard(ProductCardView card) {
        if (card == null) {
            throw new IllegalArgumentException("card argument can't be null");
        }

        final Resources resources = card.getResources();
        final String reward_product_title = (resources != null) ?
                String.format(resources.getString(R.string.reward_product_title), REWARD_FREE_AD_MINUTES) :
                String.format("%d minutes", REWARD_FREE_AD_MINUTES);
        final String reward_product_description = (resources != null) ?
                resources.getString(R.string.ad_free) : "Ad-free";
        final String reward_product_price = (resources != null) ?
                resources.getString(R.string.reward_product_price) :
                "Free, Play 1 Video Ad";

        card.setPaymentOptionsVisibility(new PaymentOptionsVisibility(false, false, true));
        Product productReward = new Product() {
            @Override
            public String sku() {
                return Products.REWARDS_DISABLE_ADS_MINUTES_SKU;
            }

            @Override
            public boolean subscription() {
                return false;
            }

            @Override
            public String title() {
                return reward_product_title;
            }

            @Override
            public String description() {
                return reward_product_description;
            }

            @Override
            public String price() {
                return reward_product_price;
            }

            @Override
            public String currency() {
                return "";
            }

            @Override
            public boolean purchased() {
                return false;
            }

            @Override
            public long purchaseTime() {
                return REWARD_FREE_AD_MINUTES * 60_000;
            }

            @Override
            public boolean available() {
                return true;
            }

            @Override
            public boolean enabled(String feature) {
                return false;
            }
        };
        card.setTag(R.id.reward_product_tag_id, productReward);
        card.updateData(productReward);
        card.updateTitle(productReward.title());
    }

    private void initProductCard(ProductCardView card, PlayStore store, String subsSKU, String
            inappSKU) {
        if (card == null) {
            throw new IllegalArgumentException("card argument can't be null");
        }
        if (store == null) {
            throw new IllegalArgumentException("store argument can't be null");
        }
        if (subsSKU == null) {
            throw new IllegalArgumentException("subsSKU argument can't be null");
        }
        if (inappSKU == null) {
            throw new IllegalArgumentException("inappSKU argument can't be null");
        }

        Product prodSubs = store.product(subsSKU);
        Product prodInApp = store.product(inappSKU);

        if (prodSubs == null || prodInApp == null) {
            //UIUtils.showLongMessage(this, R.string.play_store_out_of_sync);
            //finish();
            card.setVisibility(View.GONE);
            return;
        }

        card.setTag(R.id.subs_product_tag_id, prodSubs);
        card.setTag(R.id.inapp_product_tag_id, prodInApp);
        card.setPaymentOptionsVisibility(new PaymentOptionsVisibility(true, true, false));

        card.updateData(prodSubs);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(LAST_SELECTED_CARD_ID_KEY, selectedProductCard.getId());
        outState.putInt(PAYMENT_OPTIONS_VISIBILITY_KEY, paymentOptionsView.getVisibility());
        outState.putBoolean(OFFER_ACCEPTED, offerAccepted);
        super.onSaveInstanceState(outState);
    }

    private int getLastSelectedCardViewId(Bundle savedInstanceState) {
        int lastSelectedCardViewId = -1;
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(LAST_SELECTED_CARD_ID_KEY)) {
                lastSelectedCardViewId = savedInstanceState.getInt(LAST_SELECTED_CARD_ID_KEY);
            }
        }
        return lastSelectedCardViewId;
    }

    private int getLastPaymentOptionsViewVisibility(Bundle savedInstanceState) {
        int paymentOptionsVisibility = View.GONE;
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(PAYMENT_OPTIONS_VISIBILITY_KEY)) {
                paymentOptionsVisibility = savedInstanceState.getInt(PAYMENT_OPTIONS_VISIBILITY_KEY);
            }
        }
        return paymentOptionsVisibility;
    }

    private void highlightSelectedCard() {
        if (selectedProductCard == null) {
            return;
        }
        card30days.setSelected(selectedProductCard == card30days);
        card1year.setSelected(selectedProductCard == card1year);
        card6months.setSelected(selectedProductCard == card6months);
        cardNminutes.setSelected(selectedProductCard == cardNminutes);
    }

    private void scrollToSelectedCard() {
        ScrollView scrollView = findView(R.id.activity_buy_scrollview);
        LinearLayout linearLayout = (LinearLayout) scrollView.getChildAt(0);
        int index = linearLayout.indexOfChild(selectedProductCard);
        int cardHeight = selectedProductCard.getHeight() + selectedProductCard.getPaddingTop();
        scrollView.scrollTo(0, index * cardHeight);
    }

    private void showPaymentOptionsBelowSelectedCard() {
        paymentOptionsView.refreshOptionsVisibility(selectedProductCard);
        final ViewGroup scrollView = findView(R.id.activity_buy_scrollview);
        final ViewGroup layout = (ViewGroup) scrollView.getChildAt(0);
        if (layout != null) {
            int selectedCardIndex = layout.indexOfChild(selectedProductCard);
            final int paymentOptionsViewIndex = layout.indexOfChild(paymentOptionsView);

            if (paymentOptionsView.getVisibility() == View.VISIBLE) {
                if (paymentOptionsViewIndex - 1 == selectedCardIndex) {
                    // no need to animate payment options on the same card
                    // where it's already shown.
                    return;
                }

                paymentOptionsView.animate().setDuration(200)
                        .scaleY(0).setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> scaleDownPaymentOptionsView(layout))
                        .start();
            } else {
                // first time shown
                scaleDownPaymentOptionsView(layout);
            }
        }
    }

    // where the actual removal of the view and re-addition to the layout happens.
    private void scaleDownPaymentOptionsView(final ViewGroup layout) {
        layout.removeView(paymentOptionsView);
        int selectedCardIndex = layout.indexOfChild(selectedProductCard);
        paymentOptionsView.setVisibility(View.VISIBLE);
        layout.addView(paymentOptionsView, selectedCardIndex + 1);
        paymentOptionsView.animate().setDuration(200)
                .scaleY(1).setInterpolator(new DecelerateInterpolator())
                .start();
    }

    public void stopProgressbars(ProductPaymentOptionsView.PayButtonType payButtonType) {
        paymentOptionsView.stopProgressBar(payButtonType);
    }

    private class ProductCardViewOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (v instanceof ProductCardView) {
                int id = v.getId();
                switch (id) {
                    case R.id.activity_buy_product_card_30_days:
                        selectedProductCard = card30days;
                        break;
                    case R.id.activity_buy_product_card_1_year:
                        selectedProductCard = card1year;
                        break;
                    case R.id.activity_buy_product_card_6_months:
                        selectedProductCard = card6months;
                        break;
                    case R.id.activity_buy_product_card_reward:
                        selectedProductCard = cardNminutes;
                        break;
                    default:
                        throw new IllegalArgumentException("Card view not handled, review layout");
                }
                highlightSelectedCard();
                showPaymentOptionsBelowSelectedCard();
                scrollToSelectedCard();
            }
        }
    }

    private boolean isInterstitial() {
        Intent intent = getIntent();
        return intent != null && intent.getBooleanExtra(INTERSTITIAL_MODE, false);
    }

    private void onPurchasesUpdatedOld(int responseCode) {
        // RESPONSE_CODE = 0 -> Payment Successful
        // user clicked outside of the PlayStore purchase dialog
        if (responseCode != 0) {
            paymentOptionsView.stopProgressBar();

            LOG.info("BuyActivity local receiver -> purchase cancelled");
            return;
        }

        // make sure ads won't show on this session any more if we got a positive response.
        Offers.stopAdNetworks(this);

        LOG.info("BuyActivity local receiver -> purchase finished");
        finish();
    }

    private class InterstitialActionBarDismissButtonClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            onInterstitialActionBarDismiss();
            finish();
        }
    }

    private class InterstitialOfferDismissButtonClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            offerAccepted = false;
            onInterstitialActionBarDismiss();
            finish();
        }
    }

    private class OfferClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            final View offerLayout = findView(R.id.activity_buy_interstitial_linear_layout);
            offerAccepted = true;
            offerLayout.animate().setDuration(500)
                    .translationY(offerLayout.getBottom()).setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(() -> offerLayout.setVisibility(View.GONE))
                    .start();
        }
    }
}
