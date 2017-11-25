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
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

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
    private Paint backgroundPaint;
    private Paint hexagonBorderPaint;
    private Paint emptyHexPaint;
    private Paint fullHexPaint;
    private Paint textPaint;

    public HexHiveView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        Resources r = getResources();
        TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.HexHiveView);
        int backgroundColor = typedArray.getColor(R.styleable.HexHiveView_hexhive_backgroundColor, r.getColor(R.color.basic_blue_dark));
        int borderColor = typedArray.getColor(R.styleable.HexHiveView_hexhive_hexBorderColor, r.getColor(R.color.white));
        int emptyColor = typedArray.getColor(R.styleable.HexHiveView_hexhive_emptyColor, r.getColor(R.color.basic_gray_dark));
        int fullColor = typedArray.getColor(R.styleable.HexHiveView_hexhive_fullColor, r.getColor(R.color.basic_blue_highlight));
        int textColor = typedArray.getColor(R.styleable.HexHiveView_hexhive_textColor, r.getColor(R.color.white));
        typedArray.recycle();
        initPaints(backgroundColor, borderColor, emptyColor, fullColor, textColor);
    }

    private void initPaints(int backgroundColor,
                            int borderColor,
                            int emptyColor,
                            int fullColor,
                            int textColor) {
        backgroundPaint = new Paint();
        backgroundPaint.setColor(backgroundColor);

        hexagonBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hexagonBorderPaint.setColor(borderColor);

        emptyHexPaint = new Paint();
        emptyHexPaint.setColor(emptyColor);

        fullHexPaint = new Paint();
        fullHexPaint.setColor(fullColor);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(textColor);
        textPaint.setTextSize(14f);
        backgroundPaint = new Paint(0);
        backgroundPaint.setColor(Color.BLACK);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
// maybe i'll need to deal with this
//        if (getOrientation() == HORIZONTAL) {
//
//        }
        // EXACTLY -> User hardcoded the value in the XML
        // AT_MOST -> make it as large as it wants up to the specified size
        // UNSPECIFIED -> probably a wrap size, so use the desired width
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = resolveSizeAndState(MeasureSpec.getSize(widthMeasureSpec),widthMeasureSpec, 1);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = resolveSizeAndState(MeasureSpec.getSize(heightMeasureSpec), heightMeasureSpec, 0);
        LOG.info(String.format("onMeasure() -> MeasureSpecs -> EXACTLY(%d), AT_MOST(%d), UNSPECIFIED(%d) ", MeasureSpec.EXACTLY, MeasureSpec.AT_MOST, MeasureSpec.UNSPECIFIED));
        LOG.info(String.format("onMeasure() -> { widthMode: %d, widthSize: %d, heightMode: %d, heightSize: %d } ", widthMode, widthSize, heightMode, heightSize));
        setMeasuredDimension(widthSize, heightSize);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        // LETS TRY TO AVOID OBJECT ALLOCATIONS HERE.
        //super.onDraw(canvas);
        //canvas.drawRect(new Rect(0, 0, getWidth(), getHeight()), backgroundPaint);
        canvas.drawText("HexHiveView", 25, 25, textPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        LOG.info(String.format("onSizeChanged(w=%d, h=%d, oldw=%d, oldh=%d)", w, h, oldw, oldh));
        super.onSizeChanged(w, h, oldw, oldh);
    }
}
