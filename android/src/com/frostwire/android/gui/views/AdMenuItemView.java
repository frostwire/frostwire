/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.frostwire.android.R;

import java.util.Random;


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
        TextView textHeadline = findViewById(R.id.view_ad_menu_item_headline);
        TextView textSubtitle = findViewById(R.id.view_ad_menu_item_subtitle);
        TextView textThumbnail = findViewById(R.id.view_ad_menu_item_thumbnail);
        ImageView imageThumbnail = findViewById(R.id.view_ad_menu_item_thumbnail_image);

        textHeadline.setText(R.string.support_frostwire);

        Random myRand = new Random();
        boolean isEven = (myRand.nextInt() % 2) == 0;

        if (isEven) {
            textSubtitle.setText(R.string.save_bandwidth);
            textThumbnail.setVisibility(VISIBLE);
            textThumbnail.setText(R.string.ad_free);
        } else {
            textSubtitle.setText(R.string.remove_ads);
            imageThumbnail.setVisibility(VISIBLE);
            imageThumbnail.setImageResource(R.drawable.ad_menu_speaker);
        }
    }
}

