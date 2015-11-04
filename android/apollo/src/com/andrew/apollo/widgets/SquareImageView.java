/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.andrew.apollo.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * A custom {@link ImageView} that is sized to be a perfect square, otherwise
 * functions like a typical {@link ImageView}.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class SquareImageView extends LayoutSuppressingImageView {

    /**
     * @param context The {@link Context} to use
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public SquareImageView(final Context context, final AttributeSet attrs) {
        super(context, attrs);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onMeasure(final int widthSpec, final int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        final int mSize = Math.min(getMeasuredWidth(), getMeasuredHeight());
        setMeasuredDimension(mSize, mSize);
    }

}
