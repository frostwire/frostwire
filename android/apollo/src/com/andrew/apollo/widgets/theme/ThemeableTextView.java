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

package com.andrew.apollo.widgets.theme;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.TextView;

import com.frostwire.android.R;
import com.andrew.apollo.utils.ThemeUtils;

import java.util.WeakHashMap;

/**
 * A custom {@link TextView} that is made themeable for developers. It allows a
 * custom font and color to be set, otherwise functions like normal. Because
 * different text views may required different colors to be set, the resource
 * name each can be set in the XML via the attribute {@value themeResource}.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class ThemeableTextView extends TextView {

    /**
     * @param context The {@link Context} to use
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public ThemeableTextView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        // Initialze the theme resources
        final ThemeUtils resources = new ThemeUtils(context);
        // Retrieve styles attributes
        final TypedArray typedArray = context.obtainStyledAttributes(attrs,
                R.styleable.ThemeableTextView, 0, 0);
        // Get the theme resource name
        final String resourceName = typedArray
                .getString(R.styleable.ThemeableTextView_themeResource);
        // Theme the text color
        if (!TextUtils.isEmpty(resourceName) && !isInEditMode()) {
            setTextColor(resources.getColor(resourceName));
        }
        // Recyle the attrs
        typedArray.recycle();
    }

    /**
     * A small class that holds a weak cache for any typefaces applied to the
     * text.
     */
    public static final class TypefaceCache {

        private static final WeakHashMap<String, Typeface> MAP = new WeakHashMap<String, Typeface>();

        private static TypefaceCache sInstance;

        /**
         * Constructor for <code>TypefaceCache</code>
         */
        public TypefaceCache() {
        }

        /**
         * @return A singleton of {@linkTypefaceCache}.
         */
        public static final TypefaceCache getInstance() {
            if (sInstance == null) {
                sInstance = new TypefaceCache();
            }
            return sInstance;
        }

        /**
         * @param file The name of the type face asset.
         * @param context The {@link Context} to use.
         * @return A new type face.
         */
        public Typeface getTypeface(final String file, final Context context) {
            Typeface result = MAP.get(file);
            if (result == null) {
                result = Typeface.createFromAsset(context.getAssets(), file);
                MAP.put(file, result);
            }
            return result;
        }
    }
}
