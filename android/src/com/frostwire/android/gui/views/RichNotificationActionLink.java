/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
            tv.setTextColor(contextReference.get().getResources().getColor(R.color.basic_color));
            tv.setOnClickListener(getClickAdapter());
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.0f);
            result = tv;
        }

        return result;
    }
}
