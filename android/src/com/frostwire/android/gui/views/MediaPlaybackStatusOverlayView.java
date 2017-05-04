/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import com.frostwire.android.R;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 5/5/17.
 */


public class MediaPlaybackStatusOverlayView extends View {
    private MediaPlaybackOverlayPainter mediaPlaybackOverlayPainter;
    private MediaPlaybackOverlayPainter.MediaPlaybackState playbackState;
    private final int circleStrokeWidth;

    public MediaPlaybackStatusOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray attributes = context.getTheme().obtainStyledAttributes(attrs, R.styleable.MediaPlaybackStatusOverlayView, 0, 0);
        circleStrokeWidth = attributes.getInteger(R.styleable.MediaPlaybackStatusOverlayView_circleStrokeWidth, 5);
        attributes.recycle();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mediaPlaybackOverlayPainter == null) {
            mediaPlaybackOverlayPainter = new MediaPlaybackOverlayPainter();
            mediaPlaybackOverlayPainter.setCircleStrokeWidth(circleStrokeWidth);
            mediaPlaybackOverlayPainter.setOverlayState(playbackState);
        }
        if (mediaPlaybackOverlayPainter != null) {
            mediaPlaybackOverlayPainter.drawOverlay(canvas, getWidth(), getHeight());
        }
    }

    public void setPlaybackState(MediaPlaybackOverlayPainter.MediaPlaybackState playbackState) {
        this.playbackState = playbackState;

        if (mediaPlaybackOverlayPainter != null) {
            mediaPlaybackOverlayPainter.setOverlayState(playbackState);
        }

        setVisibility(playbackState != MediaPlaybackOverlayPainter.MediaPlaybackState.NONE ? View.VISIBLE : View.GONE);
        if (playbackState != MediaPlaybackOverlayPainter.MediaPlaybackState.NONE) {
            invalidate();
        }
    }
}
