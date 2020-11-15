/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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
        final Iterator<Slide> iterator = slides.iterator();
        while (iterator.hasNext()) {
            final Slide next = iterator.next();
            if ((next.flags & Slide.IS_ADVERTISEMENT) == Slide.IS_ADVERTISEMENT) {
                iterator.remove();
            }
        }
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
            if ((Constants.IS_GOOGLE_PLAY_DISTRIBUTION || PlayStore.available()) && position == 0) {
                promoAdapter.onSpecialOfferClick();
                return;
            }

            boolean inLandscapeMode = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

            // FROSTWIRE FEATURES VIEW
            if (!inLandscapeMode && ((!Constants.IS_GOOGLE_PLAY_DISTRIBUTION && position == 0) ||
                (Constants.IS_GOOGLE_PLAY_DISTRIBUTION && position == 1))) {
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
