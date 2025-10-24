/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.activities;

import android.Manifest;
import android.app.ActionBar;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.TellurideCourier;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.SearchMediator;
import com.frostwire.android.gui.SoftwareUpdater;
import com.frostwire.android.gui.activities.internal.MainController;
import com.frostwire.android.gui.activities.internal.NavigationMenu;
import com.frostwire.android.gui.dialogs.HandpickedTorrentDownloadDialogOnFetch;
import com.frostwire.android.gui.dialogs.NewTransferDialog;
import com.frostwire.android.gui.dialogs.YesNoDialog;
import com.frostwire.android.gui.fragments.MainFragment;
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
import com.frostwire.android.util.SystemUtils;
import com.frostwire.platform.Platforms;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.StringUtils;
import com.frostwire.util.http.OkHttpClientWrapper;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AbstractActivity implements OnDialogClickListener, ServiceConnection, ActivityCompat.OnRequestPermissionsResultCallback {

    private static final Logger LOG = Logger.getLogger(MainActivity.class);
    public static final int PROMO_VIDEO_PREVIEW_RESULT_CODE = 100;
    private static final String FRAGMENTS_STACK_KEY = "fragments_stack";
    private static final String CURRENT_FRAGMENT_KEY = "current_fragment";
    private static final String LAST_BACK_DIALOG_ID = "last_back_dialog";
    private static final String SHUTDOWN_DIALOG_ID = "shutdown_dialog";
    private static boolean firstTime = true;
    private boolean externalStoragePermissionsRequested = false;

    private final SparseArray<DangerousPermissionsChecker<MainActivity>> permissionsCheckers;
    private final Stack<Integer> fragmentsStack;
    private final MainController controller;
    private NavigationMenu navigationMenu;
    private Fragment currentFragment;
    private SearchFragment search;
    private TransfersFragment transfers;
    private final LocalBroadcastReceiver localBroadcastReceiver;
    private static TimerSubscription playerSubscription;
    private static MainActivity lastInstance = null;

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    public MainActivity() {
        super(R.layout.activity_main);
        controller = new MainController(this);
        fragmentsStack = new Stack<>();
        permissionsCheckers = initPermissionsCheckers();
        localBroadcastReceiver = new LocalBroadcastReceiver();
        lastInstance = this;
    }


    public static MainActivity instance() {
        return lastInstance;
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

    public void handleOnBackPressed() {
        if (navigationMenu.isOpen()) {
            navigationMenu.hide();
        } else if (fragmentsStack.size() > 1) {
            try {
                fragmentsStack.pop();
                int id = fragmentsStack.peek();
                Fragment fragment = getSupportFragmentManager().findFragmentById(id);
                if (fragment != null) {
                    switchContent(fragment, false);
                }
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

    public void shutdown() {
        if (shuttingDown.get()) {
            return;
        }
        shuttingDown.set(true);
        SearchMediator.instance().cancelSearch();
        MusicUtils.requestMusicPlaybackServiceShutdown(this);
        finish();
        OkHttpClientWrapper.cancelAllRequests();
        TellurideCourier.abortCurrentQuery();
        SystemUtils.stopAllHandlerThreads();
        Engine.instance().shutdown();
    }

    @Override
    public void finish() {
        finishAndRemoveTaskViaReflection();
    }

    private void finishAndRemoveTaskViaReflection() {
        final Class<? extends MainActivity> clazz = getClass();
        try {
            final Method finishAndRemoveTaskMethod = clazz.getMethod("finishAndRemoveTask");
            finishAndRemoveTaskMethod.invoke(this);
        } catch (Throwable e) {
            LOG.error("MainActivity.finishAndRemoveTaskViaReflection()", e);
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
        SystemUtils.ensureUIThreadOrCrash("MainActivity::onAdsPausedAsyncFinished");
        LOG.info("updateNavigationMenu(" + updateAvailable + ")");
        if (navigationMenu == null) {
            setupDrawer();
        }
        if (updateAvailable) {
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
            }
        }
        if (intent.hasExtra(Constants.EXTRA_DOWNLOAD_COMPLETE_NOTIFICATION)) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> onDownloadCompleteNotification(intent));
        }
        if (intent.hasExtra(Constants.EXTRA_FINISH_MAIN_ACTIVITY)) {
            finish();
        }
        super.onNewIntent(intent);
    }

    private void openTorrentUrl(Intent intent) {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.setAction(Constants.ACTION_SHOW_TRANSFERS);
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
            final String uri = intent.getDataString();
            intent.setAction(null);
            if (uri != null) {
                if (uri.startsWith("file") || uri.startsWith("http") || uri.startsWith("https") || uri.startsWith("magnet")) {
                    TransferManager.instance().downloadTorrent(uri, new HandpickedTorrentDownloadDialogOnFetch(this, false));
                } else if (uri.startsWith("content")) {
                    String newUri = saveViewContent(this, Uri.parse(uri));
                    if (newUri != null) {
                        TransferManager.instance().downloadTorrent(newUri, new HandpickedTorrentDownloadDialogOnFetch(this, false));
                    }
                }
            } else {
                LOG.warn("MainActivity.onNewIntent(): Couldn't start torrent download from Intent's URI, intent.getDataString() -> null");
            }
        } catch (Throwable e) {
            LOG.error("Error opening torrent from intent", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastInstance = this;
        localBroadcastReceiver.register(this);
        setupDrawer();
        ConfigurationManager CM = ConfigurationManager.instance();
        if (CM.getBoolean(Constants.PREF_KEY_GUI_INITIAL_SETTINGS_COMPLETE)) {
            mainResume();

            // CHANGED: Run initAdNetworks immediately on main thread
            Offers.initAdNetworks(this); // ADDED - revert from background post

        } else if (!isShutdown()) {
            controller.startWizardActivity();
        }


        checkLastSeenVersionBuild();
        syncNavigationMenu();
        updateNavigationMenu();

        if (CM.getBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED)) {
            checkExternalStoragePermissions();
        }
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            NetworkManager networkManager = NetworkManager.instance();
            NetworkManager.queryNetworkStatusBackground(networkManager);
        });
    }

    @Override
    protected void onPause() {
        localBroadcastReceiver.unregister(this);
        super.onPause();
    }

    private SparseArray<DangerousPermissionsChecker<MainActivity>> initPermissionsCheckers() {
        SparseArray<DangerousPermissionsChecker<MainActivity>> checkers = new SparseArray<>();
        final DangerousPermissionsChecker<MainActivity> externalStorageChecker = new DangerousPermissionsChecker<>(this, DangerousPermissionsChecker.EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE);
        checkers.put(DangerousPermissionsChecker.EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE, externalStorageChecker);
        final DangerousPermissionsChecker<MainActivity> postNotificationsChecker = new DangerousPermissionsChecker<>(this, DangerousPermissionsChecker.POST_NOTIFICATIONS_PERMISSIONS_REQUEST_CODE);
        checkers.put(DangerousPermissionsChecker.POST_NOTIFICATIONS_PERMISSIONS_REQUEST_CODE, postNotificationsChecker);
        return checkers;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (currentFragment != null && currentFragment.isAdded()) {
            getSupportFragmentManager().putFragment(outState, CURRENT_FRAGMENT_KEY, currentFragment);
        }
        saveFragmentsStack(outState);
        outState.putBoolean("updateAvailable", getIntent().getBooleanExtra("updateAvailable", false));
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_FrostWire);
        super.onCreate(savedInstanceState);
        lastInstance = this;
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                MainActivity.this.handleOnBackPressed();
            }
        });
    }

    private void checkExternalStoragePermissions() {
        DangerousPermissionsChecker<MainActivity> checker = permissionsCheckers.get(DangerousPermissionsChecker.EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE);
        boolean shouldShowRequestPermissionRationaleForReadExternal = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        if (shouldShowRequestPermissionRationaleForReadExternal || (!externalStoragePermissionsRequested && checker != null && checker.noExternalStorageAccess())) {
            checker.requestPermissions();
            externalStoragePermissionsRequested = true;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (search != null) {
            search.destroyHeaderBanner();
            search.destroyPromotionsBanner();
        }
        if (playerSubscription != null) {
            playerSubscription.unsubscribe();
        }
        lastInstance = null;
    }

    private void mainResume() {
        syncNavigationMenu();
        if (firstTime) {
            if (ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY) && !NetworkManager.instance().isTunnelUp()) {
                UIUtils.showDismissableMessage(findView(R.id.activity_main_parent_layout), R.string.cannot_start_engine_without_vpn);
            } else {
                firstTime = false;
                Engine.instance().startServices();
            }
        }
        if (Engine.instance().wasShutdown()) {
            Engine.instance().startServices();
        }
        SoftwareUpdater.getInstance().checkForUpdate(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!DangerousPermissionsChecker.handleOnWriteSettingsActivityResult(this)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void checkLastSeenVersionBuild() {
        final ConfigurationManager CM = ConfigurationManager.instance();
        final String lastSeenVersionBuild = CM.getString(Constants.PREF_KEY_CORE_LAST_SEEN_VERSION_BUILD);
        final String currentVersionBuild = Constants.FROSTWIRE_VERSION_STRING + "." + Constants.FROSTWIRE_BUILD;
        if (StringUtils.isNullOrEmpty(lastSeenVersionBuild)) {
            CM.setString(Constants.PREF_KEY_CORE_LAST_SEEN_VERSION_BUILD, currentVersionBuild);
        } else if (!currentVersionBuild.equals(lastSeenVersionBuild)) {
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
        YesNoDialog dlg = YesNoDialog.newInstance(LAST_BACK_DIALOG_ID, R.string.minimize_frostwire, R.string.are_you_sure_you_wanna_leave, YesNoDialog.FLAG_DISMISS_ON_OK_BEFORE_PERFORM_DIALOG_CLICK);
        dlg.show(getSupportFragmentManager());
    }

    public void showShutdownDialog() {
        if (getSupportFragmentManager().isStateSaved()) {
            LOG.warn("showShutdownDialog() aborted: FragmentManager state is already saved.");
            return;
        }
        YesNoDialog dlg = YesNoDialog.newInstance(SHUTDOWN_DIALOG_ID, R.string.app_shutdown_dlg_title, R.string.app_shutdown_dlg_message, YesNoDialog.FLAG_DISMISS_ON_OK_BEFORE_PERFORM_DIALOG_CLICK);
        dlg.show(getSupportFragmentManager());
    }

    public void onDialogClick(String tag, int which) {
        String[] mainActivityDialogTags = {LAST_BACK_DIALOG_ID, SHUTDOWN_DIALOG_ID};
        boolean mainActivityShouldHandleThisClick = Arrays.asList(mainActivityDialogTags).contains(tag);
        if (which != Dialog.BUTTON_POSITIVE || !mainActivityShouldHandleThisClick) {
            return;
        }
        LOG.info("MainActivity.onDialogClick(tag:" + tag + ", which:" + which + ")");
        if (tag.equals(LAST_BACK_DIALOG_ID)) {
            onLastDialogButtonPositive();
        } else if (tag.equals(SHUTDOWN_DIALOG_ID)) {
            onShutdownDialogButtonPositive();
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
        search = (SearchFragment) getSupportFragmentManager().findFragmentById(R.id.activity_main_fragment_search);
        transfers = (TransfersFragment) getSupportFragmentManager().findFragmentById(R.id.activity_main_fragment_transfers);
    }

    private void hideFragments() {
        try {
            androidx.fragment.app.FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
            tx.hide(search).hide(transfers);
            tx.commitNowAllowingStateLoss(); // Ensures immediate execution and allows state loss
        } catch (Throwable t) {
            LOG.warn("Error hiding fragments", t);
        }
    }


    private void setupInitialFragment(Bundle savedInstanceState) {
        Fragment fragment = null;

        // Attempt to retrieve the fragment from savedInstanceState
        if (savedInstanceState != null) {
            try {
                fragment = getSupportFragmentManager().getFragment(savedInstanceState, CURRENT_FRAGMENT_KEY);
            } catch (IllegalStateException e) {
                LOG.warn("Fragment no longer exists for key: " + CURRENT_FRAGMENT_KEY, e);
            }
            restoreFragmentsStack(savedInstanceState);
        }

        // If no valid fragment is found, fall back to the default fragment
        if (fragment == null || !fragment.isAdded()) {
            fragment = search; // Default to the search fragment
            setMainMenuSearchCheckedItem();
        }

        // Safely switch to the retrieved or default fragment
        switchContent(fragment);
    }


    private void setMainMenuSearchCheckedItem() {
        if (navigationMenu != null) {
            navigationMenu.updateCheckedItem(R.id.menu_main_search);
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
                LOG.warn("updateHeader(): no actionBar available");
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
        if (fragment == null || fragment == currentFragment) {
            return;
        }
        hideFragments();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction().show(fragment);
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

    public Fragment getFragmentByNavMenuId(int id) {
        if (id == R.id.menu_main_search) {
            return search;
        } else if (id == R.id.menu_main_transfers) {
            return transfers;
        }
        return null;
    }

    private int getNavMenuIdByFragment(Fragment fragment) {
        int menuId = -1;
        if (fragment == search) {
            menuId = R.id.menu_main_search;
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
                return false;
            }
            return false;
        }
        if (item == null) {
            return false;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
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
    }

    public void onServiceDisconnected(final ComponentName name) {
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        DangerousPermissionsChecker<MainActivity> checker = permissionsCheckers.get(requestCode);
        if (checker != null) {
            checker.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public static void refreshTransfers(Context context) {
        Intent intent = new Intent(context, MainActivity.class).setAction(Constants.ACTION_SHOW_TRANSFERS).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
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

    private static String saveViewContent(Context context, Uri uri) {
        InputStream inStream = null;
        OutputStream outStream = null;
        if (!Platforms.temp().exists()) {
            boolean mkdirs = Platforms.temp().mkdirs();
            if (!mkdirs) {
                LOG.warn("saveViewContent() could not create Platforms.temp() directory.");
            }
        }
        File target = new File(Platforms.temp(), "content-intent.torrent");
        try {
            inStream = context.getContentResolver().openInputStream(uri);
            outStream = new FileOutputStream(target);
            byte[] buffer = new byte[16384];
            int bytesRead;
            if (inStream != null) {
                while ((bytesRead = inStream.read(buffer)) != -1) {
                    outStream.write(buffer, 0, bytesRead);
                }
            }
        } catch (Throwable e) {
            LOG.error("Error when copying file from " + uri + " to temp/content-intent.torrent", e);
            return null;
        } finally {
            IOUtils.closeQuietly(inStream);
            IOUtils.closeQuietly(outStream);
        }
        return "file://" + target.getAbsolutePath();
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
                SystemUtils.postToUIThread(() -> updateNavigationMenu(value));
            }
            if (Constants.ACTION_NOTIFY_DATA_INTERNET_CONNECTION.equals(action)) {
                boolean isDataUp = intent.getBooleanExtra("isDataUp", true);
                if (!isDataUp && !!MainActivity.this.isPaused() && !MainActivity.this.isFinishing()) {
                    UIUtils.showDismissableMessage(findView(android.R.id.content), R.string.no_data_check_internet_connection);
                }
                SystemUtils.postToUIThread(() -> search.setDataUp(isDataUp));
            }
        }
    }
}
