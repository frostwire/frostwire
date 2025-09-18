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

package com.frostwire.android.offers;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.BuyActivity;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.ImageLoader;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 * Created on 4/12/17.
 */
public final class InHouseBannerFactory {
    private static final Map<Message, Integer[]> BIG_300x250_DRAWABLES = new HashMap<>();
    private static final Map<Message, Integer[]> SMALL_320x50_DRAWABLES = new HashMap<>();
    private static final Map<Message, View.OnClickListener> CLICK_LISTENERS = new HashMap<>();

    static {
        initSmallDrawables();
        initBigDrawables();
        initClickListeners();
    }

    public enum AdFormat {
        BIG_300x250,
        SMALL_320x50
    }

    public static void loadAd(final ImageView placeholder, AdFormat adFormat) {
        Message randomMessage = Message.random();
        int randomDrawable = getRandomDrawable(adFormat, randomMessage);
        Context context = placeholder.getContext();
        ImageLoader.getInstance(context).load(randomDrawable, placeholder);
        placeholder.setOnClickListener(CLICK_LISTENERS.get(randomMessage));
    }

    private static int getRandomDrawable(AdFormat adFormat, Message message) {
        Map<Message, Integer[]> drawablesMap = adFormat == AdFormat.BIG_300x250 ?
                BIG_300x250_DRAWABLES :
                SMALL_320x50_DRAWABLES;
        Integer[] drawableIds = drawablesMap.get(message);
        return drawableIds[drawableIds.length == 1 ? 0 : new Random().nextInt(drawableIds.length)];
    }

    private static void initSmallDrawables() {
        SMALL_320x50_DRAWABLES.put(Message.AD_REMOVAL,
                new Integer[]{
                        R.drawable._320x50_ad_free_1,
                        R.drawable._320x50_ad_free_2,
                        R.drawable._320x50_ad_free_3
                });
        if (!Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
            SMALL_320x50_DRAWABLES.put(Message.DONATE,
                    new Integer[]{
                            R.drawable._320x50_donate_1
                    });
        }
        SMALL_320x50_DRAWABLES.put(Message.FROSTCLICK,
                new Integer[]{
                        R.drawable._320x50_frostclick_1,
                        R.drawable._320x50_frostclick_2
                });
    }

    private static void initBigDrawables() {
        BIG_300x250_DRAWABLES.put(Message.AD_REMOVAL,
                new Integer[]{
                        R.drawable._300x250_ad_free_1,
                        R.drawable._300x250_ad_free_2
                });
        if (!Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
            BIG_300x250_DRAWABLES.put(Message.DONATE,
                    new Integer[]{
                            R.drawable._300x250_donate_1,
                            R.drawable._300x250_donate_2
                    });
        }
        BIG_300x250_DRAWABLES.put(Message.FROSTCLICK,
                new Integer[]{
                        R.drawable._300x250_frostclick_1,
                        R.drawable._300x250_frostclick_2
                });
    }

    private static void initClickListeners() {
        CLICK_LISTENERS.put(Message.AD_REMOVAL, v -> {
            Intent intent = new Intent(v.getContext(), BuyActivity.class);
            intent.putExtra("shutdownActivityAfterwards", false);
            intent.putExtra("dismissActivityAfterward", false);
            v.getContext().startActivity(intent);
        });
        if (!Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
            CLICK_LISTENERS.put(Message.DONATE, new URLOpenerClickListener("https://www.frostwire.com/give?from=android-fallback-ad"));
        }
        CLICK_LISTENERS.put(Message.FROSTCLICK, new URLOpenerClickListener("https://www.frostclick.com/?from=android-fallback-ad"));
    }

    enum Message {
        AD_REMOVAL,
        FROSTCLICK,
        DONATE;

        static Message random() {
            if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
                Message[] basic_messages = new Message[]{AD_REMOVAL, FROSTCLICK};
                return basic_messages[new Random().nextInt(basic_messages.length)];
            }
            Message[] all_messages = new Message[]{AD_REMOVAL, FROSTCLICK, DONATE};
            return all_messages[new Random().nextInt(all_messages.length)];
        }
    }

    private static class URLOpenerClickListener implements View.OnClickListener {
        private final String url;

        URLOpenerClickListener(String url) {
            this.url = url;
        }

        @Override
        public void onClick(View v) {
            UIUtils.openURL(v.getContext(), url);
        }
    }
}
