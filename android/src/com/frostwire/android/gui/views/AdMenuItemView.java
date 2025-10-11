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

package com.frostwire.android.gui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.offers.SupportOffer;


/**
 * @author gubatron
 * @author aldenml
 *
 */
public class AdMenuItemView extends RelativeLayout {

    public AdMenuItemView(Context context, AttributeSet set) {
        super(context, set);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        View.inflate(getContext(), R.layout.view_ad_menuitem, this);
    }

    public void bind(SupportOffer offer) {
        TextView textHeadline = findViewById(R.id.view_ad_menu_item_headline);
        TextView textSubtitle = findViewById(R.id.view_ad_menu_item_subtitle);
        TextView textBadge = findViewById(R.id.view_ad_menu_item_thumbnail);
        ImageView imageThumbnail = findViewById(R.id.view_ad_menu_item_thumbnail_image);

        textHeadline.setText(offer.titleRes);
        textSubtitle.setText(offer.messageRes);

        if (offer.badgeTextRes != 0) {
            textBadge.setText(offer.badgeTextRes);
            textBadge.setVisibility(VISIBLE);
            imageThumbnail.setVisibility(GONE);
        } else if (offer.iconRes != 0) {
            imageThumbnail.setImageResource(offer.iconRes);
            imageThumbnail.setVisibility(VISIBLE);
            textBadge.setVisibility(GONE);
        } else {
            textBadge.setVisibility(GONE);
            imageThumbnail.setVisibility(GONE);
        }
    }
}

