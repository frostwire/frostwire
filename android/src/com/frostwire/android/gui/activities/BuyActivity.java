/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (marcelinkaaa)
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

package com.frostwire.android.gui.activities;

import android.app.ActionBar;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.ProductCardView;
import com.frostwire.android.gui.views.ProductPaymentOptionsView;
import com.frostwire.android.gui.views.ProductPaymentOptionsViewListener;
import com.frostwire.android.offers.PlayStore;
import com.frostwire.android.offers.Product;
import com.frostwire.android.offers.Products;
import com.frostwire.logging.Logger;

import java.util.Random;

/**
 * @author gubatron
 * @author aldenml
 */
public class BuyActivity extends AbstractActivity implements ProductPaymentOptionsViewListener {

    public static final String INTERSTITIAL_MODE = "interstitialMode";
    private final String LAST_SELECTED_CARD_ID_KEY = "last_selected_card_view_id";
    private final String PAYMENT_OPTIONS_VISIBILITY_KEY = "payment_options_visibility";
    private final String OFFER_ACCEPTED = "offer_accepted";

    private Logger LOGGER = Logger.getLogger(BuyActivity.class);
    private ProductCardView card30days;
    private ProductCardView card1year;
    private ProductCardView card6months;
    private ProductCardView selectedProductCard;
    private ProductPaymentOptionsView paymentOptionsView;
    private Animation scaleUpAnimation;
    private Animation scaleDownAnimation;
    private Animation slideDownAnimation;
    private boolean offerAccepted;

    public BuyActivity() {
        super(R.layout.activity_buy);
    }

    @Override
    public void onBuyAutomaticRenewal() {
        purchaseProduct(R.id.SUBS_PRODUCT_KEY);
    }

    @Override
    public void onBuyOneTime() {
        purchaseProduct(R.id.INAPP_PRODUCT_KEY);
    }

    private void purchaseProduct(int tagId) {
        Product p = (Product) selectedProductCard.getTag(tagId);
        if (p != null) {
            try {
                PlayStore.getInstance().purchase(BuyActivity.this, p);
            } catch (Throwable t) {
                LOGGER.error("Couldn't make product purchase.", t);
            }
        }
    }

    @Override
    protected void initComponents(Bundle savedInstanceState) {
        final boolean interstitialMode = getIntent().hasExtra(INTERSTITIAL_MODE);
        offerAccepted = savedInstanceState != null &&
                savedInstanceState.containsKey(OFFER_ACCEPTED) &&
                savedInstanceState.getBoolean(OFFER_ACCEPTED,false);
        initAnimations();
        initActionBar(interstitialMode);
        initOfferLayer(interstitialMode);
        initProductCards(getLastSelectedCardViewId(savedInstanceState));
        initPaymentOptionsView(getLastPaymentOptionsViewVisibility(savedInstanceState));
    }

    private void initActionBar(boolean interstitialMode) {
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            final String title = getActionBarTitle();
            if (interstitialMode) {
                hideOSTitleBar();
                initInterstitialModeActionBar(actionBar, title);
                actionBar.hide();
            } else {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setIcon(android.R.color.transparent);
                actionBar.setTitle(title);
            }
        }
    }

    private String getActionBarTitle() {
        final String titlePrefix = getString(R.string.remove_ads);
        return titlePrefix + ". " + getRandomPitch(false) + ".";
    }

    private String getRandomPitch(final boolean avoidSupportPitches) {
        final int[] pitches = {R.string.save_bandwidth,
                R.string.support_frostwire,
                R.string.support_free_software,
                R.string.cheaper_than_drinks,
                R.string.cheaper_than_lattes,
                R.string.cheaper_than_parking,
                R.string.keep_the_project_alive};

        int suffixId = pitches[new Random().nextInt(pitches.length)];

        if (avoidSupportPitches) {
          while (suffixId == R.string.support_frostwire || suffixId == R.string.support_free_software) {
              suffixId = pitches[new Random().nextInt(pitches.length)];
          }
        }

        return getString(suffixId);
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
            getActionBar().show();
            return;
        }

        // dismiss handling.
        final InterstitialOfferDismissButtonClickListener dismissOfferClickListener = new InterstitialOfferDismissButtonClickListener();
        ImageButton dismissButton = findView(R.id.activity_buy_interstitial_dismiss_button);
        dismissButton.setClickable(true);
        dismissButton.setOnClickListener(dismissOfferClickListener);

        // going for it handling
        final OfferClickListener offerClickListener = new OfferClickListener();

        final View topClickArea = findView(R.id.activity_buy_interstitial_click_area_1);
        topClickArea.setClickable(true);
        topClickArea.setOnClickListener(offerClickListener);

        final TextView supportFrostWire = findView(R.id.activity_buy_interstitial_support_frostwire);
        supportFrostWire.setText(supportFrostWire.getText().toString().toUpperCase());
        supportFrostWire.setClickable(true);
        supportFrostWire.setOnClickListener(offerClickListener);

        final TextView randomPitch = findView(R.id.activity_buy_interstitial_random_pitch);
        randomPitch.setText(getRandomPitch(true).toUpperCase());
        randomPitch.setClickable(true);
        randomPitch.setOnClickListener(offerClickListener);

        final TextView removeAds = findView(R.id.activity_buy_interstitial_remove_ads);
        //removeAds.setText(removeAds.getText().toString().toUpperCase());
        removeAds.setClickable(true);
        removeAds.setOnClickListener(offerClickListener);

        final ImageButton frostWireLogoButton = findView(R.id.activity_buy_interstitial_frostwire_logo);
        frostWireLogoButton.setClickable(true);
        frostWireLogoButton.setOnClickListener(offerClickListener);

        final TextView adFree = findView(R.id.activity_buy_interstitial_ad_free);
        adFree.setText(adFree.getText().toString().toUpperCase());
        adFree.setClickable(true);
        adFree.setOnClickListener(offerClickListener);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        scrollToSelectedCard();
    }

    @Override
    public void onBackPressed() {
        handleBackButtonPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // this method is for older than API 5
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            handleBackButtonPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void handleBackButtonPressed() {
        Intent intent = getIntent();
        if (intent.hasExtra(INTERSTITIAL_MODE)) {
            onInterstitialActionBarDismiss();
        } else {
            finish();
        }
    }

    private void hideOSTitleBar() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
    }

//    private void showTitleBar() {
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
//        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
//    }

    private void onInterstitialActionBarDismiss() {
        final Intent intent = getIntent();
        if (intent != null && intent.hasExtra(INTERSTITIAL_MODE)) {
            boolean dismissActivityAfterward = intent.getBooleanExtra("dismissActivityAfterward", false);
            boolean shutdownActivityAfterwards = intent.getBooleanExtra("shutdownActivityAfterwards", false);

            if (dismissActivityAfterward) {
                Intent i = new Intent(getApplication(), MainActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                i.putExtra(Constants.EXTRA_FINISH_MAIN_ACTIVITY, true);
                getApplication().startActivity(i);
                return;
            }

            if (shutdownActivityAfterwards) {
                Intent i = new Intent(getApplication(), MainActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                i.putExtra("shutdown-" + ConfigurationManager.instance().getUUIDString(), true);
                getApplication().startActivity(i);
            }

            finish();
        }
    }

    private void initInterstitialModeActionBar(ActionBar bar, String title) {
        // custom view for interstitial mode's action bar.
        bar.setDisplayShowHomeEnabled(false);
        bar.setDisplayShowTitleEnabled(false);
        bar.setDisplayShowCustomEnabled(true);
        final LinearLayout customActionBar = (LinearLayout) getLayoutInflater().inflate(R.layout.view_actionbar_interstitial_buy_activity, null);
        final TextView titleTextView = (TextView) customActionBar.findViewById(R.id.view_actionbar_interstitial_buy_activity_title);
        titleTextView.setText(title);
        final ImageButton closeButton = (ImageButton) customActionBar.findViewById(R.id.view_actionbar_interstitial_buy_activity_dismiss_button);
        closeButton.setClickable(true);
        closeButton.setOnClickListener(new InterstitialActionBarDismissButtonClickListener());

        // so it fills the entire place, otherwise it leaves a bit of space on the right hand side, despite the custom view
        // having no padding or margins. http://stackoverflow.com/questions/27298282/android-actionbars-custom-view-not-filling-parent
        ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        bar.setCustomView(customActionBar, layoutParams);
    }

    private void initAnimations() {
        scaleUpAnimation = AnimationUtils.loadAnimation(this, R.anim.scale_up);
        scaleDownAnimation = AnimationUtils.loadAnimation(this, R.anim.scale_down);
        slideDownAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_down);
    }

    private void initProductCards(int lastSelectedCardViewId) {
        card30days = findView(R.id.activity_buy_product_card_30_days);
        card1year = findView(R.id.activity_buy_product_card_1_year);
        card6months = findView(R.id.activity_buy_product_card_6_months);

        final PlayStore store = PlayStore.getInstance();
        initProductCard(card30days, store, Products.SUBS_DISABLE_ADS_1_MONTH_SKU, Products.INAPP_DISABLE_ADS_1_MONTH_SKU);
        initProductCard(card1year, store, Products.SUBS_DISABLE_ADS_1_YEAR_SKU, Products.INAPP_DISABLE_ADS_1_YEAR_SKU);
        initProductCard(card6months, store, Products.SUBS_DISABLE_ADS_6_MONTHS_SKU, Products.INAPP_DISABLE_ADS_6_MONTHS_SKU);

        View.OnClickListener cardClickListener = createCardClickListener();
        card30days.setOnClickListener(cardClickListener);
        card1year.setOnClickListener(cardClickListener);
        card6months.setOnClickListener(cardClickListener);

        initLastCardSelection(lastSelectedCardViewId);
    }

    private void initLastCardSelection(int lastSelectedCardViewId) {
        switch (lastSelectedCardViewId) {
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
        paymentOptionsView.setBuyButtonsListener(this);
        paymentOptionsView.setVisibility(paymentOptionsVisibility);

        if (paymentOptionsVisibility == View.VISIBLE) {
            showPaymentOptionsBelowSelectedCard();
        }
    }

    private void initProductCard(ProductCardView card, PlayStore store, String subsSKU, String inappSKU) {
        if (card != null && store != null && subsSKU != null && inappSKU != null) {
            card.setTag(R.id.SUBS_PRODUCT_KEY, store.product(subsSKU));
            card.setTag(R.id.INAPP_PRODUCT_KEY, store.product(inappSKU));
            card.updateData(store.product(subsSKU));
        }
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

    private void on30DaysCardTouched() {
        selectedProductCard = card30days;
    }

    private void on1YearCardTouched() {
        selectedProductCard = card1year;
    }

    private void on6MonthsCardTouched() {
        selectedProductCard = card6months;
    }

    private void highlightSelectedCard() {
        if (selectedProductCard == null) {
            return;
        }
        card30days.setSelected(selectedProductCard == card30days);
        card1year.setSelected(selectedProductCard == card1year);
        card6months.setSelected(selectedProductCard == card6months);
    }

    private void scrollToSelectedCard() {
        ScrollView scrollView = (ScrollView) findViewById(R.id.activity_buy_scrollview);
        LinearLayout linearLayout = (LinearLayout) scrollView.getChildAt(0);
        int index = linearLayout.indexOfChild(selectedProductCard);
        int cardHeight = selectedProductCard.getHeight() + selectedProductCard.getPaddingTop();
        scrollView.scrollTo(0, index * cardHeight);
    }

    private void showPaymentOptionsBelowSelectedCard() {
        final ViewGroup scrollView = (ViewGroup) findViewById(R.id.activity_buy_scrollview);
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

                paymentOptionsView.clearAnimation();
                paymentOptionsView.startAnimation(scaleUpAnimation);

                // APOLOGIES FOR THE UGLY HACK BELOW.
                // I tried in numerous ways to use AnimationListeners
                // but they did not work, it seems these APIs are broken.
                // I even tried overriding the onAnimationEnd() method on
                // the view. AnimationListeners will never be invoked
                // not on the Animation object, not on the view, not on the
                // Layout (ViewGroup), this is one of the suggested workaround
                // if you need to do something right after an animation is done.
                // TL;DR; Android's AnimationListeners are broken. -gubatron.
                paymentOptionsView.postDelayed(
                        new Runnable() {
                            @Override
                            public void run() {
                                scaleDownPaymentOptionsView(layout);
                            }
                        },
                        scaleUpAnimation.getDuration() + 100);
            } else {
                // first time shown
                scaleDownPaymentOptionsView(layout);
            }
        }
    }

    private void scaleDownPaymentOptionsView(final ViewGroup layout) {
        paymentOptionsView.clearAnimation();
        layout.removeView(paymentOptionsView);
        int selectedCardIndex = layout.indexOfChild(selectedProductCard);
        paymentOptionsView.setVisibility(View.VISIBLE);
        layout.addView(paymentOptionsView, selectedCardIndex + 1);
        paymentOptionsView.startAnimation(scaleDownAnimation);
    }

    private View.OnClickListener createCardClickListener() {
        return new ProductCardViewOnClickListener();
    }

    private class ProductCardViewOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (v instanceof ProductCardView) {
                int id = v.getId();
                switch (id) {
                    case R.id.activity_buy_product_card_30_days:
                        BuyActivity.this.on30DaysCardTouched();
                        break;
                    case R.id.activity_buy_product_card_1_year:
                        BuyActivity.this.on1YearCardTouched();
                        break;
                    case R.id.activity_buy_product_card_6_months:
                        BuyActivity.this.on6MonthsCardTouched();
                        break;
                    default:
                        BuyActivity.this.on1YearCardTouched();
                        break;
                }
                highlightSelectedCard();
                showPaymentOptionsBelowSelectedCard();
                scrollToSelectedCard();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        PlayStore store = PlayStore.getInstance();
        if (store.handleActivityResult(requestCode, resultCode, data)) {
            store.refresh();
            // user clicked outside of the PlayStore purchase dialog
            if (data != null &&
                    data.hasExtra("RESPONSE_CODE") &&
                    data.getIntExtra("RESPONSE_CODE", 0) == 5) {
                paymentOptionsView.hideProgressBarOnButton(ProductPaymentOptionsView.PurchaseButton.AutomaticRenewal);
                paymentOptionsView.hideProgressBarOnButton(ProductPaymentOptionsView.PurchaseButton.OneTimePurchase);
                return;
            }
            finish();
        }
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
        }
    }

    private class OfferClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            final View offerLayout = findViewById(R.id.activity_buy_interstitial_linear_layout);
            offerAccepted = true;
            offerLayout.clearAnimation();
            offerLayout.startAnimation(slideDownAnimation);
            v.postDelayed(new Runnable() {
                @Override
                public void run() {
                    getActionBar().show();
                    offerLayout.setVisibility(View.GONE);
                    offerLayout.clearAnimation();
                }
            }, slideDownAnimation.getDuration()+50);
        }
    }
}