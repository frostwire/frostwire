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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class MenuAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final WeakReference<Context> contextRef;
    private final String title;
    private final List<MenuAction> items;

    public MenuAdapter(Context context, String title, List<MenuAction> items) {
        this.contextRef = new WeakReference<>(context);
        this.inflater = LayoutInflater.from(context);
        this.title = title;
        this.items = items;
    }

    public MenuAdapter(Context context, int titleId, List<MenuAction> items) {
        this(context, context.getResources().getString(titleId), items);
    }

    public Context getContext() {
        Context result = null;
        if (Ref.alive(contextRef)) {
            result = contextRef.get();
        }
        return result;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        MenuAction item = getItem(position);

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.view_menu_list_item, parent, false);
        }

        TextView textView = (TextView) convertView;

        textView.setTag(item);
        textView.setText(item.getText());

        textView.setCompoundDrawablesWithIntrinsicBounds(item.getImage(), null, null, null);

        return convertView;
    }

    public int getCount() {
        return items.size();
    }

    public MenuAction getItem(int position) {
        return items.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    public String getTitle() {
        return title;
    }
}
