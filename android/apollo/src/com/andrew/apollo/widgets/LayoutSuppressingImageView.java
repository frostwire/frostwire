/*
 * Copyright (C) 2012 Android Open Source Project Licensed under the Apache
 * License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
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
 * A custom {@link ImageView} that improves the performance by not passing
 * requestLayout() to its parent, taking advantage of knowing that image size
 * won't change once set.
 */
public class LayoutSuppressingImageView extends ImageView {

    /**
     * @param context The {@link Context} to use
     * @param attrs The attributes of the XML tag that is inflating the view
     */
    public LayoutSuppressingImageView(final Context context, final AttributeSet attrs) {
        super(context, attrs);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestLayout() {
        forceLayout();
    }

}
