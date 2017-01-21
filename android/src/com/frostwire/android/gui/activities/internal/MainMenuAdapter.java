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

package com.frostwire.android.gui.activities.internal;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractAdapter;

/**
 * @author gubatron
 * @author aldenml
 */
public final class MainMenuAdapter extends AbstractAdapter<MainMenuAdapter.MenuItem> {

    public MainMenuAdapter(Context context) {
        super(context, R.layout.slidemenu_listitem);

        addItems();
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).itemId();
    }

    @Override
    protected void setupView(View view, ViewGroup parent, MenuItem item) {
        TextView label = findView(view, R.id.slidemenu_listitem_label);
        ImageView icon = findView(view, R.id.slidemenu_listitem_icon);

        label.setText(item.titleResId());

        if (((Checkable) view).isChecked()) {
            icon.setImageResource(getOverIcon(item));
            view.setBackgroundResource(R.drawable.slidemenu_listitem_background_selected);
        } else {
            icon.setImageResource(item.iconResId());
            view.setBackgroundResource(android.R.color.transparent);
        }
    }

    private void addItems() {
        add(new MenuItem(R.id.menu_main_search, R.string.search, R.drawable.menu_icon_search));
        add(new MenuItem(R.id.menu_main_my_music, R.string.my_music, R.drawable.menu_icon_my_music));
        add(new MenuItem(R.id.menu_main_library, R.string.my_files, R.drawable.menu_icon_library));
        add(new MenuItem(R.id.menu_main_transfers, R.string.transfers, R.drawable.menu_icon_transfers));
        add(new MenuItem(R.id.menu_main_support, R.string.help, R.drawable.menu_icon_support));
        add(new MenuItem(R.id.menu_main_settings, R.string.settings, R.drawable.menu_icon_settings));
        // comment this line if release is necessary
        //add(new MenuItem(R.id.menu_main_settings2, R.string.settings, R.drawable.menu_icon_settings));
        add(new MenuItem(R.id.menu_main_shutdown, R.string.exit, R.drawable.menu_icon_exit));
    }

    private int getOverIcon(MenuItem item) {
        switch (item.itemId()) {
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
            case R.id.menu_main_settings2:
                return R.drawable.menu_icon_settings_over;
            case R.id.menu_main_shutdown:
                return R.drawable.menu_icon_exit_over;
            default:
                throw new IllegalArgumentException("Item id not supported");
        }
    }

    public static final class MenuItem {

        private int itemId;
        private int titleResId;
        private int iconResId;

        public MenuItem(int itemId, int titleResId, int iconResId) {
            this.itemId = itemId;
            this.titleResId = titleResId;
            this.iconResId = iconResId;
        }

        public int itemId() {
            return itemId;
        }

        public int titleResId() {
            return titleResId;
        }

        public int iconResId() {
            return iconResId;
        }
    }
}
