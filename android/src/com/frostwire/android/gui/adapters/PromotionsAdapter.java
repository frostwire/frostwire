/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa)
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
import com.frostwire.util.StringUtils;

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
    //private static final Logger LOG = Logger.getLogger(PromotionsAdapter.class);
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
        int addSpecialOffer = 1;
        int banner = 1;
        int slideCount = (slides != null) ? slides.size() : 0;
        int addAllFeaturesButtonAtTheEnd = 1;
        if (Offers.disabledAds()) {
            addSpecialOffer = 0;
            banner = 0;
        }
        return addSpecialOffer + banner + slideCount + addAllFeaturesButtonAtTheEnd;
    }

    @Override
    public Slide getItem(int position) {
        return slides.get(position);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        // [0] FIRST POSITION: A special offer/ad or an invisible view if we're not showing ads
        // if you paid for ads we show no special layout (NO_SPECIAL_OFFER)
        int specialOfferLayout = pickSpecialOfferLayout();
        boolean adsAreOn = !Offers.disabledAds();
        if (position == 0 && specialOfferLayout == NO_SPECIAL_OFFER && !adsAreOn) {
            return View.inflate(getContext(), R.layout.view_invisible_promo, null);
        } else if (position == 0 && adsAreOn) {
            return removeAdsOfferView();
        }

        // [1] SECOND POSITION: The banner ad or an invisible view if we're not showing ads
        if (position == 1 && adsAreOn) {
            return getFwBannerView();
        } else if (position == 1) {
            return View.inflate(getContext(), R.layout.view_invisible_promo, null);
        }

        // [2..N-1] SLIDES
        if (position > 1 && position < getCount() - 1) {
            return super.getView(position - 2, null, parent);
        }

        // [N] Last position: "ALL FREE DOWNLOADS" Button that takes user to the FrostWire Features page
        if (position == getCount() - 1) {
            return View.inflate(getContext(), R.layout.view_frostwire_features_all_downloads, null);
        }
        return View.inflate(getContext(), R.layout.view_invisible_promo, null);
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

    private View removeAdsOfferView() {
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
        if (Offers.removeAdsOffersEnabled()) {
            specialOfferLayout = R.layout.view_remove_ads_notification;
            return R.layout.view_remove_ads_notification;
        }
        specialOfferLayout = NO_SPECIAL_OFFER;
        return NO_SPECIAL_OFFER;
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
