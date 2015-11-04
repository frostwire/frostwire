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

package com.frostwire.android.gui.dialogs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.ClickAdapter;
import com.frostwire.android.gui.views.ContextAdapter;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public final class MenuDialog extends AbstractDialog {

    private static final String TAG = "menu_dialog";

    private static final String ID_KEY = "id";
    private static final String ITEMS_KEY = "items";

    private String id;
    private ArrayList<MenuItem> items;

    public MenuDialog() {
        super(TAG, 0);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();

        id = args.getString(ID_KEY);

        items = getArgument(ITEMS_KEY);

        Context ctx = getActivity();

        MenuAdapter adapter = new MenuAdapter(ctx, items);

        return new AlertDialog.Builder(ctx).setAdapter(adapter, new AdapterListener(this)).create();
    }

    public static MenuDialog newInstance(String id, List<MenuItem> items) {
        MenuDialog f = new MenuDialog();

        Bundle args = new Bundle();
        args.putString(ID_KEY, id);
        args.putSerializable(ITEMS_KEY, new ArrayList<MenuItem>(items));
        f.setArguments(args);

        return f;
    }

    @Override
    protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
    }
    
    @Override
    protected void performDialogClick(String tag, int which) {
        super.performDialogClick(id, which);
    }

    public static class MenuItem implements Serializable {

        private final int id;
        private final int textRestId;
        private final int drawableResId;

        public MenuItem(int id, int textRestId, int drawableResId) {
            this.id = id;
            this.textRestId = textRestId;
            this.drawableResId = drawableResId;
        }

        public int getId() {
            return id;
        }

        public int getTextResId() {
            return textRestId;
        }

        public int getDrawableResId() {
            return drawableResId;
        }
    }

    private static final class MenuAdapter extends ContextAdapter {

        private final List<MenuItem> items;

        public MenuAdapter(Context ctx, List<MenuItem> items) {
            super(ctx);
            this.items = items;
        }

        @Override
        public View getView(Context ctx, int position, View convertView, ViewGroup parent) {
            MenuItem item = getItem(position);

            if (convertView == null) {
                convertView = View.inflate(ctx, R.layout.view_menu_list_item, null);
            }

            TextView textView = (TextView) convertView;

            textView.setTag(item);
            textView.setText(item.getTextResId());

            textView.setCompoundDrawablesWithIntrinsicBounds(item.getDrawableResId(), 0, 0, 0);

            return convertView;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public MenuItem getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }

    private static final class AdapterListener extends ClickAdapter<MenuDialog> {

        public AdapterListener(MenuDialog owner) {
            super(owner);
        }

        @Override
        public void onClick(MenuDialog owner, DialogInterface dialog, int which) {
            MenuItem item = owner.items.get(which);
            owner.performDialogClick(item.id);
            owner.dismiss();
        }
    }
}
