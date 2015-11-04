/*

 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(TM). All rights reserved.
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