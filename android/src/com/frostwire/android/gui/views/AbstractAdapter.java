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

import android.content.Context;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

/**
 * 
 * @author gubatron
 * @author aldenml
 * 
 */
public abstract class AbstractAdapter<T> extends ArrayAdapter<T> {

    private final int layoutResId;

    public AbstractAdapter(Context context, int layoutResId) {
        super(context, 0);
        this.layoutResId = layoutResId;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        T item = getItem(position);
        if (convertView == null) {
            convertView = View.inflate(getContext(), layoutResId, null);
        }
        setupView(convertView, parent, item);
        return convertView;
    }

    @SuppressWarnings("unchecked")
    protected final <V extends View> V findView(View view, int id) {
        return (V) getView(view, getHolder(view), id);
    }

    protected abstract void setupView(View view, ViewGroup parent, T item);

    private SparseArray<View> getHolder(View view) {
        @SuppressWarnings("unchecked")
        SparseArray<View> h = (SparseArray<View>) view.getTag();
        if (h == null) {
            h = new SparseArray<>();
            view.setTag(h);
        }
        return h;
    }

    private View getView(View view, SparseArray<View> h, int id) {
        View v;

        int index = h.indexOfKey(id);
        if (index < 0) {
            v = view.findViewById(id);
            h.put(id, v);
        } else {
            v = h.valueAt(index);
        }

        return v;
    }

    public static abstract class OnItemClickAdapter<T> implements OnItemClickListener {

        @Override
        public final void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            try {
                @SuppressWarnings("unchecked")
                AbstractAdapter<T> adapter = (AbstractAdapter<T>) parent.getAdapter();
                onItemClick(parent, view, adapter, position, id);
            } catch (ClassCastException e) {
                // ignore
            }
        }

        public abstract void onItemClick(AdapterView<?> parent, View view, AbstractAdapter<T> adapter, int position, long id);
    }
}
