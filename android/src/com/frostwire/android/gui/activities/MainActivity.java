/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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
import android.app.FragmentManager;
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
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.R;
import com.frostwire.android.StoragePicker;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.LocalSearchEngine;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.SoftwareUpdater;
import com.frostwire.android.gui.activities.internal.MainController;
import com.frostwire.android.gui.activities.internal.NavigationMenu;
import com.frostwire.android.gui.dialogs.HandpickedTorrentDownloadDialogOnFetch;
import com.frostwire.android.gui.dialogs.NewTransferDialog;
import com.frostwire.android.gui.dialogs.SDPermissionDialog;
import com.frostwire.android.gui.dialogs.YesNoDialog;
import com.frostwire.android.gui.fragments.MainFragment;
import com.frostwire.android.gui.fragments.MyFilesFragment;
import com.frostwire.android.gui.fragments.SearchFragment;
import com.frostwire.android.gui.fragments.TransfersFragment;
import com.frostwire.android.gui.fragments.TransfersFragment.TransferStatus;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.DangerousPermissionsChecker;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.AbstractDialog.OnDialogClickListener;
import com.frostwire.android.gui.views.MiniPlayerView;
import com.frostwire.android.gui.views.TimerService;
import com.frostwire.android.gui.views.TimerSubscription;
import com.frostwire.android.offers.Offers;
import com.frostwire.platform.Platforms;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.StringUtils;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Stack;

import static com.frostwire.android.util.Asyncs.async;

/**
 * @author gubatron
 * @author aldenml
 */
public class MainActivity extends AbstractActivity implements
        OnDialogClickListener,
        ServiceConnection,
        ActivityCompat.OnRequestPermissionsResultCallback {

    private static final Logger LOG = Logger.getLogger(MainActivity.class);
    public static final int PROMO_VIDEO_PREVIEW_RESULT_CODE = 100;
    private static final String FRAGMENTS_STACK_KEY = "fragments_stack";
    private static final String CURRENT_FRAGMENT_KEY = "current_fragment";
    private static final String LAST_BACK_DIALOG_ID = "last_back_dialog";
    private static final String SHUTDOWN_DIALOG_ID = "shutdown_dialog";
    private static boolean firstTime = true;
    private boolean externalStoragePermissionsRequested = false;

    private final SparseArray<DangerousPermissionsChecker> permissionsCheckers;
    private final Stack<Integer> fragmentsStack;
    private final MainController controller;
    private NavigationMenu navigationMenu;
    private Fragment currentFragment;
    private SearchFragment search;
    private MyFilesFragment library;
    private TransfersFragment transfers;
    private BroadcastReceiver mainBroadcastReceiver;
    private final LocalBroadcastReceiver localBroadcastReceiver;
    private static TimerSubscription playerSubscription;

    private boolean shuttingdown = false;

    public MainActivity() {
        super(R.layout.activity_main);
        controller = new MainController(this);
        fragmentsStack = new Stack<>();
        permissionsCheckers = initPermissionsCheckers();
        localBroadcastReceiver = new LocalBroadcastReceiver();
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
            try {
                return super.onKeyDown(keyCode, event);
            } catch (NullPointerException npe) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        boolean lastBackDialogShown = false;
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
                lastBackDialogShown = true;
            }
        } else {
            showLastBackDialog();
            lastBackDialogShown = true;
        }
        syncNavigationMenu();
        updateHeader(getCurrentFragment());
        if (!lastBackDialogShown) {
            Offers.showInterstitialOfferIfNecessary(
                    this,
                    Offers.PLACEMENT_INTERSTITIAL_MAIN,
                    false,
                    false,
                    true);
        }
    }

    public void shutdown() {
        if (shuttingdown) {
            // NOTE: the actual solution should be for a re-architecture for
            // a guarantee of a single call of this logic.
            // For now, just mitigate the double call if coming from the exit
            // and at the same time the close of the interstitial
            return;
        }
        shuttingdown = true;
        SearchFragment.freeInstance();
        LocalSearchEngine.instance().cancelSearch();
        MusicUtils.requestMusicPlaybackServiceShutdown(this);
        finish();
        Engine.instance().shutdown();
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
        boolean result = intent != null && intent.getBooleanExtra("shutdown-frostwire", false);
        if (result) {
            shutdown();
        }
        return result;
    }

    private boolean isGoHome(Intent intent) {
        if (intent == null) {
            intent = getIntent();
        }
        return intent != null && intent.getBooleanExtra("gohome-frostwire", false);
    }

    @Override
    protected void initComponents(Bundle savedInstanceState) {
        if (isShutdown()) {
            return;
        }
        updateNavigationMenu();
        setupFragments();
        setupInitialFragment(savedInstanceState);
        MiniPlayerView miniPlayerView = findView(R.id.activity_main_player_notifier);
        if (playerSubscription != null) {
            if (playerSubscription.isSubscribed()) {
                playerSubscription.unsubscribe();
            }
            TimerService.reSubscribe(miniPlayerView.getRefresher(), playerSubscription, MiniPlayerView.REFRESHER_INTERVAL_IN_SECS);
        } else {
            playerSubscription = TimerService.subscribe(miniPlayerView.getRefresher(), MiniPlayerView.REFRESHER_INTERVAL_IN_SECS);
        }
        onNewIntent(getIntent());
        setupActionBar();
    }

    public void updateNavigationMenu(boolean updateAvailable) {
        LOG.info("updateNavigationMenu(" + updateAvailable + ")");
        if (navigationMenu == null) {
            setupDrawer();
        }
        if (updateAvailable) {
            // make sure it will remember this, even if the menu gets destroyed
            getIntent().putExtra("updateAvailable", true);
            navigationMenu.onUpdateAvailable();
        }
    }

    private void updateNavigationMenu() {
        Intent intent = getIntent();
        if (intent != null) {
            updateNavigationMenu(intent.getBooleanExtra("updateAvailable", false));
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
                    }
                    break;
                case Constants.ACTION_REQUEST_SHUTDOWN:
                    showShutdownDialog();
                    break;
////// START OF PACKAGE INSTALLER LOGIC SECTION
//    Leaving this code in case I find a solution later.
//                case Constants.ACTION_PACKAGE_INSTALLED:
//                    // see UIUtils.openAPK()
//                    onPackageInstalledCallback(intent.getExtras());
//                    break;
////// END OF PACKAGE INSTALLER LOGIC SECTION
            }
        }
        if (intent.hasExtra(Constants.EXTRA_DOWNLOAD_COMPLETE_NOTIFICATION)) {
            async(this, MainActivity::onDownloadCompleteNotification, intent);
        }
        if (intent.hasExtra(Constants.EXTRA_FINISH_MAIN_ACTIVITY)) {
            finish();
        }
    }

////// START OF PACKAGE INSTALLER LOGIC SECTION
//    Leaving this code in case I find a solution later.
//    See the commented code in UIUtils.openAPK for details.
//    private void onPackageInstalledCallback(Bundle extras) {
//        int status = extras.getInt(PackageInstaller.EXTRA_STATUS);
//        String message = extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE);
//        switch (status) {
//            case PackageInstaller.STATUS_PENDING_USER_ACTION:
//                // This test app isn't privileged, so the user has to confirm the install.
//                Intent confirmIntent = (Intent) extras.get(Intent.EXTRA_INTENT);
//                //Intent { act=android.content.pm.action.CONFIRM_PERMISSIONS pkg=com.google.android.packageinstaller (has extras) }
//                startActivity(confirmIntent); // <-- this call isn't really launching the screen to ask for permissions to install apks
//                                              // setting <uses-permission android:name="android.permission.INSTALL_PACKAGES"/> on AndroidManifest.xml doesn't work either
//                break;
//            case PackageInstaller.STATUS_SUCCESS:
//                Engine.instance().stopServices(false);
//                try {
//                    MusicUtils.getMusicPlaybackService().stop();
//                } catch (RemoteException e) {
//                    e.printStackTrace();
//                }
//                UIUtils.showToastMessage(this, "Install succeeded", Toast.LENGTH_SHORT);
//                break;
//            case PackageInstaller.STATUS_FAILURE:
//            case PackageInstaller.STATUS_FAILURE_ABORTED:
//            case PackageInstaller.STATUS_FAILURE_BLOCKED:
//            case PackageInstaller.STATUS_FAILURE_CONFLICT:
//            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
//            case PackageInstaller.STATUS_FAILURE_INVALID:
//            case PackageInstaller.STATUS_FAILURE_STORAGE:
//                UIUtils.showToastMessage(this, "Install failed! " + status + ", " + message,
//                        Toast.LENGTH_SHORT);
//                break;
//            default:
//                UIUtils.showToastMessage(this, "Unrecognized status received from installer: " + status,
//                        Toast.LENGTH_SHORT);
//        }
//    }
////// END OF PACKAGE INSTALLER LOGIC SECTION

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
                    TransferManager.instance().downloadTorrent(uri, new HandpickedTorrentDownloadDialogOnFetch(this, false));
                } else if (uri.startsWith("content")) {
                    String newUri = saveViewContent(this, Uri.parse(uri), "content-intent.torrent");
                    if (newUri != null) {
                        TransferManager.instance().downloadTorrent(newUri, new HandpickedTorrentDownloadDialogOnFetch(this, false));
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
        localBroadcastReceiver.register(this);
        setupDrawer();
        ConfigurationManager CM = ConfigurationManager.instance();
        if (CM.getBoolean(Constants.PREF_KEY_GUI_INITIAL_SETTINGS_COMPLETE)) {
            mainResume();
            Offers.initAdNetworks(this);
        } else if (!isShutdown()) {
            controller.startWizardActivity();
        }
        checkLastSeenVersionBuild();
        registerMainBroadcastReceiver();
        syncNavigationMenu();
        updateNavigationMenu();
        //uncomment to test social links dialog
        //UIUtils.showSocialLinksDialog(this, true, null, "");
        if (CM.getBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED)) {
            //checkExternalStoragePermissionsOrBindMusicService();
            checkExternalStoragePermissions();
        }
        async(NetworkManager.instance(), NetworkManager::queryNetworkStatusBackground);
    }

    @Override
    protected void onPause() {
        super.onPause();
        localBroadcastReceiver.unregister(this);
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
        mainBroadcastReceiver = new MainBroadcastReceiver(this);
        IntentFilter bf = new IntentFilter(Constants.ACTION_NOTIFY_SDCARD_MOUNTED);
        try {
            registerReceiver(mainBroadcastReceiver, bf);
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (outState != null) {
            // MIGHT DO: save checkedNavViewMenuItemId in bundle.
            outState.putBoolean("updateAvailable", getIntent().getBooleanExtra("updateAvailable", false));
            super.onSaveInstanceState(outState);
            saveLastFragment(outState);
            saveFragmentsStack(outState);
        }
    }

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
        checkExternalStoragePermissions();//OrBindMusicService();
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

    private void checkExternalStoragePermissions() {
        DangerousPermissionsChecker checker = permissionsCheckers.get(DangerousPermissionsChecker.EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE);
        if (!externalStoragePermissionsRequested && checker != null && checker.noAccess()) {
            checker.requestPermissions();
            externalStoragePermissionsRequested = true;
        }
    }

    private void onNotifySdCardMounted() {
        transfers.initStorageRelatedRichNotifications(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (search != null) {
            // this is necessary because the Fragment#onDestroy is not
            // necessary called right in the Activity#onDestroy call, making
            // the internal mopub view possible to outlive the activity
            // destruction, creating a context leak
            search.destroyHeaderBanner();
            // TODO: make a unique call for these destroys
            search.destroyPromotionsBanner();
        }
        if (playerSubscription != null) {
            playerSubscription.unsubscribe();
        }
        // necessary unregisters broadcast its internal receivers, avoids leaks.
        Offers.destroyMopubInterstitials();
    }

    private void saveLastFragment(Bundle outState) {
        Fragment fragment = getCurrentFragment();
        if (fragment != null) {
            getFragmentManager().putFragment(outState, CURRENT_FRAGMENT_KEY, fragment);
        }
    }

    private void mainResume() {
        async(this, MainActivity::checkSDPermission, MainActivity::checkSDPermissionPost);
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
        if (Engine.instance().wasShutdown()) {
            Engine.instance().startServices();
        }
        SoftwareUpdater.getInstance().checkForUpdate(this);
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
            Offers.showInterstitialOfferIfNecessary(this, Offers.PLACEMENT_INTERSTITIAL_MAIN, false, false, true);
        }

        // the filetype and audio id parameters are passed via static hack
        if (!DangerousPermissionsChecker.handleOnWriteSettingsActivityResult(this)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void checkLastSeenVersionBuild() {
        final ConfigurationManager CM = ConfigurationManager.instance();
        final String lastSeenVersionBuild = CM.getString(Constants.PREF_KEY_CORE_LAST_SEEN_VERSION_BUILD);
        final String currentVersionBuild = Constants.FROSTWIRE_VERSION_STRING + "." + Constants.FROSTWIRE_BUILD;
        if (StringUtils.isNullOrEmpty(lastSeenVersionBuild)) {
            //fresh install
            CM.setString(Constants.PREF_KEY_CORE_LAST_SEEN_VERSION_BUILD, currentVersionBuild);
        } else if (!currentVersionBuild.equals(lastSeenVersionBuild)) {
            //just updated.
            CM.setString(Constants.PREF_KEY_CORE_LAST_SEEN_VERSION_BUILD, currentVersionBuild);
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
        finish();
    }

    private void onShutdownDialogButtonPositive() {
        shutdown();
    }

    public void syncNavigationMenu() {
        invalidateOptionsMenu();
        navigationMenu.updateCheckedItem(getNavMenuIdByFragment(getCurrentFragment()));
    }

    private void setupFragments() {
        search = (SearchFragment) getFragmentManager().findFragmentById(R.id.activity_main_fragment_search);
        search.connectDrawerLayoutFilterView(findView(R.id.activity_main_drawer_layout), findView(R.id.activity_main_keyword_filter_drawer_view));
        library = (MyFilesFragment) getFragmentManager().findFragmentById(R.id.activity_main_fragment_my_files);
        transfers = (TransfersFragment) getFragmentManager().findFragmentById(R.id.activity_main_fragment_transfers);
    }

    private void hideFragments() {
        try {
            getFragmentManager().executePendingTransactions();
        } catch (Throwable t) {
            LOG.warn(t.getMessage(), t);
        }
        FragmentTransaction tx = getFragmentManager().beginTransaction();
        tx.hide(search).hide(library).hide(transfers);
        try {
            tx.commit();
        } catch (IllegalStateException e) {
            // if not that we can do a lot here, since the root of the problem
            // is the multiple entry points to MainActivity, just let it run
            // a possible inconsistent (but probably right) version.
            // in the future with a higher API, commitNow should be considered
            LOG.warn("Error running commit in fragment transaction, using weaker option", e);
            try {
                tx.commitAllowingStateLoss();
            } catch (IllegalStateException e2) {
                // ¯\_(ツ)_/¯
                LOG.warn("Error running commit in fragment transaction, weaker option also failed (commit already called - mCommited=true)", e2);
            }
        }
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
        FragmentTransaction transaction = getFragmentManager().beginTransaction().show(fragment);
        try {
            transaction.commitAllowingStateLoss();
        } catch (Throwable ignored) {
        }
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
        //musicPlaybackService = IApolloService.Stub.asInterface(service);
    }

    public void onServiceDisconnected(final ComponentName name) {
        //musicPlaybackService = null;
    }

    //@Override commented override since we are in API 16, but it will in API 23
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        DangerousPermissionsChecker checker = permissionsCheckers.get(requestCode);
        if (checker != null) {
            checker.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    public void performYTSearch(String ytUrl) {
        SearchFragment searchFragment = (SearchFragment) getFragmentByNavMenuId(R.id.menu_main_search);
        searchFragment.performYTSearch(ytUrl);
        switchContent(searchFragment);
    }

    public static void refreshTransfers(Context context) {
        Intent intent = new Intent(context,
                MainActivity.class).
                setAction(Constants.ACTION_SHOW_TRANSFERS).
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            context.startActivity(intent);
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
        }
    }

    private void onDownloadCompleteNotification(Intent intent) {
        controller.showTransfers(TransferStatus.COMPLETED);
        TransferManager.instance().clearDownloadsToReview();
        try {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancel(Constants.NOTIFICATION_DOWNLOAD_TRANSFER_FINISHED);
            }
            Bundle extras = intent.getExtras();
            if (extras != null) {
                String downloadCompletePath = extras.getString(Constants.EXTRA_DOWNLOAD_COMPLETE_PATH);
                if (downloadCompletePath != null) {
                    File file = new File(downloadCompletePath);
                    if (file.isFile()) {
                        UIUtils.openFile(this, file.getAbsoluteFile());
                    }
                }
            }
        } catch (Throwable e) {
            LOG.warn("Error handling download complete notification", e);
        }
    }

    /**
     * @return true if the SD Permission dialog must be shown
     */
    private boolean checkSDPermission() {
        if (!AndroidPlatform.saf()) {
            return false;
        }
        try {
            File data = Platforms.data();
            File parent = data.getParentFile();
            return AndroidPlatform.saf(parent) && (!Platforms.fileSystem().canWrite(parent) && !SDPermissionDialog.visible);
        } catch (Throwable e) {
            // we can't do anything about this
            LOG.error("Unable to detect if we have SD permissions", e);
            return false;
        }
    }

    private void checkSDPermissionPost(boolean showPermissionDialog) {
        if (showPermissionDialog) {
            SDPermissionDialog dlg = SDPermissionDialog.newInstance();
            FragmentManager fragmentManager = getFragmentManager();
            try {
                if (fragmentManager != null) {
                    dlg.show(fragmentManager);
                }
            } catch (IllegalStateException ignored) {
            }
        }
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

    private static final class MainBroadcastReceiver extends BroadcastReceiver {
        private final WeakReference<MainActivity> activityRef;

        MainBroadcastReceiver(MainActivity activity) {
            activityRef = Ref.weak(activity);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Ref.alive(activityRef) && Constants.ACTION_NOTIFY_SDCARD_MOUNTED.equals(intent.getAction())) {
                activityRef.get().onNotifySdCardMounted();
            }
        }
    }

    private final class LocalBroadcastReceiver extends BroadcastReceiver {

        private final IntentFilter intentFilter;

        LocalBroadcastReceiver() {
            intentFilter = new IntentFilter();
            intentFilter.addAction(Constants.ACTION_NOTIFY_UPDATE_AVAILABLE);
            intentFilter.addAction(Constants.ACTION_NOTIFY_DATA_INTERNET_CONNECTION);
        }

        public void register(Context context) {
            LocalBroadcastManager.getInstance(context).registerReceiver(this, intentFilter);
        }

        public void unregister(Context context) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Constants.ACTION_NOTIFY_UPDATE_AVAILABLE.equals(action)) {
                boolean value = intent.getBooleanExtra("value", false);
                Intent mainActivityIntent = getIntent();
                if (mainActivityIntent != null) {
                    mainActivityIntent.putExtra("updateAvailable", value);
                }
                updateNavigationMenu(value);
            }
            if (Constants.ACTION_NOTIFY_DATA_INTERNET_CONNECTION.equals(action)) {
                boolean isDataUp = intent.getBooleanExtra("isDataUp", true);
                if (!isDataUp) {
                    UIUtils.showDismissableMessage(findView(android.R.id.content),
                            R.string.no_data_check_internet_connection);
                }
                search.setDataUp(isDataUp);
            }
        }
    }
}
