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

import android.graphics.*;
import android.view.View;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public class MediaPlaybackOverlay {
    public enum MediaPlaybackState {
        NONE, PLAY, PREVIEW, STOP
    }

    private static final Paint paintCircleFill = new Paint();
    private static final Paint paintCircleStroke = new Paint();
    private static final Paint paintShapeFill = new Paint();
    private MediaPlaybackState state;
    private final WeakReference<View> viewReference;

    static {
        paintCircleFill.setColor(Color.parseColor("#b0ffffff"));
        paintCircleFill.setStyle(Paint.Style.FILL);
        paintCircleFill.setAntiAlias(true);

        paintCircleStroke.setColor(Color.parseColor("#ff525254"));
        paintCircleStroke.setStrokeWidth(5);
        paintCircleStroke.setStyle(Paint.Style.STROKE);
        paintCircleStroke.setAntiAlias(true);

        paintShapeFill.setColor(Color.parseColor("#ff525254"));
        paintShapeFill.setStyle(Paint.Style.FILL);
        paintShapeFill.setAntiAlias(true);
    }

    public MediaPlaybackOverlay(View view) {
        this.state = MediaPlaybackState.NONE;
        viewReference = Ref.weak(view);
    }

    public void setOverlayState(MediaPlaybackState state) {
        this.state = state;
    }

    public void drawOverlay(Canvas canvas) {
        if (state != MediaPlaybackState.NONE) {
            drawCircle(canvas);
            if (state == MediaPlaybackState.PLAY || state == MediaPlaybackState.PREVIEW) {
                drawTriangle(canvas);
            } else if (state == MediaPlaybackState.STOP) {
                drawSquare(canvas);
            }
        }
    }

    private void drawCircle(Canvas canvas) {
        if (Ref.alive(viewReference)) {
            View view = viewReference.get();
            float x = view.getWidth() / 2.0f;
            int h = view.getHeight();
            float y = h / 2.0f;
            float r = h / 6.0f + 2;
            canvas.drawCircle(x, y, r, paintCircleFill);
            canvas.drawCircle(x, y, r, paintCircleStroke);
        }
    }

    private void drawTriangle(Canvas canvas) {
        if (Ref.alive(viewReference)) {
            View view = viewReference.get();
            int x = view.getWidth() / 2;
            int h  = view.getHeight();
            int y = h / 2;
            int w = h / 7;
            Path path = getTriangle(new Point(x - w / 2 + 3, y - w / 2), w);
            canvas.drawPath(path, paintShapeFill);
        }
    }

    private void drawSquare(Canvas canvas) {
        if (Ref.alive(viewReference)) {
            View view = viewReference.get();

            float x = view.getWidth() / 2.0f;
            int h = view.getHeight();
            float y = h / 2.0f;
            int w = h / 7;

            canvas.drawRect(x - w / 2, y - w / 2, x + w / 2, y + w / 2, paintShapeFill);
        }
    }

    private Path getTriangle(Point p1, int width) {
        Point p2 = new Point(p1.x, p1.y + width);
        Point p3 = new Point(p1.x + width, p1.y + (width / 2));

        Path path = new Path();
        path.moveTo(p1.x, p1.y);
        path.lineTo(p2.x, p2.y);
        path.lineTo(p3.x, p3.y);

        return path;
    }
}
