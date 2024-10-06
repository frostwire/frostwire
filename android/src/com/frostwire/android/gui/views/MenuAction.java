/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public abstract class MenuAction {

    private final WeakReference<Context> contextRef;
    private final Drawable image;
    private final String text;

    public MenuAction(Context context, int imageId, String text, int tintColor) {
        this.contextRef = new WeakReference<>(context);

        Drawable drawable = ContextCompat.getDrawable(context, imageId);
        if (drawable != null) {
            drawable = DrawableCompat.wrap(drawable).mutate();
            DrawableCompat.setTint(drawable, tintColor);
        }
        this.image = drawable;
        this.text = text;
    }

    public MenuAction(Context context, int imageId, int textId, int tintColor) {
        this(context, imageId, context.getResources().getString(textId), tintColor);
    }

    public MenuAction(Context context, int imageId, int textId, int tintColor, Object... formatArgs) {
        this(context, imageId, context.getResources().getString(textId, formatArgs), tintColor);
    }

    public String getText() {
        return text;
    }

    public Drawable getImage() {
        return image;
    }

    public final void onClick() {
        Context context = contextRef.get();
        if (context != null) {
            onClick(context);
        }
    }

    public Context getContext() {
        return contextRef.get();
    }

    public abstract void onClick(Context context);
}