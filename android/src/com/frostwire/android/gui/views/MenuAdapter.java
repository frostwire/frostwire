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
        MenuAction item = (MenuAction) getItem(position);

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
