/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2024, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.BuyActivity;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractAdapter;
import com.frostwire.android.offers.FWBannerView;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.frostclick.Slide;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * Adapter in control of the List View shown when we're browsing the files of
 * one peer.
 *
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public class PromotionsAdapter extends AbstractAdapter<Slide> {
    private static final Logger LOG = Logger.getLogger(PromotionsAdapter.class);
    private static final int NO_SPECIAL_OFFER = 97999605;
    private final List<Slide> slides;
    private final PromotionDownloader promotionDownloader;
    private final ImageLoader imageLoader;
    private FWBannerView fwBannerView;
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
        if (viewItem == null) {
            return;
        }
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
        View.OnClickListener downloadPromoClickListener = view -> startPromotionDownload(theSlide);

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

    private void startPromotionDownload(Slide theSlide) {
        promotionDownloader.startPromotionDownload(theSlide);
    }

    @Override
    public int getCount() {
        int addSpecialOffer = (Constants.IS_GOOGLE_PLAY_DISTRIBUTION) ? 1 : 0;
        int addFrostWireFeaturesTitle = 1;
        int slideCount = (slides != null) ? slides.size() : 0;
        int addAllFeaturesButtonAtTheEnd = 1;
        return addSpecialOffer + addFrostWireFeaturesTitle + slideCount + addAllFeaturesButtonAtTheEnd;
    }

    @Override
    public Slide getItem(int position) {
        int correction_offset;
        // basic
        if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
            correction_offset = -2; // works great.
        } else {
            correction_offset = -2;
        }
        return slides.get(position + correction_offset);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        // "ALL FREE DOWNLOADS" button shown last
        if (position == getCount() - 1) {
            if (!Constants.IS_GOOGLE_PLAY_DISTRIBUTION || Constants.IS_BASIC_AND_DEBUG) {
                return View.inflate(getContext(), R.layout.view_frostwire_features_all_downloads, null);
            } else {
                return View.inflate(getContext(), R.layout.view_invisible_promo, null);
            }
        }

        // if you paid for ads we show no special layout (NO_SPECIAL_OFFER)
        int specialOfferLayout = pickSpecialOfferLayout();
        boolean adsAreOn = !Offers.disabledAds();//specialOfferLayout == R.layout.view_remove_ads_notification;

        // Show special offer or banner, Google play logic included in pickSpecialOfferLayout()
        if (position == 0 && specialOfferLayout == NO_SPECIAL_OFFER) {
            return View.inflate(getContext(), R.layout.view_invisible_promo, null);
        } else if (position == 0 && adsAreOn && Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
            return Objects.requireNonNull(setupRemoveAdsOfferView());
        } else if (position == 1 && adsAreOn) {
            return getFwBannerView();
        } else if (position > 1) { // everything after the "FROSTWIRE FEATURES" title view.
            return super.getView(position, null, parent);
        }
        return null;
    }

    private FWBannerView getFwBannerView() {
        if (fwBannerView == null) {
            fwBannerView = new FWBannerView(
                    getContext(),
                    null,
                    true,
                    false,
                    false,
                    FWBannerView.UNIT_ID_HOME);
            fwBannerView.setOnBannerLoadedListener(() -> {
                        fwBannerView.setShowDismissButton(false);
                        fwBannerView.setLayersVisibility(FWBannerView.Layers.APPLOVIN, true);
                    }
            );
            fwBannerView.loadFallbackBanner(FWBannerView.UNIT_ID_HOME);
            fwBannerView.setLayersVisibility(FWBannerView.Layers.FALLBACK, true);
            fwBannerView.loadMaxBanner();
        }
        return fwBannerView;
    }

    private View setupRemoveAdsOfferView() {
        String pitch = getContext().getString(UIUtils.randomPitchResId(true));
        View specialOfferView = View.inflate(getContext(), R.layout.view_remove_ads_notification, null);
        TextView pitchTitle = specialOfferView.findViewById(R.id.view_remove_ads_notification_title);
        if (pitchTitle != null) {
            pitchTitle.setText(pitch);
        }
        return specialOfferView;
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
        if (fwBannerView != null) {
            fwBannerView.destroy();
        }
    }
}
