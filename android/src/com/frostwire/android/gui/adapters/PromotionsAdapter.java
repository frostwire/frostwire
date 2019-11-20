/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.adapters;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.BuyActivity;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractAdapter;
import com.frostwire.android.offers.MoPubAdNetwork;
import com.frostwire.android.offers.MopubBannerView;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.offers.PlayStore;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.frostclick.Slide;
import com.frostwire.util.StringUtils;

import java.util.List;
import java.util.Random;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Adapter in control of the List View shown when we're browsing the files of
 * one peer.
 *
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public class PromotionsAdapter extends AbstractAdapter<Slide> {

    private static final int NO_SPECIAL_OFFER = 97999605;
    private final List<Slide> slides;
    private final PromotionDownloader promotionDownloader;
    private final ImageLoader imageLoader;
    private MopubBannerView mopubBannerView;
    private int specialOfferLayout;
    private static final double PROMO_HEIGHT_TO_WIDTH_RATIO = 0.52998;

    public PromotionsAdapter(Context ctx, List<Slide> slides, PromotionDownloader promotionDownloader) {
        super(ctx, R.layout.view_promotions_item);
        this.slides = slides;
        this.imageLoader = ImageLoader.getInstance(ctx);
        this.promotionDownloader = promotionDownloader;
    }

    @Override
    public void setupView(View convertView, ViewGroup parent, Slide viewItem) {
        ImageView imageView = findView(convertView, R.id.view_promotions_item_image);
        TextView downloadTextView = findView(convertView, R.id.view_promotions_item_download_textview);
        ImageView previewImageView = findView(convertView, R.id.view_promotions_item_preview_imageview);
        ImageView readmoreImageView = findView(convertView, R.id.view_promotions_item_readmore_imageview);

        GridView gridView = (GridView) parent;
        int promoWidth = gridView.getColumnWidth();
        int promoHeight = (int) (promoWidth * PROMO_HEIGHT_TO_WIDTH_RATIO);
        if (promoWidth > 0 && promoHeight > 0 && imageView != null) {
            imageLoader.load(Uri.parse(viewItem.imageSrc), imageView, promoWidth, promoHeight);
        }

        final Slide theSlide = viewItem;
        View.OnClickListener downloadPromoClickListener = view -> startPromotionDownlaod(theSlide);

        View.OnClickListener previewClickListener = view -> startVideoPreview(theSlide.videoURL);

        View.OnClickListener readmoreClickListener = view -> openClickURL(theSlide.clickURL);

        if (imageView != null) {
            imageView.setOnClickListener(downloadPromoClickListener);
        }

        if (downloadTextView != null) {
            downloadTextView.setOnClickListener(downloadPromoClickListener);
        }

        if (StringUtils.isNullOrEmpty(theSlide.videoURL)) {
            previewImageView.setVisibility(View.GONE);
        } else {
            previewImageView.setOnClickListener(previewClickListener);
            previewImageView.setVisibility(View.VISIBLE);
        }

        if (StringUtils.isNullOrEmpty(theSlide.clickURL)) {
            readmoreImageView.setVisibility(View.GONE);
        } else {
            readmoreImageView.setOnClickListener(readmoreClickListener);
            readmoreImageView.setVisibility(View.VISIBLE);
        }
    }

    private void openClickURL(String clickURL) {
        UIUtils.openURL(getContext(), clickURL);
    }

    private void startVideoPreview(String videoURL) {
        // frostwire-preview is not a mobile friendly experience, let's take them straight to youtube
        if (videoURL.startsWith(Constants.FROSTWIRE_PREVIEW_DOT_COM_URL)) {
            videoURL = videoURL.substring(videoURL.indexOf("detailsUrl=") + "detailsUrl=".length());
        }

        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(videoURL));
        ((MainActivity) getContext()).startActivityForResult(i, MainActivity.PROMO_VIDEO_PREVIEW_RESULT_CODE);
    }

    private void startPromotionDownlaod(Slide theSlide) {
        promotionDownloader.startPromotionDownload(theSlide);
        Offers.showInterstitialOfferIfNecessary((MainActivity) getContext(), Offers.PLACEMENT_INTERSTITIAL_MAIN, false, false);
    }

    @Override
    public int getCount() {
        boolean inLandscapeMode = Configuration.ORIENTATION_LANDSCAPE == getContext().getResources().getConfiguration().orientation;
        int addSpecialOffer = (Constants.IS_GOOGLE_PLAY_DISTRIBUTION) ? 1 : 0;
        int addFrostWireFeaturesTitle = !inLandscapeMode ? 1 : 0;
        int slideCount = (slides != null) ? slides.size() : 0;
        if (inLandscapeMode && slideCount % 2 == 0) {
            slideCount--;
        }
        int addAllFeaturesButtonAtTheEnd = 1;
        return addSpecialOffer + addFrostWireFeaturesTitle + slideCount + addAllFeaturesButtonAtTheEnd;
    }

    @Override
    public Slide getItem(int position) {
        return slides.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Plus (or when on landscape orientation) needs no special offer as we can't sell remove ads yet
        boolean inLandscapeMode = Configuration.ORIENTATION_LANDSCAPE == getContext().getResources().getConfiguration().orientation;
        // "ALL FREE DOWNLOADS" button shown last
        if (position == getCount() - 1) {
            if (!Constants.IS_GOOGLE_PLAY_DISTRIBUTION || Constants.IS_BASIC_AND_DEBUG) {
                return View.inflate(getContext(), R.layout.view_frostwire_features_all_downloads, null);
            } else {
                return View.inflate(getContext(), R.layout.view_invisible_promo, null);
            }
        }
        return (!inLandscapeMode) ? getPortraitView(position, convertView, parent) :
                getLandscapeView(position, convertView, parent);
    }

    private View getPortraitView(int position, View convertView, ViewGroup parent) {
        // "FROSTWIRE FEATURES" view logic.
        int offsetFeaturesTitleHeader = 0;
        // OPTIONAL OFFER ON TOP
        if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION || PlayStore.available()) {
            offsetFeaturesTitleHeader++; // has to move down one slot since we'll have special offer above

            // if you paid for ads we show no special layout (NO_SPECIAL_OFFER)
            int specialOfferLayout = pickSpecialOfferLayout();

            // Show special offer or banner, Google play logic included in pickSpecialOfferLayout()
            if (position == 0 && specialOfferLayout == NO_SPECIAL_OFFER) {
                return View.inflate(getContext(), R.layout.view_invisible_promo, null);
            } else if (position == 0) {
                // mopubBannerView does not work well in Android 4.4 (KitKat)
                // java.lang.ClassCastException:
                //  at android.widget.GridView.onMeasure (GridView.java:1046)
                int r = 1 + new Random(System.currentTimeMillis()).nextInt(100);
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT || r % 2 == 0) {
                    if (specialOfferLayout == R.layout.view_remove_ads_notification) {
                        View removeAdsOfferView = setupRemoveAdsOfferView();
                        if (removeAdsOfferView != null) {
                            return removeAdsOfferView;
                        }
                    }
                } else {
                    return getMopubBannerView();
                }
                //this line below should be impossible, but we could have some other special offer layout
                //return View.inflate(getContext(), specialOfferLayout, null);
            } else if (position > 1) { // everything after the "FROSTWIRE FEATURES" title view.
                return super.getView(position - 2, null, parent);
            }
        }
        // "FROSTWIRE FEATURES" title view
        if (position == offsetFeaturesTitleHeader) {
            if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION && !Constants.IS_BASIC_AND_DEBUG) {
                return View.inflate(getContext(), R.layout.view_invisible_promo, null);
            } else {
                return View.inflate(getContext(), R.layout.view_frostwire_features_title, null);
            }
        }
        return super.getView(position - 1, null, parent);
    }

    private View getMopubBannerView() {
        if (mopubBannerView == null) {
            mopubBannerView = new MopubBannerView(getContext(), null, true, false);
            mopubBannerView.setOnBannerLoadedListener(() -> mopubBannerView.setShowDismissButton(false));
            // will ANR after MoPub 5.4.0, tried putting it on a background thread, but then when the ad
            // is destroyed it triggers a android.view.ViewRootImpl$CalledFromWrongThreadException
            // temp fix placed in MoPubAdNetwork.java#126
           mopubBannerView.loadMoPubBanner(MoPubAdNetwork.UNIT_ID_HOME);
        }
        return mopubBannerView;
    }

    private View getLandscapeView(int position, View convertView, ViewGroup parent) {
        try {
            return super.getView(position, null, parent);
        } catch (Throwable t) {
            return convertView;
        }
    }

    @Nullable
    private View setupRemoveAdsOfferView() {
        String pitch = getContext().getString(UIUtils.randomPitchResId(true));
        if (pitch != null) {
            View specialOfferView = View.inflate(getContext(), R.layout.view_remove_ads_notification, null);
            TextView pitchTitle = specialOfferView.findViewById(R.id.view_remove_ads_notification_title);
            if (pitchTitle != null) {
                pitchTitle.setText(pitch);
                return specialOfferView;
            }
        }
        return null;
    }

    /**
     * Decide what the special offer layout we should use.
     */
    private int pickSpecialOfferLayout() {
        // Optimistic: If we're plus, we can't offer ad removal yet.
        specialOfferLayout = NO_SPECIAL_OFFER;

        if (Offers.removeAdsOffersEnabled()) {
            specialOfferLayout = R.layout.view_remove_ads_notification;
        }

        return specialOfferLayout;
    }

    public void onAllFeaturedDownloadsClick(String from) {
        UIUtils.openURL(getContext(), Constants.ALL_FEATURED_DOWNLOADS_URL + "?from=" + from);
    }

    public void onSpecialOfferClick() {
        if (specialOfferLayout == R.layout.view_remove_ads_notification) {
            // take to buy remove ads screen
            MainActivity mainActivity = (MainActivity) getContext();
            Intent i = new Intent(getContext(), BuyActivity.class);
            mainActivity.startActivityForResult(i, BuyActivity.PURCHASE_SUCCESSFUL_RESULT_CODE);
        }
    }

    public void onDestroyView() {
        if (mopubBannerView != null) {
            mopubBannerView.destroy();
        }
    }
}
