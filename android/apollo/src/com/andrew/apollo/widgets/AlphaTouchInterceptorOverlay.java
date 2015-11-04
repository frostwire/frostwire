/*
 * Copyright (C) 2012 The Android Open Source Project Licensed under the Apache
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
import android.view.View;
import android.widget.FrameLayout;

/**
 * A View that other Views can use to create a touch-interceptor layer above
 * their other sub-views. This layer can be enabled and disabled; when enabled,
 * clicks are intercepted and passed to a listener. Also supports an alpha layer
 * to dim the content underneath. By default, the alpha layer is the same View
 * as the touch-interceptor layer. However, for some use-cases, you want a few
 * Views to not be dimmed, but still have touches intercepted (for example,
 * {@link CarouselTab}'s label appears above the alpha layer). In this case, you
 * can specify the View to use as the alpha layer via setAlphaLayer(); in this
 * case you are responsible for managing the z-order of the alpha-layer with
 * respect to your other sub-views. Typically, you would not use this class
 * directly, but rather use another class that uses it, for example
 * {@link FrameLayoutWithOverlay}.
 */
public class AlphaTouchInterceptorOverlay extends FrameLayout {

    private final View mInterceptorLayer;

    private float mAlpha = 0.0f;

    private View mAlphaLayer;

    /**
     * @param context The {@link Context} to use.
     */
    public AlphaTouchInterceptorOverlay(final Context context) {
        super(context);

        mInterceptorLayer = new View(context);
        mInterceptorLayer.setBackgroundColor(0);
        addView(mInterceptorLayer);

        mAlphaLayer = this;
    }

    /**
     * Set the View that the overlay will use as its alpha-layer. If none is set
     * it will use itself. Only necessary to set this if some child views need
     * to appear above the alpha-layer but below the touch-interceptor.
     */
    public void setAlphaLayer(final View alphaLayer) {
        if (mAlphaLayer == alphaLayer) {
            return;
        }

        /* We're no longer the alpha-layer, so make ourself invisible. */
        if (mAlphaLayer == this) {
            setAlphaOnViewBackground(this, 0.0f);
        }

        mAlphaLayer = alphaLayer == null ? this : alphaLayer;
        setAlphaLayerValue(mAlpha);
    }

    /** Sets the alpha value on the alpha layer. */
    public void setAlphaLayerValue(final float alpha) {
        mAlpha = alpha;
        if (mAlphaLayer != null) {
            setAlphaOnViewBackground(mAlphaLayer, mAlpha);
        }
    }

    /** Delegate to interceptor-layer. */
    public void setOverlayOnClickListener(final OnClickListener listener) {
        mInterceptorLayer.setOnClickListener(listener);
    }

    /** Delegate to interceptor-layer. */
    public void setOverlayClickable(final boolean clickable) {
        mInterceptorLayer.setClickable(clickable);
    }

    /**
     * Sets an alpha value on the view.
     */
    public static void setAlphaOnViewBackground(final View view, final float alpha) {
        if (view != null) {
            view.setBackgroundColor((int)(clamp(alpha, 0.0f, 1.0f) * 255) << 24);
        }
    }

    /**
     * If the input value lies outside of the specified range, return the nearer
     * bound. Otherwise, return the input value, unchanged.
     */
    public static float clamp(final float input, final float lowerBound, final float upperBound) {
        if (input < lowerBound) {
            return lowerBound;
        } else if (input > upperBound) {
            return upperBound;
        }
        return input;
    }

}
