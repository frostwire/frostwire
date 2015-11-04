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
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.ImageButton;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public class BrowseThumbnailImageButton extends ImageButton {
    private final MediaPlaybackOverlay overlay;

    public BrowseThumbnailImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        overlay = new MediaPlaybackOverlay(this);
    }

    public void setOverlayState(MediaPlaybackOverlay.MediaPlaybackState state) {
        overlay.setOverlayState(state);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (canvas != null) {
            super.onDraw(canvas);
            overlay.drawOverlay(canvas);
        }
    }
}