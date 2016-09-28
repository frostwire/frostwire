/*

 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

import android.content.ContentUris;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.util.ImageLoader;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class PlayerMenuItemView extends LinearLayout {

    private ImageView imageThumbnail;
    private TextView textTitle;
    private TextView textArtist;
    private TextView textPlayingNow;

    public PlayerMenuItemView(Context context, AttributeSet set) {
        super(context, set);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        View.inflate(getContext(), R.layout.view_player_menuitem, this);

        if (isInEditMode()) {
            return;
        }

        imageThumbnail = (ImageView) findViewById(R.id.view_player_menu_item_thumbnail);
        textTitle = (TextView) findViewById(R.id.view_player_menu_item_title);
        textArtist = (TextView) findViewById(R.id.view_player_menu_item_artist);
        textPlayingNow = (TextView) findViewById(R.id.view_player_menu_item_playingnow);

        refresh();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        onTouchEvent(ev);
        return false;
    }

    public void refresh() {
        if (Engine.instance() != null && Engine.instance().getMediaPlayer() != null) {

            FileDescriptor fd = Engine.instance().getMediaPlayer().getCurrentFD();

            if (fd != null) {
                if (getVisibility() == View.GONE) {
                    setVisibility(View.VISIBLE);
                }

                if (getVisibility() == View.VISIBLE) {
                    textTitle.setText(fd.title);
                    textArtist.setText(fd.artist);
                    textPlayingNow.setText((Engine.instance().getMediaPlayer().isPlaying() ? getResources().getString(R.string.playing) : getResources().getString(R.string.paused)));
                    setArtwork(fd);
                }
            } else {
                if (getVisibility() == View.VISIBLE) {
                    setVisibility(View.GONE);

                    imageThumbnail.setImageBitmap(null);
                    textTitle.setText("");
                    textArtist.setText("");
                    textPlayingNow.setText("");
                }
            }
        }
    }

    private void setArtwork(FileDescriptor fd) {
        Uri uri = ContentUris.withAppendedId(ImageLoader.ALBUM_THUMBNAILS_URI, fd.albumId);
        ImageLoader.getInstance(getContext()).load(uri, imageThumbnail, 96, 96, R.drawable.artwork_default_micro_kind);
    }

    public void unbindDrawables() {
        if (imageThumbnail != null) {
            Drawable drawable = imageThumbnail.getDrawable();
            if (drawable != null) {
                drawable.setCallback(null);
            }
        }
    }
}