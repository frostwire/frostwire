/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.frostwire.util.Ref;

/**
 * 
 * @author gubatron
 * @author aldenml
 * 
 */
public abstract class ContextAdapter extends BaseAdapter {

    private final WeakReference<Context> ctxRef;

    public ContextAdapter(Context ctx) {
        this.ctxRef = Ref.weak(ctx);
    }

    @Override
    public final View getView(int position, View convertView, ViewGroup parent) {
        return Ref.alive(ctxRef) ? getView(ctxRef.get(), position, convertView, parent) : null;
    }

    public abstract View getView(Context ctx, int position, View convertView, ViewGroup parent);
}
