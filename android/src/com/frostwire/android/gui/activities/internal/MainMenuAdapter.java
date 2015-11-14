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

package com.frostwire.android.gui.activities.internal;

import android.content.Context;
import android.view.*;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractAdapter;
import com.frostwire.android.gui.views.menu.MenuBuilder;

/**
 * @author gubatron
 * @author aldenml
 */
public final class MainMenuAdapter extends AbstractAdapter<MenuItem> {

    public MainMenuAdapter(Context context) {
        super(context, R.layout.slidemenu_listitem);

        addItems(context);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getItemId();
    }

    @Override
    protected void setupView(View view, ViewGroup parent, MenuItem item) {
        TextView label = findView(view, R.id.slidemenu_listitem_label);
        ImageView icon = findView(view, R.id.slidemenu_listitem_icon);

        label.setText(item.getTitle());

        if (((Checkable) view).isChecked()) {
            icon.setImageResource(getOverIcon(item));
            view.setBackgroundResource(R.drawable.slidemenu_listitem_background_selected);
        } else {
            icon.setImageDrawable(item.getIcon());
            view.setBackgroundResource(android.R.color.transparent);
        }
    }

    private void addItems(Context context) {
        MenuInflater menuInflater = new MenuInflater(context);
        Menu menu = new MenuBuilder(context);
        menuInflater.inflate(R.menu.main, menu);

        for (int i = 0; i < menu.size(); i++) {
            add(menu.getItem(i));
        }
    }

    private int getOverIcon(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_main_search:
                return R.drawable.menu_icon_search_over;
            case R.id.menu_main_my_music:
                return R.drawable.menu_icon_my_music_over;
            case R.id.menu_main_library:
                return R.drawable.menu_icon_library_over;
            case R.id.menu_main_transfers:
                return R.drawable.menu_icon_transfers_over;
            case R.id.menu_main_support:
                return R.drawable.menu_icon_support_over;
            case R.id.menu_main_settings:
                return R.drawable.menu_icon_settings_over;
            case R.id.menu_main_shutdown:
                return R.drawable.menu_icon_exit_over;
            default:
                return 0;
        }
    }
}
