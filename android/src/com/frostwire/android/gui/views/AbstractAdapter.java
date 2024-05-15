/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter
 * Copyright (c) 2011-2024, FrostWire(R). All rights reserved.
 *
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
