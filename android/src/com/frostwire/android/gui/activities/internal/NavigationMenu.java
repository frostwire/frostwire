/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.activities.internal;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.SoftwareUpdater;
import com.frostwire.android.gui.activities.AboutActivity;
import com.frostwire.android.gui.activities.BuyActivity;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.fragments.TransfersFragment;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AdMenuItemView;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;
import com.google.android.material.navigation.NavigationView;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 * <p>
 * Created on 02/23/2017
 */

public final class NavigationMenu {
    private final static Logger LOG = Logger.getLogger(NavigationMenu.class);
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
        menuRemoveAdsItem = initAdRemovalMenuItemListener(mainActivity);
        refreshMenuRemoveAdsItem();
    }

    public boolean isOpen() {
        SystemUtils.ensureUIThreadOrCrash("NavigationMenu::isOpen");
        return drawerLayout.isDrawerOpen(navView);
    }

    public void show() {
        SystemUtils.ensureUIThreadOrCrash("NavigationMenu::show");
        drawerLayout.openDrawer(navView);
    }

    public void hide() {
        SystemUtils.ensureUIThreadOrCrash("NavigationMenu::hide");
        drawerLayout.closeDrawer(navView);
    }

    public void onConfigurationChanged(Configuration newConfig) {
        SystemUtils.ensureUIThreadOrCrash("NavigationMenu::onConfigurationChanged");
        drawerToggle.onConfigurationChanged(newConfig);
    }

    public void syncState() {
        SystemUtils.ensureUIThreadOrCrash("NavigationMenu::syncState");
        drawerToggle.syncState();
    }

    public void updateCheckedItem(int menuItemId) {
        SystemUtils.ensureUIThreadOrCrash("NavigationMenu::updateCheckedItem");
        navView.setCheckedItem(menuItemId);
    }

    private NavigationView initNavigationView(final MainActivity activity) {
        SystemUtils.ensureUIThreadOrCrash("NavigationMenu::initNavigationView");
        NavigationView resultNavView = navView;
        if (navView == null) {
            resultNavView = activity.findViewById(R.id.activity_main_nav_view);
            resultNavView.setNavigationItemSelectedListener(
                    menuItem -> {
                        onMenuItemSelected(menuItem);
                        return true;
                    });
            View navViewHeader = resultNavView.getHeaderView(0);
            // Logo
            ImageView navLogo = navViewHeader.findViewById(R.id.nav_view_header_main_app_logo);
            navLogo.setOnClickListener(v -> UIUtils.openURL(v.getContext(), Constants.FROSTWIRE_GIVE_URL));

            // Prep title and version
            TextView title = navViewHeader.findViewById(R.id.nav_view_header_main_title);
            TextView version = navViewHeader.findViewById(R.id.nav_view_header_main_version);
            String basicOrPlus = (String) activity.getText(Constants.IS_GOOGLE_PLAY_DISTRIBUTION ? R.string.basic : R.string.plus);
            boolean isDevelopment = Constants.IS_BASIC_AND_DEBUG;
            if (isDevelopment) {
                basicOrPlus = "Developer";
            }
            title.setText("FrostWire " + basicOrPlus);
            version.setText(" v" + Constants.FROSTWIRE_VERSION_STRING);
            TextView build = navViewHeader.findViewById(R.id.nav_view_header_main_build);
            build.setText(activity.getText(R.string.build) + " " + BuildConfig.VERSION_CODE);
            View.OnClickListener aboutActivityLauncher = v -> {
                Intent intent = new Intent(v.getContext(), AboutActivity.class);
                v.getContext().startActivity(intent);
            };
            title.setOnClickListener(aboutActivityLauncher);
            version.setOnClickListener(aboutActivityLauncher);
            build.setOnClickListener(aboutActivityLauncher);

            // Prep update button
            ImageView updateButton = navViewHeader.findViewById(R.id.nav_view_header_main_update);
            updateButton.setVisibility(View.GONE);
            updateButton.setOnClickListener(v -> onUpdateButtonClicked(activity));
        }
        return resultNavView;
    }

    private void onMenuItemSelected(MenuItem menuItem) {
        SystemUtils.ensureUIThreadOrCrash("NavigationMenu::onMenuItemSelected");
        if (controller.getActivity() == null) {
            return;
        }
        checkedNavViewMenuItemId = menuItem.getItemId();
        controller.syncNavigationMenu();
        menuItem.setChecked(true);
        controller.setTitle(menuItem.getTitle());
        int menuActionId = menuItem.getItemId();

        Fragment fragment = controller.getFragmentByNavMenuId(menuItem.getItemId());
        if (fragment != null) {
            controller.switchContent(fragment);
        } else {
            if (menuActionId == R.id.menu_main_my_music) {
                controller.launchMyMusic();
            } else if (menuActionId == R.id.menu_downloads) {
                controller.openOSFileExplorer();
            } else if (menuActionId == R.id.menu_main_transfers) {
                controller.showTransfers(TransfersFragment.TransferStatus.ALL);
            } else if (menuActionId == R.id.menu_main_support) {
                UIUtils.openURL(controller.getActivity(), Constants.SUPPORT_URL);
            } else if (menuActionId == R.id.menu_main_settings) {
                controller.showPreferences();
            } else if (menuActionId == R.id.menu_main_shutdown) {
                controller.showShutdownDialog();
            }
        }

        hide();
    }

    private void onUpdateButtonClicked(MainActivity mainActivity) {
        SystemUtils.ensureUIThreadOrCrash("NavigationMenu::onUpdateButtonClicked");
        hide();
        SoftwareUpdater.getInstance().notifyUserAboutUpdate(mainActivity);
    }

    private AdMenuItemView initAdRemovalMenuItemListener(final AppCompatActivity activity) {
        SystemUtils.ensureUIThreadOrCrash("NavigationMenu::initAdRemovalMenuItemListener");
        AdMenuItemView adMenuItemView = activity.findViewById(R.id.slidermenu_ad_menuitem);
        RelativeLayout menuAd = activity.findViewById(R.id.view_ad_menu_item_ad);
        menuAd.setOnClickListener(v -> {
            Intent intent = new Intent(activity, BuyActivity.class);
            activity.startActivity(intent);
        });
        return adMenuItemView;
    }

    private void refreshMenuRemoveAdsItem() {
        SystemUtils.ensureUIThreadOrCrash("NavigationMenu::refreshMenuRemoveAdsItem");
        // only visible for basic or debug build and if ads have not been disabled.

        // visible if ads are not paused
        int visibility = Offers.disabledAds() ? View.GONE : View.VISIBLE;
        SystemUtils.postToUIThread(() -> {
            try {
                menuRemoveAdsItem.setVisibility(visibility);
            } catch (Throwable t) {
                if (BuildConfig.DEBUG) {
                    throw t;
                }
                LOG.error("NavigationMenu::refreshMenuRemoveAdsItem() error posting menuRemoveAdsItem.setVisibility(...) to main looper: " + t.getMessage(), t);
            }
        });
    }

    public void onUpdateAvailable() {
        SystemUtils.ensureUIThreadOrCrash("NavigationMenu::onUpdateAvailable");
        View navViewHeader = navView.getHeaderView(0);
        ImageView updateButton = navViewHeader.findViewById(R.id.nav_view_header_main_update);
        updateButton.setVisibility(View.VISIBLE);
    }

    public MenuItem getCheckedItem() {
        SystemUtils.ensureUIThreadOrCrash("NavigationMenu::getCheckedItem");
        return navView.getMenu().findItem(
                checkedNavViewMenuItemId != -1 ?
                        checkedNavViewMenuItemId :
                        R.id.menu_main_search);
    }

    public void onOptionsItemSelected(MenuItem item) {
        SystemUtils.ensureUIThreadOrCrash("NavigationMenu::onOptionsItemSelected");
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
            SystemUtils.ensureUIThreadOrCrash("NavigationMenu::MenuDrawerToggle::onDrawerOpened");
            refreshMenuRemoveAdsItem();
            if (controller.getActivity() != null) {
                UIUtils.hideKeyboardFromActivity(controller.getActivity());
            }
            controller.syncNavigationMenu();
        }

        @Override
        public void onDrawerStateChanged(int newState) {
            SystemUtils.ensureUIThreadOrCrash("NavigationMenu::MenuDrawerToggle::onDrawerStateChanged");
            refreshMenuRemoveAdsItem();
            controller.syncNavigationMenu();
        }
    }
}
