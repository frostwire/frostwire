/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.adapters;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
import com.frostwire.android.offers.Offers;
import com.frostwire.android.offers.PlayStore;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.frostclick.Slide;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;

import java.util.Calendar;
import java.util.List;

/**
 * Adapter in control of the List View shown when we're browsing the files of
 * one peer.
 *
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public class PromotionsAdapter extends AbstractAdapter<Slide> {
    private static Logger LOG = Logger.getLogger(PromotionsAdapter.class);
    private static final int NO_SPECIAL_OFFER = 97999605;
    private final List<Slide> slides;
    private final PromotionDownloader promotionDownloader;
    private final ImageLoader imageLoader;
    private int specialOfferLayout;
    private static final double PROMO_HEIGHT_TO_WIDTH_RATIO = 0.52998;

    public PromotionsAdapter(Context ctx, List<Slide> slides, PromotionDownloader promotionDownloader) {
        super(ctx, R.layout.view_promotions_item);
        this.slides = slides;
        final boolean landscapeMode = Configuration.ORIENTATION_LANDSCAPE == getContext().getResources().getConfiguration().orientation;
        this.imageLoader = ImageLoader.getInstance(ctx);
        this.promotionDownloader = promotionDownloader;
    }

    @Override
    public void setupView(View convertView, ViewGroup parent, Slide viewItem) {
        ImageView imageView = findView(convertView, R.id.view_promotions_item_image);
        TextView downloadTextView = findView(convertView, R.id.view_promotions_item_download_textview);
        ImageView previewImageView = findView(convertView, R.id.view_promotions_item_preview_imageview);
        ImageView readmoreImageView =  findView(convertView, R.id.view_promotions_item_readmore_imageview);

        GridView gridView = (GridView) parent;
        int promoWidth = gridView.getColumnWidth();
        int promoHeight = (int) (promoWidth * PROMO_HEIGHT_TO_WIDTH_RATIO);
        if (promoWidth > 0 && promoHeight > 0 && imageView != null) {
            imageLoader.load(Uri.parse(viewItem.imageSrc), imageView, promoWidth, promoHeight);
        }

        final Slide theSlide = viewItem;
        View.OnClickListener downloadPromoClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startPromotionDownlaod(theSlide);
            }
        };

        View.OnClickListener previewClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startVideoPreview(theSlide.videoURL);
            }
        };

        View.OnClickListener readmoreClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openClickURL(theSlide.clickURL);
            }
        };

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
            videoURL = videoURL.substring(videoURL.indexOf("detailsUrl=")+"detailsUrl=".length());
        }

        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(videoURL));
        ((MainActivity) getContext()).startActivityForResult(i, MainActivity.PROMO_VIDEO_PREVIEW_RESULT_CODE);
    }

    private void startPromotionDownlaod(Slide theSlide) {
        promotionDownloader.startPromotionDownload(theSlide);
        Offers.showInterstitialOfferIfNecessary((MainActivity) getContext(), Offers.PLACEMENT_INTERSTITIAL_EXIT, false, false);
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
            return View.inflate(getContext(), R.layout.view_frostwire_features_all_downloads, null);
        }
        return (!inLandscapeMode) ? getPortraitView(position, convertView, parent) :
                getLandscapeView(position, convertView, parent);
    }

    private View getPortraitView(int position, View convertView, ViewGroup parent) {
        // "FROSTWIRE FEATURES" view logic.
        int offsetFeaturesTitleHeader = 0;
        // OPTIONAL OFFER ON TOP
        if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
            offsetFeaturesTitleHeader++; // has to move down one slot since we'll have special offer above

            // if you paid for ads, I tell you to go plus
            int specialOfferLayout = pickSpecialOfferLayout();

            if (position == 0 && specialOfferLayout != NO_SPECIAL_OFFER) {
                if (specialOfferLayout == R.layout.view_remove_ads_notification) {
                    View removeAdsOfferView = setupRemoveAdsOfferView();
                    if (removeAdsOfferView != null) {
                        return removeAdsOfferView;
                    }
                }
                return View.inflate(getContext(), specialOfferLayout, null);
            } else if (position > 1) { // everything after the "FROSTWIRE FEATURES" title view.
                return super.getView(position - 2, null, parent);
            }
        }
        // "FROSTWIRE FEATURES" title view
        if (position == offsetFeaturesTitleHeader) {
            return View.inflate(getContext(), R.layout.view_frostwire_features_title, null);
        }
        return super.getView(position - 1, null, parent);
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
            TextView pitchTitle = (TextView) specialOfferView.findViewById(R.id.view_remove_ads_notification_title);
            if (pitchTitle != null && specialOfferView != null) {
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

        // If we're basic and we have not paid to remove ads, we pick the specialOfferLayout randomly.
        if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
            // If we're basic and we paid... you should still know about plus :)
            specialOfferLayout = R.layout.view_less_results_notification;

            if (Offers.removeAdsOffersEnabled()) {
                //LOG.info("pickSpecialOfferLayout: removeAdsOffersEnabled.");
                // offer to remove ads, or offer to upgrade to plus for more results
                specialOfferLayout = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) % 2 == 0 ?
                        R.layout.view_remove_ads_notification :
                        R.layout.view_less_results_notification;
            } else {
                LOG.info("pickSpecialOfferLayout: removeAdsOffersEnabled NOT enabled!");
            }

        }
        return specialOfferLayout;
    }

    public void onAllFeaturedDownloadsClick(String from) {
        UIUtils.openURL(getContext(), Constants.ALL_FEATURED_DOWNLOADS_URL + "?from=" + from);
    }

    public void onSpecialOfferClick() {
        if (specialOfferLayout == R.layout.view_remove_ads_notification) {
            // take to buy remove ads screen
            PlayStore.getInstance().endAsync();
            MainActivity mainActivity = (MainActivity) getContext();
            Intent i = new Intent(getContext(), BuyActivity.class);
            mainActivity.startActivityForResult(i, BuyActivity.PURCHASE_SUCCESSFUL_RESULT_CODE);
        } else if (specialOfferLayout == R.layout.view_less_results_notification) {
            Intent i = new Intent("android.intent.action.VIEW", Uri.parse(Constants.HOW_TO_GET_MORE_SEARCH_RESULTS_URL));
            try {
                getContext().startActivity(i);
            } catch (Throwable t) {
                // some devices incredibly may have no apps to handle this intent.
            }
        }
    }
}
