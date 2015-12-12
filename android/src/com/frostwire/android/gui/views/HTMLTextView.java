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

import android.content.Context;
import android.text.Html;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * Lazy Googlers didn't add support for HTML at the XML level on <TextView>.
 * @author gubatron
 * @author aldenml
 *
 */
public class HTMLTextView extends TextView {

    public HTMLTextView(Context context) {
        super(context);
        init();
    }

    public HTMLTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HTMLTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        String textString = getText().toString();
        String textStringLowered = textString.toLowerCase();
        String prefix = (textStringLowered.startsWith("<html>")) ? "" : "<html>";
        String suffix = (textStringLowered.endsWith("</html>")) ? "" : "</html>";
        String htmlText = prefix + textString + suffix;
        setText(Html.fromHtml(htmlText));
    }
}
