/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.frostwire.android.R;

import androidx.annotation.Nullable;

import static com.frostwire.android.util.Asyncs.async;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 11/23/17.
 */
public class HexHiveView<T> extends View {
    //private static final Logger LOG = Logger.getLogger(HexHiveView.class);
    private Paint bitmapPaint;
    private Paint hexagonBorderPaint;
    private CubePaint emptyHexPaint;
    private CubePaint fullHexPaint;
    private DrawingProperties DP;
    private Bitmap compressedBitmap;

    private static float getHexWidth(float sideLength) {
        return (float) (Math.sqrt(3) * sideLength);
    }

    private static float getHexHeight(float sideLength) {
        return 2 * sideLength;
    }

    public static float getHexagonSideLength(final int width, final int height, final int numHexagons) {
        final float THREE_HALVES_SQRT_OF_THREE = 2.59807621135f;
        final int fullArea = width*height;
        // fullArea             numHexagons                     fullArea
        // --------         =                => s = sqrt(-----------------------)
        // 3/2*sqrt(3)*s^2                               3/2*sqrt(3)*numHexagons
        final float preliminarySideLength = (float) Math.sqrt(fullArea / (THREE_HALVES_SQRT_OF_THREE*numHexagons));

        float spaceToUse = 0.9f;

        if (numHexagons < 50) {
            spaceToUse = 0.85f;
        }

        if (numHexagons < 15) {
            spaceToUse = 0.8f;
        }

        return preliminarySideLength * spaceToUse;
    }

    public HexHiveView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        Resources r = getResources();
        TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.HexHiveView);
        int borderColor = typedArray.getColor(R.styleable.HexHiveView_hexhive_hexBorderColor, r.getColor(R.color.white));
        int emptyColor = typedArray.getColor(R.styleable.HexHiveView_hexhive_emptyColor, r.getColor(R.color.basic_gray_dark));
        int fullColor = typedArray.getColor(R.styleable.HexHiveView_hexhive_fullColor, r.getColor(R.color.basic_blue_highlight));
        typedArray.recycle();
        initPaints(borderColor, emptyColor, fullColor);
    }

    public boolean ready() {
        return DP != null && compressedBitmap != null;
    }

    private void initPaints(int borderColor,
                            int emptyColor,
                            int fullColor) {
        bitmapPaint = new Paint();
        hexagonBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hexagonBorderPaint.setStyle(Paint.Style.STROKE);
        hexagonBorderPaint.setColor(borderColor);
        hexagonBorderPaint.setStrokeWidth(0);
        emptyHexPaint = new CubePaint(10);
        emptyHexPaint.setColor(emptyColor);
        emptyHexPaint.setStyle(Paint.Style.FILL);
        fullHexPaint = new CubePaint(20);
        fullHexPaint.setColor(fullColor);
        fullHexPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // see asyncDraw to see how compressedBitmap is created (in a background thread)
        // once that thread is done, it posts an invalidate call on the UI's handler loop.
        if (compressedBitmap != null) {
            canvas.drawBitmap(compressedBitmap, 0, 0, bitmapPaint);
        }
    }

    public void updateData(HexDataAdapter<T> hexDataAdapter) {
        int canvasWidth = getWidth();
        int canvasHeight = getHeight();

        if (DP == null && canvasHeight > 0 && canvasWidth > 0 && hexDataAdapter != null) {
            DP = new DrawingProperties(hexDataAdapter,
                    hexagonBorderPaint.getStrokeWidth(),
                    getPaddingLeft(),
                    getPaddingTop(),
                    canvasWidth - getPaddingRight(),
                    canvasHeight - getPaddingBottom());
        }
        if (DP == null) {
            // not ready yet (perhaps during animation or rotation)
            return;
        }
        if (hexDataAdapter != null && hexDataAdapter.getFullHexagonsCount() >= 0 && canvasWidth > 0 && canvasHeight > 0) {
            async(this,
                    HexHiveView::asyncDraw,
                    canvasWidth, canvasHeight, hexDataAdapter,
                    (v, w, h, a) -> v.invalidate());
        }
    }

    public interface HexDataAdapter<T> {
        void updateData(T data);

        int getTotalHexagonsCount();

        int getFullHexagonsCount();

        boolean isFull(int hexOffset);
    }

    private static final class CubePaint extends Paint {
        private int baseColor = -1;
        private int darkColor = -1;
        private int lightColor = -1;
        private final int shades;

        CubePaint(int shades) {
            super();
            this.shades = shades;
        }

        @Override
        public void setColor(int color) {
            if (baseColor == -1) {
                this.baseColor = color;
                int A = (baseColor >> 24) & 0xff;
                int R = (baseColor >> 16) & 0xff;
                int G = (baseColor >> 8) & 0xff;
                int B = (baseColor) & 0xff;
                int darkR = Math.max(R - shades, 0);
                int darkG = Math.max(G - shades, 0);
                int darkB = Math.max(B - shades, 0);
                int lightR = Math.min(R + shades, 0xff);
                int lightG = Math.min(G + shades, 0xff);
                int lightB = Math.min(B + shades, 0xff);
                darkColor = (A & 0xff) << 24 | (darkR & 0xff) << 16 | (darkG & 0xff) << 8 | (darkB & 0xff);
                lightColor = (A & 0xff) << 24 | (lightR & 0xff) << 16 | (lightG & 0xff) << 8 | (lightB & 0xff);
            }
            super.setColor(color);
        }

        public void useBaseColor() {
            if (baseColor != -1) {
                setColor(baseColor);
            }
        }

        public void useDarkColor() {
            if (darkColor != -1) {
                setColor(darkColor);
            }
        }

        public void useLightColor() {
            if (lightColor != -1) {
                setColor(lightColor);
            }
        }
    }

    private static final class DrawingProperties {
        // Painting Area Configuration
        /**
         * Drawing area top-left
         */
        Point origin;

        /**
         * Drawing area center
         */
        Point center;

        /**
         * Drawing are bottom-right cornerBuffer
         */
        Point end;

        /**
         * Drawing area dimensions
         */
        Rect dimensions;

        /**
         * Drawing area width
         */
        private int width;

        /**
         * Drawing area height
         */
        private int height;
        // Hexagon Geometry Helpers
        /**
         * Number of hexagons to draw
         */
        private int numHexs;

        /**
         * Side length of each hexagon
         */
        private float hexSideLength;

        /**
         * Height of each hexagon
         */
        private float hexHeight;

        /**
         * Width of each hexagon
         */
        private float hexWidth;

        /**
         * Hexagon border stroke width, has to be converted to pixels depending on screen density
         */
        private float hexBorderStrokeWidth;

        private final Point evenRowOrigin;

        private final Point oddRowOrigin;

        /**
         * Point object we'll reuse to draw hexagons
         * (Object creation and destruction must be avoided when calling onDraw())
         */
        private final Point hexCenterBuffer = new Point(-1, -1);

        /**
         * Point object we'll reuse to draw hexagon sides
         * (Object creation and destruction must be avoided when calling onDraw())
         */
        private final Point cornerBuffer = new Point(-1, -1);

        /**
         * Path object we'll reuse to draw the filled areas of the hexagons
         */
        private final Path fillPathBuffer = new Path();

        DrawingProperties(HexDataAdapter adapter, float hexBorderWidth, int left, int top, int right, int bottom) {
            if (adapter == null) {
                throw new RuntimeException("check your logic, you need a data adapter before calling initDrawingProperties");
            }
            // The canvas can paint the entire view, if padding has been defined,
            // we won't draw outside the padded area.
            hexBorderStrokeWidth = hexBorderWidth;
            dimensions = new Rect(left, top, right, bottom);
            origin = new Point(dimensions.left, dimensions.top);
            center = new Point(dimensions.centerX(), dimensions.centerY());
            end = new Point(dimensions.right, dimensions.bottom);
            width = dimensions.width();
            height = dimensions.height();
            numHexs = adapter.getTotalHexagonsCount();
            hexSideLength = getHexagonSideLength(width, height, numHexs);
            hexHeight = getHexHeight(hexSideLength) + 2 * hexBorderStrokeWidth;
            hexWidth = getHexWidth(hexSideLength) + (2 * hexBorderStrokeWidth);
            evenRowOrigin = new Point(
                    (int) (origin.x + (hexWidth / 2)),
                    (int) (origin.y + (hexHeight / 2)));
            // calculate number of hexagons in an even row
            oddRowOrigin = new Point(
                    (int) (evenRowOrigin.x + (hexWidth / 2)),
                    (int) (evenRowOrigin.y + hexHeight));
        }
    }

    private void asyncDraw(int canvasWidth, int canvasHeight, HexDataAdapter adapter) {
        // with DP we don't need to think about padding offsets. We just use DP numbers for our calculations
        DP.hexCenterBuffer.set(DP.evenRowOrigin.x, DP.evenRowOrigin.y);
        boolean evenRow = true;
        int pieceIndex = 0;
        float heightQuarter = DP.hexHeight / 4;
        float threeQuarters = heightQuarter * 3;
        // if we have just one piece to draw, we'll draw it in the center
        if (DP.numHexs == 1) {
            DP.hexCenterBuffer.x = DP.center.x;
            DP.hexCenterBuffer.y = DP.center.y;
        }
        boolean drawCubes = DP.numHexs <= 600;
        Bitmap bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        while (pieceIndex < DP.numHexs) {
            drawHexagon(DP, canvas, hexagonBorderPaint, (adapter.isFull(pieceIndex) ? fullHexPaint : emptyHexPaint), drawCubes);
            pieceIndex++;
            DP.hexCenterBuffer.x += DP.hexWidth + (hexagonBorderPaint.getStrokeWidth() * 4);
            float rightSide = DP.hexCenterBuffer.x + (DP.hexWidth / 2) + (hexagonBorderPaint.getStrokeWidth() * 3);
            if (rightSide >= DP.end.x) {
                evenRow = !evenRow;
                DP.hexCenterBuffer.x = (evenRow) ? DP.evenRowOrigin.x : DP.oddRowOrigin.x;
                DP.hexCenterBuffer.y += threeQuarters;
            }
        }
        compressedBitmap = bitmap;
    }

    // Drawing/Geometry functions

    /**
     * @param outCorner    - a re-usable Point buffer to output the
     * @param inCenter     - a reusable Point buffer representing the center coordinates of a hexagon
     * @param sideLength   - length of hexagon side
     * @param cornerNumber - from 0 to 6 (we count 7 because we have to get back to the origin)
     */
    private static void getHexCorner(final Point outCorner, final Point inCenter, int cornerNumber, float sideLength) {
        double angle_rad = Math.toRadians((60 * cornerNumber) + 30);
        outCorner.set((int) (inCenter.x + sideLength * Math.cos(angle_rad)), (int) (inCenter.y + sideLength * Math.sin(angle_rad)));
    }

    private static void drawHexagon(final DrawingProperties DP,
                                    final Canvas canvas,
                                    final Paint borderPaint,
                                    final CubePaint fillPaint,
                                    final boolean drawCube) {
        DP.fillPathBuffer.reset();
        for (int i = 0; i < 7; i++) {
            getHexCorner(DP.cornerBuffer, DP.hexCenterBuffer, i, DP.hexSideLength);
            if (i == 0) {
                DP.fillPathBuffer.moveTo(DP.cornerBuffer.x, DP.cornerBuffer.y);
            } else {
                DP.fillPathBuffer.lineTo(DP.cornerBuffer.x, DP.cornerBuffer.y);
            }
        }
        canvas.drawPath(DP.fillPathBuffer, fillPaint);
        canvas.drawPath(DP.fillPathBuffer, borderPaint);
        DP.fillPathBuffer.reset();
        if (drawCube) {
            // LEFT FACE
            // bottom corner - 90 degrees points straight below (zero at horizon on the right side)
            // angles move clockwise. 0 is below right horizon, 1 right below the center...

            // make left face path
            DP.fillPathBuffer.moveTo(DP.hexCenterBuffer.x, DP.hexCenterBuffer.y);
            getHexCorner(DP.cornerBuffer, DP.hexCenterBuffer, 1, DP.hexSideLength);
            DP.fillPathBuffer.lineTo(DP.cornerBuffer.x, DP.cornerBuffer.y);
            getHexCorner(DP.cornerBuffer, DP.hexCenterBuffer, 2, DP.hexSideLength);
            DP.fillPathBuffer.lineTo(DP.cornerBuffer.x, DP.cornerBuffer.y);
            getHexCorner(DP.cornerBuffer, DP.hexCenterBuffer, 3, DP.hexSideLength);
            DP.fillPathBuffer.lineTo(DP.cornerBuffer.x, DP.cornerBuffer.y);
            DP.fillPathBuffer.lineTo(DP.hexCenterBuffer.x, DP.hexCenterBuffer.y);
            fillPaint.useDarkColor();
            // fill left face path
            canvas.drawPath(DP.fillPathBuffer, fillPaint);

            // TOP FACE
            // make top face path
            DP.fillPathBuffer.reset();
            DP.fillPathBuffer.moveTo(DP.hexCenterBuffer.x, DP.hexCenterBuffer.y);
            getHexCorner(DP.cornerBuffer, DP.hexCenterBuffer, 3, DP.hexSideLength);
            DP.fillPathBuffer.lineTo(DP.cornerBuffer.x, DP.cornerBuffer.y);
            getHexCorner(DP.cornerBuffer, DP.hexCenterBuffer, 4, DP.hexSideLength);
            DP.fillPathBuffer.lineTo(DP.cornerBuffer.x, DP.cornerBuffer.y);
            getHexCorner(DP.cornerBuffer, DP.hexCenterBuffer, 5, DP.hexSideLength);
            DP.fillPathBuffer.lineTo(DP.cornerBuffer.x, DP.cornerBuffer.y);
            DP.fillPathBuffer.lineTo(DP.hexCenterBuffer.x, DP.hexCenterBuffer.y);
            fillPaint.useLightColor();
            canvas.drawPath(DP.fillPathBuffer, fillPaint);

            // Now draw 3 lines from the center to corners 1,3 and 5
            DP.fillPathBuffer.reset();
            DP.fillPathBuffer.moveTo(DP.hexCenterBuffer.x, DP.hexCenterBuffer.y);
            getHexCorner(DP.cornerBuffer, DP.hexCenterBuffer, 1, DP.hexSideLength);
            DP.fillPathBuffer.lineTo(DP.cornerBuffer.x, DP.cornerBuffer.y);
            DP.fillPathBuffer.moveTo(DP.hexCenterBuffer.x, DP.hexCenterBuffer.y);
            getHexCorner(DP.cornerBuffer, DP.hexCenterBuffer, 3, DP.hexSideLength);
            DP.fillPathBuffer.lineTo(DP.cornerBuffer.x, DP.cornerBuffer.y);
            DP.fillPathBuffer.moveTo(DP.hexCenterBuffer.x, DP.hexCenterBuffer.y);
            getHexCorner(DP.cornerBuffer, DP.hexCenterBuffer, 5, DP.hexSideLength);
            DP.fillPathBuffer.lineTo(DP.cornerBuffer.x, DP.cornerBuffer.y);
            canvas.drawPath(DP.fillPathBuffer, borderPaint);

            DP.fillPathBuffer.reset();
            fillPaint.useBaseColor();
        }
        DP.cornerBuffer.set(-1, -1);
    }

}
