/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.RelativeLayout;

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
import com.frostwire.android.gui.activities.internal.MainMenuAdapter;
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
import com.frostwire.android.gui.views.AdMenuItemView;
import com.frostwire.android.gui.views.PlayerMenuItemView;
import com.frostwire.android.gui.views.PlayerNotifierView;
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
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
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

    private static final Logger LOG = Logger.getLogger(MainActivity.class);
    private static final String FRAGMENTS_STACK_KEY = "fragments_stack";
    private static final String CURRENT_FRAGMENT_KEY = "current_fragment";
    private static final String LAST_BACK_DIALOG_ID = "last_back_dialog";
    private static final String SHUTDOWN_DIALOG_ID = "shutdown_dialog";
    private static boolean firstTime = true;
    public static final int PROMO_VIDEO_PREVIEW_RESULT_CODE = 100;
    private final Map<Integer, DangerousPermissionsChecker> permissionsCheckers;
    private MainController controller;
    private DrawerLayout drawerLayout;

    private ActionBarDrawerToggle drawerToggle;
    private View leftDrawer;
    private ListView listMenu;
    private SearchFragment search;
    private BrowsePeerFragment library;
    private TransfersFragment transfers;
    private Fragment currentFragment;
    private final Stack<Integer> fragmentsStack;
    private PlayerMenuItemView playerItem;
    private AdMenuItemView menuRemoveAdsItem;
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
        if (drawerLayout.isDrawerOpen(leftDrawer)) {
            closeSlideMenu();
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

        syncSlideMenu();
        updateHeader(getCurrentFragment());
    }

    public void onConfigurationUpdate() {
        setupMenuItems();
    }

    public void shutdown() {
        Offers.stopAdNetworks(this);
        //UXStats.instance().flush(true); // sends data and ends 3rd party APIs sessions.
        finish();
        Engine.instance().shutdown();
        MusicUtils.requestMusicPlaybackServiceShutdown(MainActivity.this);
        // we make sure all services have finished shutting down before we kill our own process.
        new Thread("shutdown-halt") {
            @Override
            public void run() {
                SystemUtils.waitWhileServicesAreRunning(MainActivity.this, 5000, MusicPlaybackService.class, EngineService.class);
                LOG.info("MainActivity::shutdown()/shutdown-halt thread: android.os.Process.killProcess(" + android.os.Process.myPid() + ")");
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        }.start();
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
        leftDrawer = findView(R.id.activity_main_left_drawer);
        listMenu = findView(R.id.left_drawer);
        initPlayerItemListener();
        initAdMenuItemListener();
        setupFragments();
        setupMenuItems();
        setupInitialFragment(savedInstanceState);
        playerSubscription = TimerService.subscribe(((PlayerNotifierView) findView(R.id.activity_main_player_notifier)).getRefresher(), 1);
        onNewIntent(getIntent());
        SoftwareUpdater.instance().addConfigurationUpdateListener(this);
        setupActionBar();
        setupDrawer();
    }

    private void initPlayerItemListener() {
        playerItem = findView(R.id.slidemenu_player_menuitem);
        playerItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                controller.launchPlayerActivity();
            }
        });
    }

    private void initAdMenuItemListener() {
        menuRemoveAdsItem = findView(R.id.slidermenu_ad_menuitem);
        RelativeLayout menuAd = findView(R.id.view_ad_menu_item_ad);
        menuAd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, BuyActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        if (isShutdown(intent)) {
            return;
        }
        if (isGoHome(intent)) {
            finish();
            return;
        }

        String action = intent.getAction();
        if (action != null) {
            if (action.equals(Constants.ACTION_SHOW_TRANSFERS)) {
                intent.setAction(null);
                controller.showTransfers(TransferStatus.ALL);
            } else if (action.equals(Intent.ACTION_VIEW)) {
                openTorrentUrl(intent);
            } else if (action.equals(Constants.ACTION_START_TRANSFER_FROM_PREVIEW)) {
                if (Ref.alive(NewTransferDialog.srRef)) {
                    SearchFragment.startDownload(this, NewTransferDialog.srRef.get(), getString(R.string.download_added_to_queue));
                    UXStats.instance().log(UXAction.DOWNLOAD_CLOUD_FILE_FROM_PREVIEW);
                }
            } else if (action.equals(Constants.ACTION_REQUEST_SHUTDOWN)) {
                UXStats.instance().log(UXAction.MISC_NOTIFICATION_EXIT);
                showShutdownDialog();
            }
        }
        if (intent.hasExtra(Constants.EXTRA_DOWNLOAD_COMPLETE_NOTIFICATION)) {
            controller.showTransfers(TransferStatus.COMPLETED);
            TransferManager.instance().clearDownloadsToReview();
            try {
                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(Constants.NOTIFICATION_DOWNLOAD_TRANSFER_FINISHED);
                Bundle extras = intent.getExtras();
                if (extras.containsKey(Constants.EXTRA_DOWNLOAD_COMPLETE_PATH)) {
                    File file = new File(extras.getString(Constants.EXTRA_DOWNLOAD_COMPLETE_PATH));
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
        initPlayerItemListener();
        initAdMenuItemListener();
        refreshPlayerItem();
        refreshMenuRemoveAdsItem();
        if (ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_INITIAL_SETTINGS_COMPLETE)) {
            mainResume();
            Offers.initAdNetworks(this);
        } else if (!isShutdown()) {
            controller.startWizardActivity();
        }
        checkLastSeenVersion();
        registerMainBroadcastReceiver();
        syncSlideMenu();
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

    private Map<Integer, DangerousPermissionsChecker> initPermissionsCheckers() {
        Map<Integer, DangerousPermissionsChecker> checkers = new HashMap<>();

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
            super.onSaveInstanceState(outState);
            saveLastFragment(outState);
            saveFragmentsStack(outState);
        }
    }

    private ServiceToken mToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.ThemeFrostWire);
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

        if (playerItem != null) {
            playerItem.unbindDrawables();
        }

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

        syncSlideMenu();
        if (firstTime) {
            if (ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY) &&
                    !NetworkManager.instance().isTunnelUp()) {
                UIUtils.showDismissableMessage(getWindow().getDecorView().getRootView(), R.string.cannot_start_engine_without_vpn);
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
        } else {
            // TODO:
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == StoragePicker.SELECT_FOLDER_REQUEST_CODE) {
            StoragePicker.handle(this, requestCode, resultCode, data);
        } else if (requestCode == MainActivity.PROMO_VIDEO_PREVIEW_RESULT_CODE) {
            Offers.showInterstitialOfferIfNecessary(this, Offers.PLACEMENT_INTERSTITIAL_TRANSFERS, false, false);
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
        if (drawerLayout.isDrawerOpen(leftDrawer)) {
            drawerLayout.closeDrawer(leftDrawer);
        } else {
            drawerLayout.openDrawer(leftDrawer);
            syncSlideMenu();
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

    private void showShutdownDialog() {
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
        Offers.showInterstitial(this, Offers.PLACEMENT_INTERSTITIAL_HOME, false, true);
    }

    private void onShutdownDialogButtonPositive() {
        Offers.showInterstitial(this, Offers.PLACEMENT_INTERSTITIAL_EXIT, true, false);
    }

    private void syncSlideMenu() {
        listMenu.clearChoices();
        invalidateOptionsMenu();

        Fragment fragment = getCurrentFragment();
        int menuId = R.id.menu_main_search;
        if (fragment instanceof SearchFragment) {
            menuId = R.id.menu_main_search;
        } else if (fragment instanceof BrowsePeerFragment) {
            menuId = R.id.menu_main_library;
        } else if (fragment instanceof TransfersFragment) {
            menuId = R.id.menu_main_transfers;
        }
        setCheckedItem(menuId);
    }

    private void setCheckedItem(int id) {
        try {
            listMenu.clearChoices();
            ((MainMenuAdapter) listMenu.getAdapter()).notifyDataSetChanged();

            int position = 0;
            MainMenuAdapter adapter = (MainMenuAdapter) listMenu.getAdapter();
            for (int i = 0; i < adapter.getCount(); i++) {
                listMenu.setItemChecked(i, false);
                if (adapter.getItemId(i) == id) {
                    position = i;
                    break;
                }
            }

            if (id != -1) {
                listMenu.setItemChecked(position, true);
            }

            invalidateOptionsMenu();

            if (drawerToggle != null) {
                drawerToggle.syncState();
            }
        } catch (Throwable e) { // protecting from weird android UI engine issues
            LOG.warn("Error setting slide menu item selected", e);
        }
    }

    private void refreshPlayerItem() {
        if (playerItem != null) {
            playerItem.refresh();
        }
    }

    private void refreshMenuRemoveAdsItem() {
        // only visible for basic or debug build
        int visibility = View.GONE;
        if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION || Constants.IS_BASIC_AND_DEBUG) {
            // if they haven't paid for ads
            if (!Offers.disabledAds() &&
                    (playerItem == null || playerItem.getVisibility() == View.GONE)) {
                visibility = View.VISIBLE;
            }
        }
        menuRemoveAdsItem.setVisibility(visibility);
    }

    private void setupMenuItems() {
        listMenu.setAdapter(new MainMenuAdapter(this));
        listMenu.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        listMenu.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //onItemClick(AdapterView<?> parent, View view, int position, long id)
                syncSlideMenu();
                Engine.instance().getVibrator().hapticFeedback();
                controller.closeSlideMenu();
                try {
                    if (id == R.id.menu_main_settings) {
                        controller.showPreferences();
                    } else if (id == R.id.menu_main_shutdown) {
                        showShutdownDialog();
                    } else if (id == R.id.menu_main_my_music) {
                        controller.launchMyMusic();
                    } else if (id == R.id.menu_main_support) {
                        UIUtils.openURL(MainActivity.this, Constants.SUPPORT_URL);
                    } else {
                        listMenu.setItemChecked(position, true);
                        controller.switchFragment((int) id);
                    }
                } catch (Throwable e) { // protecting from weird android UI engine issues
                    LOG.error("Error clicking slide menu item", e);
                }
            }
        });
    }

    private void setupFragments() {
        search = (SearchFragment) getFragmentManager().findFragmentById(R.id.activity_main_fragment_search);
        library = (BrowsePeerFragment) getFragmentManager().findFragmentById(R.id.activity_main_fragment_browse_peer);
        transfers = (TransfersFragment) getFragmentManager().findFragmentById(R.id.activity_main_fragment_transfers);
        hideFragments(getFragmentManager().beginTransaction()).commit();
    }

    private FragmentTransaction hideFragments(FragmentTransaction ts) {
        return ts.hide(search).hide(library).hide(transfers);
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
                    setToolbarView(header, Gravity.CENTER);
                }
            }
        } catch (Throwable e) {
            LOG.error("Error updating main header", e);
        }
    }

    private void switchContent(Fragment fragment, boolean addToStack) {
        hideFragments(getFragmentManager().beginTransaction()).show(fragment).commitAllowingStateLoss();
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

    public Fragment getFragmentByMenuId(int id) {
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

    public void switchContent(Fragment fragment) {
        switchContent(fragment, true);
    }

    public Fragment getCurrentFragment() {
        return currentFragment;
    }

    public void closeSlideMenu() {
        drawerLayout.closeDrawer(leftDrawer);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle != null) {
            try {
                drawerToggle.onOptionsItemSelected(item);
            } catch (Throwable t) {
                // usually java.lang.IllegalArgumentException: No drawer view found with gravity LEFT
                return false;
            }
            return true;
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
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
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
        drawerLayout = findView(R.id.drawer_layout);
        Toolbar toolbar = findToolbar();
        drawerToggle = new MenuDrawerToggle(this, drawerLayout, toolbar);
        drawerLayout.setDrawerListener(drawerToggle);
    }

    public void onServiceConnected(final ComponentName name, final IBinder service) {
        musicPlaybackService = IApolloService.Stub.asInterface(service);
    }

    /**
     * {@inheritDoc}
     */
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

    private static final class MenuDrawerToggle extends ActionBarDrawerToggle {
        private final WeakReference<MainActivity> activityRef;

        MenuDrawerToggle(MainActivity activity, DrawerLayout drawerLayout, Toolbar toolbar) {
            super(activity, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close);

            // aldenml: even if the parent class holds a strong reference, I decided to keep a weak one
            this.activityRef = Ref.weak(activity);
        }

        @Override
        public void onDrawerClosed(View view) {
            if (Ref.alive(activityRef)) {
                activityRef.get().invalidateOptionsMenu();
                activityRef.get().syncSlideMenu();
            }
        }

        @Override
        public void onDrawerOpened(View drawerView) {
            if (Ref.alive(activityRef)) {
                UIUtils.hideKeyboardFromActivity(activityRef.get());
                activityRef.get().invalidateOptionsMenu();
                activityRef.get().syncSlideMenu();
            }
        }

        @Override
        public void onDrawerStateChanged(int newState) {
            if (Ref.alive(activityRef)) {
                MainActivity activity = activityRef.get();
                activity.refreshPlayerItem();
                activity.refreshMenuRemoveAdsItem();
                activity.syncSlideMenu();
            }
        }
    }

    public void performYTSearch(String ytUrl) {
        SearchFragment searchFragment = (SearchFragment) getFragmentByMenuId(R.id.menu_main_search);
        searchFragment.performYTSearch(ytUrl);
        switchContent(searchFragment);
    }

    // TODO: refactor and move this method for a common place when needed
    private static String saveViewContent(Context context, Uri uri, String name) {
        InputStream inStream = null;
        OutputStream outStream = null;
        if (!Platforms.temp().exists()) {
            Platforms.temp().mkdirs();
        }
        File target = new File(Platforms.temp(), name);
        try {
            inStream = context.getContentResolver().openInputStream(uri);
            outStream = new FileOutputStream(target);

            byte[] buffer = new byte[16384]; // MAGIC_NUMBER
            int bytesRead;
            if (inStream != null && outStream != null) {
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
