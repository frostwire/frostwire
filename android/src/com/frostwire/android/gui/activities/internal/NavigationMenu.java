/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.SoftwareUpdater;
import com.frostwire.android.gui.activities.BuyActivity;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AdMenuItemView;
import com.frostwire.android.offers.Offers;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *
 * Created on 02/23/2017
 */

public final class NavigationMenu {
    private final MainController controller;
    private final NavigationView navView;
    private final DrawerLayout drawerLayout;
    private final ActionBarDrawerToggle drawerToggle;
    private final AdMenuItemView menuRemoveAdsItem;
    private int checkedNavViewMenuItemId = -1;

    public NavigationMenu(MainController controller, DrawerLayout drawerLayout, Toolbar toolbar) {
        this.controller = controller;
        this.drawerLayout = drawerLayout;
        MainActivity mainActivity = controller.getActivity();
        drawerToggle = new MenuDrawerToggle(controller, drawerLayout, toolbar);
        this.drawerLayout.addDrawerListener(drawerToggle);
        navView = initNavigationView(mainActivity);
        menuRemoveAdsItem = initAdMenuItemListener(mainActivity);
        refreshMenuRemoveAdsItem();
    }

    public boolean isOpen() {
        return drawerLayout.isDrawerOpen(navView);
    }

    public void show() {
        drawerLayout.openDrawer(navView);
    }

    public void hide() {
        drawerLayout.closeDrawer(navView);
    }

    public void onConfigurationChanged(Configuration newConfig) {
        drawerToggle.onConfigurationChanged(newConfig);
    }

    public void syncState() {
        drawerToggle.syncState();
    }

    public void updateCheckedItem(int menuItemId) {
        navView.setCheckedItem(menuItemId);
    }

    private NavigationView initNavigationView(final Activity activity) {
        NavigationView resultNavView = navView;
        if (navView == null) {
            resultNavView = (NavigationView) activity.findViewById(R.id.activity_main_nav_view);
            resultNavView.setNavigationItemSelectedListener(
                    new NavigationView.OnNavigationItemSelectedListener() {
                        @Override
                        public boolean onNavigationItemSelected(MenuItem menuItem) {
                            onMenuItemSelected(menuItem);
                            return true;
                        }
                    });
            View navViewHeader = resultNavView.getHeaderView(0);
            // Prep title and version
            TextView title = (TextView) navViewHeader.findViewById(R.id.nav_view_header_main_title);
            String basicOrPlus = (String) activity.getText(Constants.IS_GOOGLE_PLAY_DISTRIBUTION ? R.string.basic : R.string.plus);
            title.setText(activity.getText(R.string.application_label) + " " + basicOrPlus + " v" + Constants.FROSTWIRE_VERSION_STRING);
            TextView build = (TextView) navViewHeader.findViewById(R.id.nav_view_header_main_build);
            build.setText(activity.getText(R.string.build) + " " + BuildConfig.VERSION_CODE);
            // Prep update button
            ImageView updateButton = (ImageView) navViewHeader.findViewById(R.id.nav_view_header_main_update);
            updateButton.setVisibility(View.GONE);
            updateButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onUpdateButtonClicked(activity);
                }
            });
        }
        return resultNavView;
    }

    private void onMenuItemSelected(MenuItem menuItem) {
        if (controller.getActivity() == null) {
            return;
        }
        checkedNavViewMenuItemId = menuItem.getItemId();
        Engine.instance().getVibrator().hapticFeedback();
        controller.syncNavigationMenu();
        menuItem.setChecked(true);
        controller.setTitle(menuItem.getTitle());

        Fragment fragment = controller.getFragmentByNavMenuId(menuItem.getItemId());
        if (fragment != null) {
            controller.switchContent(fragment);
        } else {
            switch (menuItem.getItemId()) {
                case R.id.menu_main_my_music:
                    controller.launchMyMusic();
                    break;
                case R.id.menu_main_support:
                    UIUtils.openURL(controller.getActivity(), Constants.SUPPORT_URL);
                    break;
                case R.id.menu_main_settings:
                    controller.showPreferences();
                    break;
                case R.id.menu_main_shutdown:
                    controller.showShutdownDialog();
                    break;
                default:
                    break;
            }
        }
        hide();
    }

    private void onUpdateButtonClicked(Context context) {
        hide();
        SoftwareUpdater.instance().notifyUserAboutUpdate(context);
    }

    private AdMenuItemView initAdMenuItemListener(final Activity activity) {
        AdMenuItemView adMenuItemView = (AdMenuItemView) activity.findViewById(R.id.slidermenu_ad_menuitem);
        RelativeLayout menuAd = (RelativeLayout) activity.findViewById(R.id.view_ad_menu_item_ad);
        menuAd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(activity, BuyActivity.class);
                activity.startActivity(intent);
            }
        });
        return adMenuItemView;
    }

    private void refreshMenuRemoveAdsItem() {
        // only visible for basic or debug build and if ads have not been disabled.
        int visibility = ((Constants.IS_GOOGLE_PLAY_DISTRIBUTION || Constants.IS_BASIC_AND_DEBUG) && !Offers.disabledAds()) ?
                View.VISIBLE :
                View.GONE;
        menuRemoveAdsItem.setVisibility(visibility);
    }

    public void onUpdateAvailable() {
        View navViewHeader = navView.getHeaderView(0);
        ImageView updateButton = (ImageView) navViewHeader.findViewById(R.id.nav_view_header_main_update);
        updateButton.setVisibility(View.VISIBLE);
    }

    public MenuItem getCheckedItem() {
        return navView.getMenu().findItem(
                checkedNavViewMenuItemId != -1 ?
                checkedNavViewMenuItemId :
                R.id.menu_main_search);
    }

    public void onOptionsItemSelected(MenuItem item) {
        drawerToggle.onOptionsItemSelected(item);
    }

    private final class MenuDrawerToggle extends ActionBarDrawerToggle {
        private final MainController controller;

        MenuDrawerToggle(MainController controller, DrawerLayout drawerLayout, Toolbar toolbar) {
            super(controller.getActivity(), drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close);
            this.controller = controller;
        }

        @Override
        public void onDrawerClosed(View view) {
            controller.syncNavigationMenu();
        }

        @Override
        public void onDrawerOpened(View drawerView) {
            if (controller.getActivity() != null) {
                UIUtils.hideKeyboardFromActivity(controller.getActivity());
            }
            controller.syncNavigationMenu();
        }

        @Override
        public void onDrawerStateChanged(int newState) {
            NavigationMenu.this.refreshMenuRemoveAdsItem();
            controller.syncNavigationMenu();
        }
    }
}
