/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import com.frostwire.android.R;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 */
public final class MediaPlaybackStatusOverlayView extends View {

    private MediaPlaybackOverlayPainter mediaPlaybackOverlayPainter;
    private MediaPlaybackOverlayPainter.MediaPlaybackState playbackState;
    private final int circleStrokeWidth;

    public MediaPlaybackStatusOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.MediaPlaybackStatusOverlayView, 0, 0);
        circleStrokeWidth = getCircleStrokeWidth(attributes);
        attributes.recycle();
    }

    private int getCircleStrokeWidth(TypedArray attributes) {
        int defaultCircleStrokeWidth = getResources().getDimensionPixelSize(R.dimen.default_circleStrokeWidth);
        try {
            return attributes.getDimensionPixelSize(R.styleable.MediaPlaybackStatusOverlayView_circleStrokeWidth,
                    defaultCircleStrokeWidth);
        } catch (UnsupportedOperationException e) {
            // surprisingly (or not), there are a small number of poor phone implementations of
            // this method out there
            return defaultCircleStrokeWidth;
        }
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
