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

package com.frostwire.android.gui.adapters;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ImageView;
import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractAdapter;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.frostclick.Slide;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Adapter in control of the List View shown when we're browsing the files of
 * one peer.
 * 
 * @author gubatron
 * @author aldenml
 * 
 */
public class PromotionsAdapter extends AbstractAdapter<Slide> {

    private final List<Slide> slides;
    private final ImageLoader imageLoader;
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
        int promoWidth = getColumnWidth(gridView); //hack
        int promoHeight = (int) (promoWidth * PROMO_HEIGHT_TO_WIDTH_RATIO);

        if (promoWidth > 0 && promoHeight > 0) {
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
    
    /**
     * This is a hack.
     * The reason is that, the very first time we try to find out what are the dimensions
     * of the FWGridView component in the PromotionsAdapter.getView() method, it always
     * returns 0. The idea was to use the width of the component, and the orientation of the
     * device, and then we'd know if we're in a single column mode or 2 column mode when displaying
     * the promos. This however works every time, but I'm not sure if it'll break after Android API 16 (Jelly Bean)
     * since Android later introduced it's own getColumnWidth() method.
     * @return
     */
    private int getColumnWidth(GridView grid) {
        try {
            Field field = GridView.class.getDeclaredField("mColumnWidth");
            field.setAccessible(true);
            Integer value = (Integer) field.get(grid);
            field.setAccessible(false);
            return value.intValue();
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
