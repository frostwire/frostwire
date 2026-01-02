/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.views;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.FragmentManager;

import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
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
        Context context = getContext();
        if (context != null) {
            onClick(context);
        }
    }

    public Context getContext() {
        Context result = null;
        if (Ref.alive(contextRef)) {
            result = contextRef.get();
        }
        return result;
    }

    public AppCompatActivity getAppCompatActivity() {
        Context context = getContext();
        if (context instanceof AppCompatActivity) {
            return (AppCompatActivity) context;
        }
        return null;
    }

    public FragmentManager getFragmentManager() {
        AppCompatActivity activity = getAppCompatActivity();
        if (activity != null) {
            return activity.getSupportFragmentManager();
        }
        return null;
    }

    public abstract void onClick(Context context);
}