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
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class RichNotificationActionLink {
    private final String text;
    private final ClickAdapter clickAdapter;
    private final WeakReference<Context> contextReference;

    public RichNotificationActionLink(Context context, String text, ClickAdapter clickAdapter) {
        this.contextReference = Ref.weak(context);
        this.text = text;
        this.clickAdapter = clickAdapter;
    }

    public String getText() {
        return text;
    }

    public ClickAdapter getClickAdapter() {
        return this.clickAdapter;
    }

    public View getView() {
        View result = null;
        if (Ref.alive(contextReference)) {
            TextView tv = new TextView(contextReference.get());
            tv.setClickable(true);
            tv.setLinksClickable(true);
            tv.setAutoLinkMask(Linkify.WEB_URLS);
            SpannableString text = new SpannableString(getText());
            text.setSpan(new UnderlineSpan(), 0, text.length(), 0);
            tv.setText(text);
            tv.setTextColor(contextReference.get().getResources().getColor(R.color.basic_blue));
            tv.setOnClickListener(getClickAdapter());
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.0f);
            result = tv;
        }

        return result;
    }
}
