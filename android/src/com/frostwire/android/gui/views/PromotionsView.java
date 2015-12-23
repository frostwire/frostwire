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
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.frostwire.android.R;
import com.frostwire.android.gui.adapters.PromotionsAdapter;
import com.frostwire.frostclick.Slide;

import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public class PromotionsView extends LinearLayout {
    //private Logger LOG = Logger.getLogger(PromotionsView.class);

    private GridView gridview;

    private List<Slide> slides;

    private OnPromotionClickListener onPromotionClickListener;

    public PromotionsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setOnPromotionClickListener(OnPromotionClickListener listener) {
        this.onPromotionClickListener = listener;
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
            gridview.setAdapter(new PromotionsAdapter(gridview.getContext(), getSlides()));
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        View.inflate(getContext(), R.layout.view_promotions, this);

        if (isInEditMode()) {
            return;
        }

        gridview = (GridView) findViewById(R.id.view_promotions_gridview);

        gridview.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                Slide slide = (Slide) gridview.getAdapter().getItem(position);
                if (onPromotionClickListener != null && slide != null) {
                    onPromotionClickListener.onPromotionClick(PromotionsView.this, slide);
                }
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // aldenml: The need of this method is because don't have the best
        // use of saved states for fragments starting from the top activity.
        // When the activity configuration changes (for example, orientation)
        // the gridview is kept in memory, then the need of this forced unbind.
        //
        // Additionally, I'm recycling the picasso drawables for older devices. 
        unbindPromotionDrawables();
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
            //UIUtils.picassoRecycle(d);
        }
    }

    public interface OnPromotionClickListener {
        void onPromotionClick(PromotionsView v, Slide slide);
    }
}