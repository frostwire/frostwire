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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.Dimension;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.frostwire.android.R;
import com.frostwire.util.Logger;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 11/23/17.
 */
// TODO: data handling to be done with <T> templates
// we should have a way to update the T[] dataModel
public class HexHiveView extends View {
    private static final Logger LOG = Logger.getLogger(HexHiveView.class);
    private static final double SQRT_OF_THREE = 1.7320508075688772;
    private Paint backgroundPaint;
    private Paint hexagonBorderPaint;
    private Paint emptyHexPaint;
    private Paint fullHexPaint;
    private Paint textPaint;
    private Rect dimensions;

    public HexHiveView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        Resources r = getResources();
        TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.HexHiveView);
        int backgroundColor = typedArray.getColor(R.styleable.HexHiveView_hexhive_backgroundColor, r.getColor(R.color.basic_blue_dark));
        int borderColor = typedArray.getColor(R.styleable.HexHiveView_hexhive_hexBorderColor, r.getColor(R.color.white));
        float borderWidth = typedArray.getFloat(R.styleable.HexHiveView_hexhive_borderWidth, 1.0f);
        int emptyColor = typedArray.getColor(R.styleable.HexHiveView_hexhive_emptyColor, r.getColor(R.color.basic_gray_dark));
        int fullColor = typedArray.getColor(R.styleable.HexHiveView_hexhive_fullColor, r.getColor(R.color.basic_blue_highlight));
        int textColor = typedArray.getColor(R.styleable.HexHiveView_hexhive_textColor, r.getColor(R.color.white));
        float textSize = typedArray.getFloat(R.styleable.HexHiveView_hexhive_textSize, 20f);
        typedArray.recycle();
        initPaints(backgroundColor, borderColor, borderWidth, emptyColor, fullColor, textColor, textSize);
    }

    private void initPaints(int backgroundColor,
                            int borderColor,
                            float borderWidth,
                            int emptyColor,
                            int fullColor,
                            int textColor,
                            float textSize) {
        backgroundPaint = new Paint();
        backgroundPaint.setColor(backgroundColor);
        hexagonBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hexagonBorderPaint.setColor(borderColor);
        hexagonBorderPaint.setStrokeWidth(borderWidth);
        emptyHexPaint = new Paint();
        emptyHexPaint.setColor(emptyColor);
        fullHexPaint = new Paint();
        fullHexPaint.setColor(fullColor);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(textColor);
        textPaint.setTextSize(textSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // LETS TRY TO AVOID OBJECT ALLOCATIONS HERE.
        //super.onDraw(canvas);
        if (dimensions == null && getHeight() > 0 && getWidth() > 0) {
            dimensions = new Rect(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        }
        int sideLength = 0;
        if (dimensions != null) {
            canvas.drawRect(dimensions, backgroundPaint);
            // TODO: Store all these initial parameters in a reusable object
            sideLength = getHexagonSideLength(dimensions.width()*dimensions.height(),  100);
            int hexHeight = getHexHeight(sideLength);
            int hexWidth = getHexWidth(hexHeight);
            int y = hexWidth/2;
            for (int i=0; i < 7; i++) {
                int x = (hexWidth / 2) + (hexWidth * i);
                drawHexagon(x, y, sideLength, true, canvas, hexagonBorderPaint);
            }
        }
        canvas.drawText(String.format("HexHiveView Rect(l=%d,t=%d,r=%d,b=%d)\r\nSideLength=%d", dimensions.left, dimensions.top, dimensions.right, dimensions.bottom, sideLength), 25 + dimensions.left, 25 + dimensions.top, textPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        LOG.info(String.format("onSizeChanged(w=%d, h=%d, oldw=%d, oldh=%d)", w, h, oldw, oldh));
        super.onSizeChanged(w, h, oldw, oldh);
    }

    // Drawing/Geometry functions
    private int getHexagonSideLength(int canvasArea, int numHexagons) {
        return (int) Math.sqrt((2*canvasArea)/(numHexagons * SQRT_OF_THREE));
    }

    /**
     *
     * @param out - a re-usable array holding [x,y] of the output corner to avoid object creation/destruction
     * @param x - center.x of hexagon
     * @param y - center.y of hexagon
     * @param sideLength - length of hexagon side
     * @param sideNumber - from 0 to 5
     */
    private void getHexCorner(int[] out, int x, int y, int sideLength, int sideNumber, boolean pointyTopped) {
        double angle_rad = Math.toRadians(60 * sideNumber + (pointyTopped ? 30 : 0));
        out[0] = (int) (x + sideLength * Math.cos(angle_rad));
        out[1] = (int) (y + sideLength * Math.sin(angle_rad));
    }

    private void drawHexagon(int x, int y, int sideLength, boolean pointyTopped, Canvas canvas, Paint paint) {
        int[] corner = new int[2];
        int[] lastCorner = new int[2];
        for (int i=0; i < 7; i++) {
            getHexCorner(corner, x, y, sideLength, i, pointyTopped);
            if (i > 0) {
                canvas.drawLine(
                        lastCorner[0],
                        lastCorner[1],
                        corner[0],
                        corner[1],
                        paint);
            }
            lastCorner[0] = corner[0];
            lastCorner[1] = corner[1];
        }
    }

    private int getHexWidth(int height) {
        return (int) (SQRT_OF_THREE / 2 * height);
    }

    private int getHexHeight(int sideLength) {
        return sideLength*2;
    }
}
