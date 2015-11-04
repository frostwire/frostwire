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
import android.util.AttributeSet;

import com.andrew.apollo.utils.ThemeUtils;
import com.viewpagerindicator.TitlePageIndicator;

/**
 * This is a custom {@link TitlePageIndicator} that is made themeable by
 * allowing developers to choose the background and the selected and unselected
 * text colors.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class ThemeableTitlePageIndicator extends TitlePageIndicator {

    /**
     * Resource name used to theme the background
     */
    private static final String BACKGROUND = "tpi_background";

    /**
     * Resource name used to theme the selected text color
     */
    private static final String SELECTED_TEXT = "tpi_selected_text_color";

    /**
     * Resource name used to theme the unselected text color
     */
    private static final String TEXT = "tpi_unselected_text_color";

    /**
     * Resource name used to theme the footer color
     */
    private static final String FOOTER = "tpi_footer_color";

    /**
     * @param context The {@link Context} to use
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    @SuppressWarnings("deprecation")
    public ThemeableTitlePageIndicator(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        // Initialze the theme resources
        final ThemeUtils resources = new ThemeUtils(context);
        // Theme the background
        setBackgroundDrawable(resources.getDrawable(BACKGROUND));
        // Theme the selected text color
        setSelectedColor(resources.getColor(SELECTED_TEXT));
        // Theme the unselected text color
        setTextColor(resources.getColor(TEXT));
        // Theme the footer
        setFooterColor(resources.getColor(FOOTER));
    }
}
