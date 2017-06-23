/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.android.gui.activities;

import android.app.ActionBar;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.Toolbar;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;

import com.andrew.apollo.IApolloService;
import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.MusicUtils.ServiceToken;
import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.R;
import com.frostwire.android.StoragePicker;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.SoftwareUpdater;
import com.frostwire.android.gui.SoftwareUpdater.ConfigurationUpdateListener;
import com.frostwire.android.gui.activities.internal.MainController;
import com.frostwire.android.gui.activities.internal.NavigationMenu;
import com.frostwire.android.gui.dialogs.HandpickedTorrentDownloadDialogOnFetch;
import com.frostwire.android.gui.dialogs.NewTransferDialog;
import com.frostwire.android.gui.dialogs.SDPermissionDialog;
import com.frostwire.android.gui.dialogs.YesNoDialog;
import com.frostwire.android.gui.fragments.BrowsePeerFragment;
import com.frostwire.android.gui.fragments.MainFragment;
import com.frostwire.android.gui.fragments.SearchFragment;
import com.frostwire.android.gui.fragments.TransfersFragment;
import com.frostwire.android.gui.fragments.TransfersFragment.TransferStatus;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.services.EngineService;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.DangerousPermissionsChecker;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.AbstractDialog.OnDialogClickListener;
import com.frostwire.android.gui.views.MiniPlayerView;
import com.frostwire.android.gui.views.TimerService;
import com.frostwire.android.gui.views.TimerSubscription;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.platform.Platforms;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.StringUtils;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Stack;

import static com.andrew.apollo.utils.MusicUtils.musicPlaybackService;

/**
 * @author gubatron
 * @author aldenml
 */
public class MainActivity extends AbstractActivity implements ConfigurationUpdateListener,
        OnDialogClickListener,
        ServiceConnection,
        ActivityCompat.OnRequestPermissionsResultCallback {
    public static final int PROMO_VIDEO_PREVIEW_RESULT_CODE = 100;

    private static final Logger LOG = Logger.getLogger(MainActivity.class);
    private static final String FRAGMENTS_STACK_KEY = "fragments_stack";
    private static final String CURRENT_FRAGMENT_KEY = "current_fragment";
    private static final String LAST_BACK_DIALOG_ID = "last_back_dialog";
    private static final String SHUTDOWN_DIALOG_ID = "shutdown_dialog";
    private static boolean firstTime = true;

    private final SparseArray<DangerousPermissionsChecker> permissionsCheckers;
    private final MainController controller;

    private NavigationMenu navigationMenu;
    private SearchFragment search;
    private BrowsePeerFragment library;
    private TransfersFragment transfers;

    private Fragment currentFragment;
    private final Stack<Integer> fragmentsStack;
    private TimerSubscription playerSubscription;
    private BroadcastReceiver mainBroadcastReceiver;
    private boolean externalStoragePermissionsRequested = false;

    public MainActivity() {
        super(R.layout.activity_main);
        this.controller = new MainController(this);
        this.fragmentsStack = new Stack<>();
        this.permissionsCheckers = initPermissionsCheckers();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            if (!(getCurrentFragment() instanceof SearchFragment)) {
                controller.switchFragment(R.id.menu_main_search);
            }
        } else if (keyCode == KeyEvent.KEYCODE_MENU) {
            toggleDrawer();
        } else {
            return super.onKeyDown(keyCode, event);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (navigationMenu.isOpen()) {
            navigationMenu.hide();
        } else if (fragmentsStack.size() > 1) {
            try {
                fragmentsStack.pop();
                int id = fragmentsStack.peek();
                Fragment fragment = getFragmentManager().findFragmentById(id);
                switchContent(fragment, false);
            } catch (Throwable e) {
                // don't break the app
                showLastBackDialog();
            }
        } else {
            showLastBackDialog();
        }

        syncNavigationMenu();
        updateHeader(getCurrentFragment());
    }

    public void onConfigurationUpdate(boolean frostWireUpdateAvailable) {
        updateNavigationMenu(frostWireUpdateAvailable);
    }

    public void shutdown() {
        Offers.stopAdNetworks(this);
        //UXStats.instance().flush(true); // sends data and ends 3rd party APIs sessions.
        finish();
        Engine.instance().shutdown();
        MusicUtils.requestMusicPlaybackServiceShutdown(this);
        SystemUtils.requestKillProcess(this);
    }

    @Override
    public void finish() {
        if (Build.VERSION.SDK_INT >= 21) {
            finishAndRemoveTaskViaReflection();
        } else {
            super.finish();
        }
    }

    private void finishAndRemoveTaskViaReflection() {
        final Class<? extends MainActivity> clazz = getClass();
        try {
            final Method finishAndRemoveTaskMethod = clazz.getMethod("finishAndRemoveTask");
            if (finishAndRemoveTaskMethod != null) {
                finishAndRemoveTaskMethod.invoke(this);
            }
        } catch (Throwable e) {
            e.printStackTrace();
            super.finish();
        }
    }

    private boolean isShutdown() {
        return isShutdown(null);
    }

    private boolean isShutdown(Intent intent) {
        if (intent == null) {
            intent = getIntent();
        }
        boolean result = intent != null && intent.getBooleanExtra("shutdown-" + ConfigurationManager.instance().getUUIDString(), false);
        if (result) {
            shutdown();
        }
        return result;
    }

    private boolean isGoHome(Intent intent) {
        if (intent == null) {
            intent = getIntent();
        }
        return intent != null && intent.getBooleanExtra("gohome-" + ConfigurationManager.instance().getUUIDString(), false);
    }

    @Override
    protected void initComponents(Bundle savedInstanceState) {
        if (isShutdown()) {
            return;
        }
        updateNavigationMenu(false);
        setupFragments();
        setupInitialFragment(savedInstanceState);
        playerSubscription = TimerService.subscribe(((MiniPlayerView) findView(R.id.activity_main_player_notifier)).getRefresher(), 1);
        onNewIntent(getIntent());
        SoftwareUpdater.instance().addConfigurationUpdateListener(this);
        setupActionBar();
    }

    private void updateNavigationMenu(boolean updateAvailable) {
        if (navigationMenu == null) {
            setupDrawer();
        }
        if (updateAvailable) {
            navigationMenu.onUpdateAvailable();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (intent == null || isShutdown(intent)) {
            return;
        }
        if (isGoHome(intent)) {
            finish();
            return;
        }
        String action = intent.getAction();
        if (action != null) {
            switch (action) {
                case Constants.ACTION_SHOW_TRANSFERS:
                    intent.setAction(null);
                    controller.showTransfers(TransferStatus.ALL);
                    break;
                case Intent.ACTION_VIEW:
                    openTorrentUrl(intent);
                    break;
                case Constants.ACTION_START_TRANSFER_FROM_PREVIEW:
                    if (Ref.alive(NewTransferDialog.srRef)) {
                        SearchFragment.startDownload(this, NewTransferDialog.srRef.get(), getString(R.string.download_added_to_queue));
                        UXStats.instance().log(UXAction.DOWNLOAD_CLOUD_FILE_FROM_PREVIEW);
                    }
                    break;
                case Constants.ACTION_REQUEST_SHUTDOWN:
                    UXStats.instance().log(UXAction.MISC_NOTIFICATION_EXIT);
                    showShutdownDialog();
                    break;
            }
        }
        if (intent.hasExtra(Constants.EXTRA_DOWNLOAD_COMPLETE_NOTIFICATION)) {
            controller.showTransfers(TransferStatus.COMPLETED);
            TransferManager.instance().clearDownloadsToReview();
            try {
                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(Constants.NOTIFICATION_DOWNLOAD_TRANSFER_FINISHED);
                Bundle extras = intent.getExtras();
                String downloadCompletePath = extras.getString(Constants.EXTRA_DOWNLOAD_COMPLETE_PATH);
                if (downloadCompletePath != null) {
                    File file = new File(downloadCompletePath);
                    if (file.isFile()) {
                        UIUtils.openFile(this, file.getAbsoluteFile());
                    }
                }
            } catch (Throwable e) {
                LOG.warn("Error handling download complete notification", e);
            }
        }
        if (intent.hasExtra(Constants.EXTRA_FINISH_MAIN_ACTIVITY)) {
            finish();
        }
    }

    private void openTorrentUrl(Intent intent) {
        try {
            //Open a Torrent from a URL or from a local file :), say from Astro File Manager.
            //Show me the transfer tab
            Intent i = new Intent(this, MainActivity.class);
            i.setAction(Constants.ACTION_SHOW_TRANSFERS);
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
            //go!
            final String uri = intent.getDataString();
            intent.setAction(null);
            if (uri != null) {
                if (uri.startsWith("file") ||
                    uri.startsWith("http") ||
                    uri.startsWith("https") ||
                    uri.startsWith("magnet")) {
                    TransferManager.instance().downloadTorrent(uri, new HandpickedTorrentDownloadDialogOnFetch(this));
                } else if (uri.startsWith("content")) {
                    String newUri = saveViewContent(this, Uri.parse(uri), "content-intent.torrent");
                    if (newUri != null) {
                        TransferManager.instance().downloadTorrent(newUri, new HandpickedTorrentDownloadDialogOnFetch(this));
                    }
                }
            } else {
                LOG.warn("MainActivity.onNewIntent(): Couldn't start torrent download from Intent's URI, intent.getDataString() -> null");
                LOG.warn("(maybe URI is coming in another property of the intent object - #fragmentation)");
            }
        } catch (Throwable e) {
            LOG.error("Error opening torrent from intent", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupDrawer();
        if (ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_INITIAL_SETTINGS_COMPLETE)) {
            mainResume();
            Offers.initAdNetworks(this);
        } else if (!isShutdown()) {
            controller.startWizardActivity();
        }
        checkLastSeenVersion();
        registerMainBroadcastReceiver();
        syncNavigationMenu();
        //uncomment to test social links dialog
        //UIUtils.showSocialLinksDialog(this, true, null, "");
        if (ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED)) {
            checkExternalStoragePermissionsOrBindMusicService();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mainBroadcastReceiver != null) {
            try {
                unregisterReceiver(mainBroadcastReceiver);
            } catch (Throwable ignored) {
                //oh well (the api doesn't provide a way to know if it's been registered before,
                //seems like overkill keeping track of these ourselves.)
            }
        }
    }

    private SparseArray<DangerousPermissionsChecker> initPermissionsCheckers() {
        SparseArray<DangerousPermissionsChecker> checkers = new SparseArray<>();

        // EXTERNAL STORAGE ACCESS CHECKER.
        final DangerousPermissionsChecker externalStorageChecker =
                new DangerousPermissionsChecker(this, DangerousPermissionsChecker.EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE);
        //externalStorageChecker.setPermissionsGrantedCallback(() -> {});
        checkers.put(DangerousPermissionsChecker.EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE, externalStorageChecker);

        // COARSE
        final DangerousPermissionsChecker accessCoarseLocationChecker =
                new DangerousPermissionsChecker(this, DangerousPermissionsChecker.ACCESS_COARSE_LOCATION_PERMISSIONS_REQUEST_CODE);
        checkers.put(DangerousPermissionsChecker.ACCESS_COARSE_LOCATION_PERMISSIONS_REQUEST_CODE, accessCoarseLocationChecker);

        // add more permissions checkers if needed...
        return checkers;
    }

    private void registerMainBroadcastReceiver() {
        mainBroadcastReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                if (Constants.ACTION_NOTIFY_SDCARD_MOUNTED.equals(intent.getAction())) {
                    onNotifySdCardMounted();
                }
            }
        };
        IntentFilter bf = new IntentFilter(Constants.ACTION_NOTIFY_SDCARD_MOUNTED);
        registerReceiver(mainBroadcastReceiver, bf);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (outState != null) {
            // MIGHT DO: save checkedNavViewMenuItemId in bundle.
            super.onSaveInstanceState(outState);
            saveLastFragment(outState);
            saveFragmentsStack(outState);
        }
    }

    private ServiceToken mToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_FrostWire);
        super.onCreate(savedInstanceState);

        if (!ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED)) {
            // we are still in the wizard.
            return;
        }

        if (isShutdown()) {
            return;
        }

        checkExternalStoragePermissionsOrBindMusicService();
        checkAccessCoarseLocationPermissions();
    }

    private void checkAccessCoarseLocationPermissions() {
        DangerousPermissionsChecker checker = permissionsCheckers.get(DangerousPermissionsChecker.ACCESS_COARSE_LOCATION_PERMISSIONS_REQUEST_CODE);
        if (checker != null && !checker.hasAskedBefore()) {
            checker.requestPermissions();
            ConfigurationManager.instance().setBoolean(Constants.ASKED_FOR_ACCESS_COARSE_LOCATION_PERMISSIONS, true);
        } else {
            LOG.info("Asked for ACCESS_COARSE_LOCATION before, skipping.");
        }
    }

    private void checkExternalStoragePermissionsOrBindMusicService() {
        DangerousPermissionsChecker checker = permissionsCheckers.get(DangerousPermissionsChecker.EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE);
        if (!externalStoragePermissionsRequested && checker != null && checker.noAccess()) {
            checker.requestPermissions();
            externalStoragePermissionsRequested = true;
        } else if (mToken == null && checker != null && !checker.noAccess()) {
            mToken = MusicUtils.bindToService(this, this);
        }
    }

    private void onNotifySdCardMounted() {
        transfers.initStorageRelatedRichNotifications(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (playerSubscription != null) {
            playerSubscription.unsubscribe();
        }

        //avoid memory leaks when the device is tilted and the menu gets recreated.
        SoftwareUpdater.instance().removeConfigurationUpdateListener(this);

        if (mToken != null) {
            MusicUtils.unbindFromService(mToken);
            mToken = null;
        }

        // necessary unregisters broadcast its internal receivers, avoids leaks.
        Offers.destroyMopubInterstitials(this);
    }

    private void saveLastFragment(Bundle outState) {
        Fragment fragment = getCurrentFragment();
        if (fragment != null) {
            getFragmentManager().putFragment(outState, CURRENT_FRAGMENT_KEY, fragment);
        }
    }

    private void mainResume() {
        checkSDPermission();
        syncNavigationMenu();
        if (firstTime) {
            if (ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY) &&
                    !NetworkManager.instance().isTunnelUp()) {
                UIUtils.showDismissableMessage(findView(R.id.activity_main_parent_layout), R.string.cannot_start_engine_without_vpn);
            } else {
                firstTime = false;
                Engine.instance().startServices(); // it's necessary for the first time after wizard
            }
        }
        SoftwareUpdater.instance().checkForUpdate(this);
    }

    private void checkSDPermission() {
        if (!AndroidPlatform.saf()) {
            return;
        }

        try {
            File data = Platforms.data();
            File parent = data.getParentFile();

            if (!AndroidPlatform.saf(parent)) {
                return;
            }
            if (!Platforms.fileSystem().canWrite(parent) &&
                    !SDPermissionDialog.visible) {
                SDPermissionDialog dlg = SDPermissionDialog.newInstance();
                dlg.show(getFragmentManager());
            }
        } catch (Throwable e) {
            // we can't do anything about this
            LOG.error("Unable to detect if we have SD permissions", e);
        }
    }

    private void handleSDPermissionDialogClick(int which) {
        if (which == Dialog.BUTTON_POSITIVE) {
            StoragePicker.show(this);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == StoragePicker.SELECT_FOLDER_REQUEST_CODE) {
            StoragePicker.handle(this, requestCode, resultCode, data);
        } else if (requestCode == MainActivity.PROMO_VIDEO_PREVIEW_RESULT_CODE) {
            Offers.showInterstitialOfferIfNecessary(this, Offers.PLACEMENT_INTERSTITIAL_EXIT, false, false);
        }
        if (!DangerousPermissionsChecker.handleOnWriteSettingsActivityResult(this)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void checkLastSeenVersion() {
        final String lastSeenVersion = ConfigurationManager.instance().getString(Constants.PREF_KEY_CORE_LAST_SEEN_VERSION);
        if (StringUtils.isNullOrEmpty(lastSeenVersion)) {
            //fresh install
            ConfigurationManager.instance().setString(Constants.PREF_KEY_CORE_LAST_SEEN_VERSION, Constants.FROSTWIRE_VERSION_STRING);
            UXStats.instance().log(UXAction.CONFIGURATION_WIZARD_FIRST_TIME);
        } else if (!Constants.FROSTWIRE_VERSION_STRING.equals(lastSeenVersion)) {
            //just updated.
            ConfigurationManager.instance().setString(Constants.PREF_KEY_CORE_LAST_SEEN_VERSION, Constants.FROSTWIRE_VERSION_STRING);
            UXStats.instance().log(UXAction.CONFIGURATION_WIZARD_AFTER_UPDATE);
        }
    }

    private void toggleDrawer() {
        if (navigationMenu.isOpen()) {
            navigationMenu.hide();
        } else {
            navigationMenu.show();
            syncNavigationMenu();
        }
        updateHeader(getCurrentFragment());
    }

    private void showLastBackDialog() {
        YesNoDialog dlg = YesNoDialog.newInstance(
                LAST_BACK_DIALOG_ID,
                R.string.minimize_frostwire,
                R.string.are_you_sure_you_wanna_leave,
                YesNoDialog.FLAG_DISMISS_ON_OK_BEFORE_PERFORM_DIALOG_CLICK);
        dlg.show(getFragmentManager()); //see onDialogClick
    }

    public void showShutdownDialog() {
        UXStats.instance().flush();
        YesNoDialog dlg = YesNoDialog.newInstance(
                SHUTDOWN_DIALOG_ID,
                R.string.app_shutdown_dlg_title,
                R.string.app_shutdown_dlg_message,
                YesNoDialog.FLAG_DISMISS_ON_OK_BEFORE_PERFORM_DIALOG_CLICK);
        dlg.show(getFragmentManager()); //see onDialogClick
    }

    public void onDialogClick(String tag, int which) {
        if (tag.equals(LAST_BACK_DIALOG_ID) && which == Dialog.BUTTON_POSITIVE) {
            onLastDialogButtonPositive();
        } else if (tag.equals(SHUTDOWN_DIALOG_ID) && which == Dialog.BUTTON_POSITIVE) {
            onShutdownDialogButtonPositive();
        } else if (tag.equals(SDPermissionDialog.TAG)) {
            handleSDPermissionDialogClick(which);
        }
    }

    private void onLastDialogButtonPositive() {
        Offers.showInterstitial(this, Offers.PLACEMENT_INTERSTITIAL_EXIT, false, true);
    }

    private void onShutdownDialogButtonPositive() {
        Offers.showInterstitial(this, Offers.PLACEMENT_INTERSTITIAL_EXIT, true, false);
    }

    public void syncNavigationMenu() {
        invalidateOptionsMenu();
        navigationMenu.updateCheckedItem(getNavMenuIdByFragment(getCurrentFragment()));
    }

    private void setupFragments() {
        search = (SearchFragment) getFragmentManager().findFragmentById(R.id.activity_main_fragment_search);
        search.connectDrawerLayoutFilterView((DrawerLayout) findView(R.id.activity_main_drawer_layout), findView(R.id.activity_main_keyword_filter_drawer_view));
        library = (BrowsePeerFragment) getFragmentManager().findFragmentById(R.id.activity_main_fragment_browse_peer);
        transfers = (TransfersFragment) getFragmentManager().findFragmentById(R.id.activity_main_fragment_transfers);
    }

    private void hideFragments() {
        FragmentTransaction tx = getFragmentManager().beginTransaction();
        tx.hide(search).hide(library).hide(transfers);
        tx.commit();
    }

    private void setupInitialFragment(Bundle savedInstanceState) {
        Fragment fragment = null;
        if (savedInstanceState != null) {
            fragment = getFragmentManager().getFragment(savedInstanceState, CURRENT_FRAGMENT_KEY);
            restoreFragmentsStack(savedInstanceState);
        }
        if (fragment == null) {
            fragment = search;
            setCheckedItem(R.id.menu_main_search);
        }
        switchContent(fragment);
    }

    private void setCheckedItem(int navMenuItemId) {
        if (navigationMenu != null) {
            navigationMenu.updateCheckedItem(navMenuItemId);
        }
    }

    private void saveFragmentsStack(Bundle outState) {
        int[] stack = new int[fragmentsStack.size()];
        for (int i = 0; i < stack.length; i++) {
            stack[i] = fragmentsStack.get(i);
        }
        outState.putIntArray(FRAGMENTS_STACK_KEY, stack);
    }

    private void restoreFragmentsStack(Bundle savedInstanceState) {
        try {
            int[] stack = savedInstanceState.getIntArray(FRAGMENTS_STACK_KEY);
            if (stack != null) {
                for (int id : stack) {
                    fragmentsStack.push(id);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void updateHeader(Fragment fragment) {
        try {
            Toolbar toolbar = findToolbar();
            if (toolbar == null) {
                LOG.warn("updateHeader(): Check your logic, no actionBar available");
                return;
            }
            if (fragment instanceof MainFragment) {
                View header = ((MainFragment) fragment).getHeader(this);
                if (header != null) {
                    setToolbarView(header);
                }
            }
            if (navigationMenu != null) {
                MenuItem item = navigationMenu.getCheckedItem();
                setTitle(item.getTitle());
            }
        } catch (Throwable e) {
            LOG.error("Error updating main header", e);
        }
    }

    private void switchContent(Fragment fragment, boolean addToStack) {
        hideFragments();
        getFragmentManager().beginTransaction().show(fragment).commitAllowingStateLoss();
        if (addToStack && (fragmentsStack.isEmpty() || fragmentsStack.peek() != fragment.getId())) {
            fragmentsStack.push(fragment.getId());
        }
        currentFragment = fragment;
        updateHeader(fragment);

        if (currentFragment instanceof MainFragment) {
            ((MainFragment) currentFragment).onShow();
        }
    }

    /*
     * The following methods are only public to be able to use them from another package(internal).
     */

    public Fragment getFragmentByNavMenuId(int id) {
        switch (id) {
            case R.id.menu_main_search:
                return search;
            case R.id.menu_main_library:
                return library;
            case R.id.menu_main_transfers:
                return transfers;
            default:
                return null;
        }
    }

    private int getNavMenuIdByFragment(Fragment fragment) {
        int menuId = -1;
        if (fragment == search) {
            menuId = R.id.menu_main_search;
        } else if (fragment == library) {
            menuId = R.id.menu_main_library;
        } else if (fragment == transfers) {
            menuId = R.id.menu_main_transfers;
        }
        return menuId;
    }

    public void switchContent(Fragment fragment) {
        switchContent(fragment, true);
    }

    public Fragment getCurrentFragment() {
        return currentFragment;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (navigationMenu != null) {
            try {
                navigationMenu.onOptionsItemSelected(item);
            } catch (Throwable t) {
                // usually java.lang.IllegalArgumentException: No drawer view found with gravity LEFT
                return false;
            }
            return false;
        }

        if (item == null) {
            return false;
        }

        switch (item.getItemId()) {
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        navigationMenu.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        navigationMenu.syncState();
    }

    private void setupActionBar() {
        ActionBar bar = getActionBar();
        if (bar != null) {
            bar.setCustomView(R.layout.view_custom_actionbar);
            bar.setDisplayShowCustomEnabled(true);
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setHomeButtonEnabled(true);
        }
    }

    private void setupDrawer() {
        DrawerLayout drawerLayout = findView(R.id.activity_main_drawer_layout);
        Toolbar toolbar = findToolbar();
        navigationMenu = new NavigationMenu(controller, drawerLayout, toolbar);
    }

    public void onServiceConnected(final ComponentName name, final IBinder service) {
        musicPlaybackService = IApolloService.Stub.asInterface(service);
    }

    public void onServiceDisconnected(final ComponentName name) {
        musicPlaybackService = null;
    }

    //@Override commented override since we are in API 16, but it will in API 23
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        DangerousPermissionsChecker checker = permissionsCheckers.get(requestCode);
        if (checker != null) {
            checker.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
        Offers.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void performYTSearch(String ytUrl) {
        SearchFragment searchFragment = (SearchFragment) getFragmentByNavMenuId(R.id.menu_main_search);
        searchFragment.performYTSearch(ytUrl);
        switchContent(searchFragment);
    }

    // TODO: refactor and move this method for a common place when needed
    private static String saveViewContent(Context context, Uri uri, String name) {
        InputStream inStream = null;
        OutputStream outStream = null;
        if (!Platforms.temp().exists()) {
            boolean mkdirs = Platforms.temp().mkdirs();
            if (!mkdirs) {
                LOG.warn("saveViewContent() could not create Platforms.temp() directory.");
            }
        }
        File target = new File(Platforms.temp(), name);
        try {
            inStream = context.getContentResolver().openInputStream(uri);
            outStream = new FileOutputStream(target);

            byte[] buffer = new byte[16384]; // MAGIC_NUMBER
            int bytesRead;
            if (inStream != null) {
                while ((bytesRead = inStream.read(buffer)) != -1) {
                    outStream.write(buffer, 0, bytesRead);
                }
            }
        } catch (Throwable e) {
            LOG.error("Error when copying file from " + uri + " to temp/" + name, e);
            return null;
        } finally {
            IOUtils.closeQuietly(inStream);
            IOUtils.closeQuietly(outStream);
        }

        return "file://" + target.getAbsolutePath();
    }
}
