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
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import com.frostwire.android.R;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 11/23/17.
 */
public class HexHiveView<T> extends View {
    //private static final Logger LOG = Logger.getLogger(HexHiveView.class);
    private boolean debug = true;
    private Paint hexagonBorderPaint;
    private Paint emptyHexPaint;
    private Paint fullHexPaint;
    private Paint textPaint;
    private Paint debugLinesPaint;
    private DrawingProperties DP;
    private HexDataAdapter adapter;
    private int lastKnownPieceCount;

    public interface HexDataAdapter<T> {
        void updateData(T data);

        int getTotalHexagonsCount();

        int getFullHexagonsCount();

        boolean isFull(int hexOffset);
    }

    private final class DrawingProperties {
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

        /**
         * Maximum number of hexagons to draw per row
         */
        private int maxHexagonsPerRow;

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

        DrawingProperties(HexDataAdapter adapter, float hexBorderWidth) {
            if (adapter == null) {
                throw new RuntimeException("check your logic, you need a data adapter before calling initDrawingProperties");
            }
            // The canvas can paint the entire view, if padding has been defined,
            // we won't draw outside the padded area.
            hexBorderStrokeWidth = hexBorderWidth;
            dimensions = new Rect(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
            origin = new Point(dimensions.left, dimensions.top);
            center = new Point(dimensions.centerX(), dimensions.centerY());
            end = new Point(dimensions.right, dimensions.bottom);
            width = dimensions.width();
            height = dimensions.height();
            numHexs = adapter.getTotalHexagonsCount();
            hexSideLength = getHexagonSideLength(width * height, numHexs);
            hexHeight = getHexHeight(hexSideLength) - 2 * hexBorderStrokeWidth;
            hexWidth = getHexWidth(hexSideLength) + (2 * hexBorderStrokeWidth);
            maxHexagonsPerRow = (int) (dimensions.width() / hexWidth) + 1;
            evenRowOrigin = new Point(
                    (int) (origin.x + (hexWidth / 2)),
                    (int) (origin.y + (hexHeight / 2)));
            // calculate number of hexagons in an even row
            oddRowOrigin = new Point(
                    (int) (evenRowOrigin.x + (hexWidth / 2)),
                    (int) (evenRowOrigin.y + hexHeight));
        }
    }

    public HexHiveView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        lastKnownPieceCount = -1;
        Resources r = getResources();
        TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.HexHiveView);
        int borderColor = typedArray.getColor(R.styleable.HexHiveView_hexhive_hexBorderColor, r.getColor(R.color.white));
        float borderWidth = typedArray.getFloat(R.styleable.HexHiveView_hexhive_borderWidth, 1.0f);
        int emptyColor = typedArray.getColor(R.styleable.HexHiveView_hexhive_emptyColor, r.getColor(R.color.basic_gray_dark));
        int fullColor = typedArray.getColor(R.styleable.HexHiveView_hexhive_fullColor, r.getColor(R.color.basic_blue_highlight));
        int debugLinesColor = typedArray.getColor(R.styleable.HexHiveView_hexhive_debugLinesColor, r.getColor(R.color.black));
        int textColor = typedArray.getColor(R.styleable.HexHiveView_hexhive_textColor, r.getColor(R.color.white));
        float textSize = typedArray.getFloat(R.styleable.HexHiveView_hexhive_textSize, 20f);
        debug = typedArray.getBoolean(R.styleable.HexHiveView_hexhive_debug, false);
        typedArray.recycle();
        initPaints(borderColor, borderWidth, emptyColor, fullColor, debugLinesColor, textColor, textSize);
    }

    public void updateData(HexDataAdapter<T> hexDataAdapter) {
        this.adapter = hexDataAdapter;
        if (adapter.getFullHexagonsCount() != lastKnownPieceCount) {
            lastKnownPieceCount = adapter.getFullHexagonsCount();
            invalidate();
        }
    }

    private void initPaints(int borderColor,
                            float borderWidth,
                            int emptyColor,
                            int fullColor,
                            int debugLinesColor,
                            int textColor,
                            float textSize) {
        hexagonBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hexagonBorderPaint.setStyle(Paint.Style.STROKE);
        hexagonBorderPaint.setColor(borderColor);
        hexagonBorderPaint.setStrokeWidth(borderWidth);
        emptyHexPaint = new Paint();
        emptyHexPaint.setColor(emptyColor);
        emptyHexPaint.setStyle(Paint.Style.FILL);
        fullHexPaint = new Paint();
        fullHexPaint.setColor(fullColor);
        fullHexPaint.setStyle(Paint.Style.FILL);
        debugLinesPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        debugLinesPaint.setColor(debugLinesColor);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(textColor);
        textPaint.setTextSize(textSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // LETS TRY TO AVOID REPEATED OBJECT ALLOCATIONS HERE
        if (DP == null && getHeight() > 0 && getWidth() > 0 && adapter != null) {
            DP = new DrawingProperties(adapter, hexagonBorderPaint.getStrokeWidth());
        }
        if (DP == null) {
            // not ready yet (perhaps during animation or rotation)
            return;
        }

        // with DP we don't need to think about padding offsets. We just use DP numbers for our calculations
        DP.hexCenterBuffer.set(DP.evenRowOrigin.x, DP.evenRowOrigin.y);
        int drawnHexagons = 0;
        boolean evenRow = true;
        int pieceIndex = 0;
        for (int i = 0;
             i < DP.maxHexagonsPerRow && drawnHexagons < DP.numHexs;
             i++) {
            drawHexagon(DP, canvas, hexagonBorderPaint, adapter.isFull(pieceIndex) ? fullHexPaint : emptyHexPaint);
            DP.hexCenterBuffer.x += DP.hexWidth + (hexagonBorderPaint.getStrokeWidth()*3);
            drawnHexagons++;
            if (i == DP.maxHexagonsPerRow-1) {
                evenRow = !evenRow;
                DP.hexCenterBuffer.x = (evenRow) ? DP.evenRowOrigin.x : DP.oddRowOrigin.x;
                DP.hexCenterBuffer.y += DP.hexHeight -  (hexagonBorderPaint.getStrokeWidth()*3);
                if (!evenRow) {
                    i = 0; // will start at i=1
                } else {
                    i = -1; // will start at i=0
                }
            }
            pieceIndex++;
        }

        if (debug) {
            canvas.drawLine(DP.origin.x, DP.origin.y, DP.end.x, DP.origin.y, debugLinesPaint);
            canvas.drawLine(DP.origin.x, DP.origin.y, DP.origin.x, DP.end.y, debugLinesPaint);
            canvas.drawLine(DP.end.x, DP.end.y, DP.origin.x, DP.end.y, debugLinesPaint);
            canvas.drawLine(DP.end.x, DP.end.y, DP.end.x, DP.origin.y, debugLinesPaint);
        }
    }

    // Drawing/Geometry functions
    private float getHexagonSideLength(int canvasArea, int numHexagons) {
        // if hexagons were squared...
        double squaredBlockArea = canvasArea / numHexagons;
        double squareSide = Math.sqrt(squaredBlockArea);
        double hexWidth = squareSide;
        float sideLength = getSideLength((float) hexWidth);
        return sideLength;
    }

    /**
     * @param outCorner    - a re-usable Point buffer to output the
     * @param inCenter     - a reusable Point buffer representing the cente coordinates of a hexagon
     * @param sideLength   - length of hexagon side
     * @param cornerNumber - from 0 to 6 (we count 7 because we have to get back to the origin)
     */
    private void getHexCorner(final Point outCorner, final Point inCenter, int cornerNumber, float sideLength) {
        double angle_rad = Math.toRadians((60 * cornerNumber) + 30);
        outCorner.set((int) (inCenter.x + sideLength * Math.cos(angle_rad)), (int) (inCenter.y + sideLength * Math.sin(angle_rad)));
    }

    // TODO: Pass drawing mode to save up to 50% in lines drawn
    // FULL, TOP_PARTIAL_5, BOTTOM_PARTIAL_4, BOTTOM_PARTIAL_3
    private void drawHexagon(final DrawingProperties DP,
                             final Canvas canvas,
                             final Paint borderPaint,
                             final Paint fillPaint) {
        for (int i = 0; i < 7; i++) {
            getHexCorner(DP.cornerBuffer, DP.hexCenterBuffer, i, DP.hexSideLength);
            if (i==0) {
                DP.fillPathBuffer.reset();
                DP.fillPathBuffer.moveTo(DP.cornerBuffer.x, DP.cornerBuffer.y);
            } else {
                DP.fillPathBuffer.lineTo(DP.cornerBuffer.x, DP.cornerBuffer.y);
            }
        }
        canvas.drawPath(DP.fillPathBuffer, fillPaint);
        canvas.drawPath(DP.fillPathBuffer, borderPaint);
        DP.cornerBuffer.set(-1, -1);
    }

    private float getSideLength(float hexWidth) {
        return (float) (hexWidth / (2 * Math.cos(Math.toRadians(30))));
    }

    private float getHexWidth(float sideLength) {
        return (float) Math.cos(Math.toRadians(30)) * (sideLength * 2);
    }

    private float getHexHeight(float sideLength) {
        return (float) (4 * (Math.sin(Math.toRadians(30)) * sideLength));
    }
}
