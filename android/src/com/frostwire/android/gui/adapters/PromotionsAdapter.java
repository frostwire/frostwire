/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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
import android.net.Uri;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ImageView;

import com.andrew.apollo.ui.activities.AudioPlayerActivity;
import com.frostwire.android.R;
import com.frostwire.android.gui.activities.BuyActivity;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.views.AbstractAdapter;
import com.frostwire.android.offers.PlayStore;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.frostclick.Slide;

import java.util.List;

/**
 * Adapter in control of the List View shown when we're browsing the files of
 * one peer.
 *
 * @author gubatron
 * @author aldenml
 */
public class PromotionsAdapter extends AbstractAdapter<Slide> {
    private final List<Slide> slides;
    private final ImageLoader imageLoader;
    private int specialOfferLayout;
    private int specialOfferId;
    private int featuresTitleId;
    private static final double PROMO_HEIGHT_TO_WIDTH_RATIO = 0.52998;

    public PromotionsAdapter(Context ctx, List<Slide> slides) {
        super(ctx, R.layout.view_promotions_item);
        this.slides = slides;
        this.imageLoader = ImageLoader.getInstance(ctx);
    }

    @Override
    public void setupView(View convertView, ViewGroup parent, Slide viewItem) {
        ImageView imageView = findView(convertView, R.id.view_promotions_item_image);

        GridView gridView = (GridView) parent;
        int promoWidth = gridView.getColumnWidth();
        int promoHeight = (int) (promoWidth * PROMO_HEIGHT_TO_WIDTH_RATIO);

        if (promoWidth > 0 && promoHeight > 0 && imageView != null) {
            imageLoader.load(Uri.parse(viewItem.imageSrc), imageView, promoWidth, promoHeight);
        }
    }

    @Override
    public int getCount() {
        return slides.size();
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
        if (position == 0) {
            specialOfferLayout = Math.random() % 2 == 0 ? R.layout.view_remove_ads_notification : R.layout.view_less_results_notification;
            convertView = View.inflate(getContext(), specialOfferLayout, null);
            specialOfferId = convertView.getId();
        } else if (position == 1) {
            convertView = View.inflate(getContext(), R.layout.view_frostwire_features_title, null);
            featuresTitleId = convertView.getId();
        }
        else {
            convertView = super.getView(position-2, null, parent);
        }
        return convertView;
    }

    public void onSpecialOfferClick() {
        if (specialOfferLayout == R.layout.view_remove_ads_notification) {
            // take to buy remove ads screen
            PlayStore.getInstance().endAsync();
            MainActivity mainActivity = (MainActivity) getContext();
            Intent i = new Intent(getContext(), BuyActivity.class);
            mainActivity.startActivityForResult(i, BuyActivity.PURCHASE_SUCCESSFUL_RESULT_CODE);
        } else if (specialOfferLayout == R.layout.view_less_results_notification) {
            Intent i = new Intent("android.intent.action.VIEW", Uri.parse("http://support.frostwire.com/hc/en-us/articles/204095909-How-to-fix-FrostWire-for-Android-not-showing-YouTube-search-results-"));
            try {
                getContext().startActivity(i);
            } catch (Throwable t) {
                // some devices incredibly may have no apps to handle this intent.
            }
        }
    }
}
