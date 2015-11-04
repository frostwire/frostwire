/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(TM). All rights reserved.
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

import java.lang.ref.WeakReference;

import android.content.Context;
import android.graphics.drawable.Drawable;
import com.frostwire.util.Ref;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public abstract class MenuAction {

    private final WeakReference<Context> contextRef;
    private final Drawable image;
    private final String text;

    public MenuAction(Context context, Drawable image, String text) {
        this.contextRef = new WeakReference<Context>(context);
        this.image = image;
        this.text = text;
    }

    public MenuAction(Context context, int imageId, String text) {
        this(context, context.getResources().getDrawable(imageId), text);
    }

    public MenuAction(Context context, int imageId, int textId) {
        this(context, context.getResources().getDrawable(imageId), context.getResources().getString(textId));
    }

    public MenuAction(Context context, int imageId, int textId, Object... formatArgs) {
        this(context, imageId, context.getResources().getString(textId, formatArgs));
    }

    public String getText() {
        return text;
    }

    public Drawable getImage() {
        return image;
    }

    public final void onClick() {
        if (contextRef.get() != null) {
            onClick(contextRef.get());
        }
    }

    public Context getContext() {
        Context result = null;
        if (Ref.alive(contextRef)) {
            result = contextRef.get();
        }
        return result;
    }

    protected abstract void onClick(Context context);
}