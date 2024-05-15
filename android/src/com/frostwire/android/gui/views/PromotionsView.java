/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter
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

package com.frostwire.android.gui.views;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.adapters.PromotionDownloader;
import com.frostwire.android.gui.adapters.PromotionsAdapter;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.offers.PlayStore;
import com.frostwire.frostclick.Slide;

import java.util.Iterator;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public class PromotionsView extends LinearLayout {
    private GridView gridview;
    private List<Slide> slides;
    private PromotionDownloader promotionDownloader;

    public PromotionsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public List<Slide> getSlides() {
        return slides;
    }

    public void setSlides(List<Slide> slides) {
        if (gridview != null && slides != null) {
            this.slides = slides;
            updateAdapter();
        }
    }

    public void updateAdapter() {
        if (getSlides() != null) {
            List<Slide> slides = getSlides();
            if (Offers.disabledAds()) {
                // remove all ad slides flagged as advertisement
                removeAds(slides);
            }
            destroyPromotionsBanner();
            gridview.setAdapter(new PromotionsAdapter(gridview.getContext(), slides, promotionDownloader));
            gridview.invalidate();
        }
    }

    private void removeAds(List<Slide> slides) {
        slides.removeIf(next -> (next.flags & Slide.IS_ADVERTISEMENT) == Slide.IS_ADVERTISEMENT);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        View.inflate(getContext(), R.layout.view_promotions, this);

        if (isInEditMode()) {
            return;
        }

        gridview = findViewById(R.id.view_promotions_gridview);

        gridview.setOnItemClickListener((parent, v, position, id) -> {
            if (gridview == null || gridview.getAdapter() == null) {
                return;
            }
            PromotionsAdapter promoAdapter = (PromotionsAdapter) gridview.getAdapter();
            if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION && position == 0) {
                promoAdapter.onSpecialOfferClick();
                return;
            }

            // FROSTWIRE FEATURES VIEW
            if (position == (Constants.IS_GOOGLE_PLAY_DISTRIBUTION ? 1 : 0)) {
                promoAdapter.onAllFeaturedDownloadsClick("topHeader");
                return;
            }

            if (position ==  promoAdapter.getCount()-1) {
                promoAdapter.onAllFeaturedDownloadsClick("allFreeDownloadsButton");
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // aldenml: The need of this method is because don't have the best
        // use of saved states for fragments starting from the top activity.
        // When the activity configuration changes (for example, orientation)
        // the GridView is kept in memory, then the need of this forced unbind.
        //
        // Additionally, I'm recycling the picasso drawables for older devices. 
        unbindPromotionDrawables();

        destroyPromotionsBanner();
    }

    public void destroyPromotionsBanner() {
        if (gridview == null) {
            return;
        }
        PromotionsAdapter promotionsAdapter = (PromotionsAdapter) gridview.getAdapter();
        if (promotionsAdapter != null) {
            promotionsAdapter.onDestroyView();
        }
    }

    private void unbindPromotionDrawables() {
        for (int i = 0; gridview != null && i < gridview.getChildCount(); i++) {
            if (gridview.getChildAt(i) instanceof ImageView) {
                unbindPromotionDrawable((ImageView) gridview.getChildAt(i));
            }
        }
    }

    private void unbindPromotionDrawable(ImageView view) {
        if (view.getDrawable() != null) {
            Drawable d = view.getDrawable();
            d.setCallback(null);
            view.setImageDrawable(null);
        }
    }

    public void setPromotionDownloader(PromotionDownloader promotionDownloader) {
        this.promotionDownloader = promotionDownloader;
    }
}
