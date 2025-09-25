/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.limegroup.gnutella.gui;

import com.frostwire.bittorrent.BTContext;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.gui.theme.ThemeMediator;
import com.frostwire.service.ErrorService;
import com.frostwire.util.OSUtils;
import com.frostwire.util.UserAgentGenerator;
import com.limegroup.gnutella.ExternalControl;
import com.limegroup.gnutella.LimeCoreGlue;
import com.limegroup.gnutella.LimeCoreGlue.InstallFailedException;
import com.limegroup.gnutella.LimeWireCore;
import com.limegroup.gnutella.gui.bugs.BugManager;
import com.limegroup.gnutella.gui.init.SetupManager;
import com.limegroup.gnutella.gui.notify.NotifyUserProxy;
import com.limegroup.gnutella.settings.*;
import com.limegroup.gnutella.util.FrostWireUtils;
import com.limegroup.gnutella.util.MacOSXUtils;
import org.limewire.util.CommonUtils;
import org.limewire.util.I18NConvert;
import org.limewire.util.NetworkUtils;

import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import java.awt.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;

/**
 * Initializes (creates, starts, & displays) the LimeWire Core & UI.
 */
final class Initializer {
    /**
     * True if is running from a system startup.
     */
    private volatile boolean isStartup = false;

    Initializer() {
    }

    /**
     * Initializes all of the necessary application classes.
     * <p>
     * If this throws any exceptions, then LimeWire was not able to construct
     * properly and must be shut down.
     */
    void initialize(String[] args, Frame awtSplash) {
        // ** THE VERY BEGINNING -- DO NOT ADD THINGS BEFORE THIS **
        preinit();
        // Apply the theme the user chose last time (falls back to DEFAULT)
        // IMPORTANT: Due to the introduction of the new light theme and
        // internal changes in ThemeMediator.ThemeEnum, we need to perform some
        // migration for old theme names to prevent crashes
        String uiThemeValue = UISettings.UI_THEME.getValue();
        if (uiThemeValue.equals("DARK")) {
            uiThemeValue = "DARK_FLAT_LAF";
            UISettings.UI_THEME.setValue(uiThemeValue);
        } else if (uiThemeValue.equals("DARK_LAF")) {
            uiThemeValue = "DARK_FLAT_LAF";
            UISettings.UI_THEME.setValue(uiThemeValue);
        } else if (uiThemeValue.equals("LIGHT_LAF")) {
            uiThemeValue = "LIGHT_FLAT_LAF";
            UISettings.UI_THEME.setValue(uiThemeValue);
        }
        
        // Safely parse theme enum with fallback to DEFAULT to prevent crashes
        // when user has unknown theme names from newer/older versions
        ThemeMediator.ThemeEnum saved;
        try {
            saved = ThemeMediator.ThemeEnum.valueOf(uiThemeValue);
        } catch (IllegalArgumentException e) {
            // Unknown theme enum, fallback to DEFAULT and update settings
            saved = ThemeMediator.ThemeEnum.DEFAULT;
            UISettings.UI_THEME.setValue(saved.name());
        }
        System.out.println("Initializer.initialize() applying theme: " + saved);
        ThemeMediator.switchTheme(saved);
        // Various startup tasks...
        //System.out.println("Initializer.initialize() setup callbacks and listeners");
        setupCallbacksAndListeners();
        validateStartup(args);
        // Creates LimeWire itself.
        //System.out.println("Initializer.initialize() create Limewire");
        LimeWireGUI limewireGUI = createLimeWire();
        LimeWireCore limeWireCore = limewireGUI.getLimeWireCore();
        // Various tasks that can be done after core is glued & started.
        //System.out.println("Initializer.initialize() glue core");
        glueCore(limeWireCore);
        // Validate any arguments or properties outside of the LW environment.
        //System.out.println("Initializer.initialize() run external checks");
        runExternalChecks(limeWireCore, args);
        limeWireCore.getExternalControl().startServer();
        // Starts some system monitoring for deadlocks.
        //System.out.println("Initializer.initialize() monitor deadlocks");
        DeadlockSupport.startDeadlockMonitoring();
        //stopwatch.resetAndLog("Start deadlock monitor");
        // Installs properties & resources.
        //System.out.println("Initializer.initialize() install properties");
        installProperties();
        installResources();
        // Construct the SetupManager, which may or may not be shown.
        final SetupManager setupManager = new SetupManager();
        //stopwatch.resetAndLog("construct SetupManager");
        // Move from the AWT splash to the Swing splash & start early core.
        //System.out.println("Initializer.initialize() switch splashes");
        switchSplashes(awtSplash);
        startEarlyCore(limeWireCore);
        // Initialize early UI components, display the setup manager (if necessary),
        // and ensure the save directory is valid.
        //System.out.println("Initializer.initialize() init early UI");
        initializeEarlyUI();
        startSetupManager(setupManager);
        startBittorrentCore();
        // Load the UI, system tray & notification handlers,
        // and hide the splash screen & display the UI.
        //System.out.println("Initializer.initialize() load UI");
        loadUI();
        loadTrayAndNotifications();
        hideSplashAndShowUI();
        // Initialize late tasks, like Icon initialization & install listeners.
        loadLateTasksForUI();
        // Start the core & run any queued control requests, and load DAAP.
        //System.out.println("Initializer.initialize() start core");
        startCore(limeWireCore);
        runQueuedRequests(limeWireCore);
        if (OSUtils.isMacOSX()) {
            GURLHandler.getInstance().register();
            MacEventHandler.instance();
        }
        // Run any after-init tasks.
        postinit();
    }

    /**
     * Initializes the very early things.
     */
    /*
     * DO NOT CHANGE THIS WITHOUT KNOWING WHAT YOU'RE DOING.
     * PREINSTALL MUST BE DONE BEFORE ANYTHING ELSE IS REFERENCED.
     * (Because it sets the preference directory in CommonUtils.)
     */
    private void preinit() {
        // Make sure the settings directory is set.
        try {
            LimeCoreGlue.preinstall();
        } catch (InstallFailedException ife) {
            failPreferencesPermissions();
        }
    }

    /**
     * Installs all callbacks & listeners.
     */
    private void setupCallbacksAndListeners() {
        // Set the error handler so we can receive core errors.
        ErrorService.setErrorCallback(new ErrorHandler());
        // Set the messaging handler so we can receive core messages
        com.frostwire.service.MessageService.setCallback(new MessageHandler());
        // Set the default event error handler so we can receive uncaught
        // AWT errors.
        DefaultErrorCatcher.install();
    }

    /**
     * Ensures this should continue running, by checking
     * for expiration failures or startup settings.
     */
    private void validateStartup(String[] args) {
        // Yield so any other events can be run to determine
        // startup status, but only if we're going to possibly
        // be starting...
        if (StartupSettings.RUN_ON_STARTUP.getValue()) {
            Thread.yield();
        }
        if (OSUtils.isMacOSX()) {
            try {
                MacOSXUtils.setLoginStatus(StartupSettings.RUN_ON_STARTUP.getValue());
            } catch (Throwable ignored) {
                ignored.printStackTrace();
            }
        }
        if (args.length >= 1 && "-startup".equals(args[0]))
            isStartup = true;
        if (isStartup) {
            // if the user doesn't want to start on system startup, exit the
            // JVM immediately
            if (!StartupSettings.RUN_ON_STARTUP.getValue())
                System.exit(0);
        }
    }

    /**
     * Wires together LimeWire.
     */
    private LimeWireGUI createLimeWire() {
        return LimeWireModule.instance().getLimeWireGUIModule().getLimeWireGUI();
    }

    /**
     * Wires together remaining non-Guiced pieces.
     */
    private void glueCore(LimeWireCore limeWireCore) {
        limeWireCore.getLimeCoreGlue().install();
    }

    /**
     * Initializes any code that is dependent on external controls.
     * Specifically, GURLHandler & MacEventHandler on OS X,
     * ensuring that multiple LimeWire's can't run at once,
     * and processing any arguments that were passed to LimeWire.
     */
    private void runExternalChecks(LimeWireCore limeWireCore, String[] args) {
        ExternalControl externalControl = limeWireCore.getExternalControl();
        // Test for preexisting FrostWire and pass it a magnet URL if one
        // has been passed in.
        if (args.length > 0 && !args[0].equals("-startup")) {
            String arg = externalControl.preprocessArgs(args);
            externalControl.checkForActiveFrostWire(arg);
            externalControl.enqueueControlRequest(arg);
        } else if (!StartupSettings.ALLOW_MULTIPLE_INSTANCES.getValue()) {
            // if we don't want multiple instances, we need to check if
            // frostwire is already active.
            externalControl.checkForActiveFrostWire();
        }
    }

    /**
     * Installs any system properties.
     */
    private void installProperties() {
        System.setProperty("http.agent", UserAgentGenerator.getUserAgent());
        if (OSUtils.isMacOSX()) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
        }
    }

    /**
     * Sets up ResourceManager.
     */
    private void installResources() {
        GUIMediator.safeInvokeAndWait(ResourceManager::instance);
    }

    /**
     * Starts any early core-related functionality.
     */
    private void startEarlyCore(LimeWireCore limeWireCore) {
        // Add this running program to the Windows Firewall Exceptions list
        boolean inFirewallException = FirewallUtils.addToFirewall();
        //stopwatch.resetAndLog("add firewall exception");
        if (!inFirewallException) {
            limeWireCore.getLifecycleManager().loadBackgroundTasks();
            //stopwatch.resetAndLog("load background tasks");
        }
    }

    /**
     * Switches from the AWT splash to the Swing splash.
     */
    private void switchSplashes(Frame awtSplash) {
        GUIMediator.safeInvokeAndWait(() -> {
            // Show the splash screen if we're not starting automatically on
            // system startup
            if (!isStartup) {
                SplashWindow.instance().begin();
                //stopwatch.resetAndLog("begin splash window");
            }
        });
        if (awtSplash != null) {
            awtSplash.dispose();
            //stopwatch.resetAndLog("dispose AWT splash");
        }
    }

    /**
     * Initializes any early UI tasks, such as HTML loading and the Bug Manager.
     */
    private void initializeEarlyUI() {
        // Load up the HTML engine.
        GUIMediator.setSplashScreenString(I18n.tr("Loading HTML Engine..."));
        //stopwatch.resetAndLog("update splash for HTML engine");
        GUIMediator.safeInvokeAndWait(() -> {
            //stopwatch.resetAndLog("enter evt queue");
            JLabel label = new JLabel();
            // setting font and color to null to minimize generated css
            // script
            // which causes a parser exception under circumstances
            label.setFont(null);
            label.setForeground(null);
            BasicHTML.createHTMLView(label, "<html>.</html>");
            //stopwatch.resetAndLog("create HTML view");
        });
        //stopwatch.resetAndLog("return from evt queue");
        // Initialize the bug manager
        BugManager.instance();
        //stopwatch.resetAndLog("BugManager instance");
    }

    /**
     * Starts the SetupManager, if necessary.
     */
    private void startSetupManager(final SetupManager setupManager) {
        // Run through the initialization sequence -- this must always be
        // called before GUIMediator constructs the LibraryTree!
        GUIMediator.safeInvokeAndWait(setupManager::createIfNeeded);
    }

    /**
     * Loads the UI.
     */
    private void loadUI() {
        GUIMediator.setSplashScreenString(I18n.tr("Loading User Interface..."));
        GUIMediator.safeInvokeAndWait(GUIMediator::instance);
        GUIMediator.setSplashScreenString(I18n.tr("Loading Core Components..."));
    }

    /**
     * Loads the system tray & other notifications.
     */
    private void loadTrayAndNotifications() {
        // Create the user desktop notifier object.
        // This must be done before the GUI is made visible,
        // otherwise the user can close it and not see the
        // tray icon.
        GUIMediator.safeInvokeAndWait(() -> {
            //stopwatch.resetAndLog("enter evt queue");
            NotifyUserProxy.instance();
            if (!ApplicationSettings.DISPLAY_TRAY_ICON.getValue())
                NotifyUserProxy.instance().hideTrayIcon();
            SettingsWarningManager.checkSettingsLoadSaveFailure();
        });
    }

    /**
     * Hides the splash screen and sets the UI for allowing viz.
     */
    private void hideSplashAndShowUI() {
        // Hide the splash screen and recycle its memory.
        if (!isStartup) {
            SplashWindow.instance().dispose();
        }
        GUIMediator.allowVisibility();
        // Make the GUI visible.
        if (!isStartup) {
            GUIMediator.setAppVisible(true);
        } else {
            GUIMediator.startupHidden();
        }
    }

    /**
     * Runs any late UI tasks, such as initializing Icons, I18n support.
     */
    private void loadLateTasksForUI() {
        // Initialize IconManager.
        //GUIMediator.setSplashScreenString(I18n.tr("Loading Icons..."));
        GUIMediator.safeInvokeAndWait(() -> {
            GUIMediator.setSplashScreenString(I18n.tr("Loading Icons..."));
            IconManager.instance();
        });
        // Touch the I18N stuff to ensure it loads properly.
        GUIMediator.setSplashScreenString(I18n.tr("Loading Internationalization Support..."));
        I18NConvert.instance();
    }

    /**
     * Starts the core.
     */
    private void startCore(LimeWireCore limeWireCore) {
        // Start the backend threads.  Note that the GUI is not yet visible,
        // but it needs to be constructed at this point  
        limeWireCore.getLifecycleManager().start();
        // Instruct the gui to perform tasks that can only be performed
        // after the backend has been constructed.
        GUIMediator.instance().coreInitialized();
        GUIMediator.setSplashScreenString(I18n.tr("Loading Old Downloads..."));
        limeWireCore.getDownloadManager().loadSavedDownloadsAndScheduleWriting();
    }

    private void startBittorrentCore() {
        SharingSettings.initTorrentDataDirSetting();
        SharingSettings.initTorrentsDirSetting();
        File homeDir = new File(CommonUtils.getUserSettingsDir() + File.separator + "libtorrent" + File.separator);
        if (!homeDir.exists()) {
            homeDir.mkdirs();
        }
        // We don't save the port we use, just the range, and this is done in RouterConfigurationPaneItem.
        // We use this range to select a random port every time we apply the settings.
        int randomPortInRange = NetworkUtils.getPortInRange(
                ConnectionSettings.MANUAL_PORT_RANGE.getValue(),
                ConnectionSettings.PORT_RANGE_0.getDefaultValue(),
                ConnectionSettings.PORT_RANGE_1.getDefaultValue(),
                ConnectionSettings.PORT_RANGE_0.getValue(),
                ConnectionSettings.PORT_RANGE_1.getValue());
        String iface = NetworkUtils.getLibtorrentFormattedNetworkInterface(
                ConnectionSettings.USE_CUSTOM_NETWORK_INTERFACE.getValue(),
                "0.0.0.0",
                ConnectionSettings.CUSTOM_INETADRESS_NO_PORT.getValue(),
                randomPortInRange);
        BTContext ctx = new BTContext();
        ctx.homeDir = homeDir;
        ctx.torrentsDir = SharingSettings.TORRENTS_DIR_SETTING.getValue();
        ctx.dataDir = SharingSettings.TORRENT_DATA_DIR_SETTING.getValue();
        ctx.interfaces = iface;
        ctx.retries = 10;
        ctx.enableDht = SharingSettings.ENABLE_DISTRIBUTED_HASH_TABLE.getValue();
        FrostWireUtils.getFrostWireVersionBuild(ctx.version);
        BTEngine.ctx = ctx;
        BTEngine.onCtxSetupComplete();
        BTEngine btEngine = BTEngine.getInstance();
        btEngine.start();
        VPNStatusRefresher.getInstance().addRefreshListener(new VPNDropGuard());
    }

    /**
     * Runs control requests that we queued early in initializing.
     */
    private void runQueuedRequests(LimeWireCore limeWireCore) {
        // Activate a download for magnet URL locally if one exists
        limeWireCore.getExternalControl().runQueuedControlRequest();
    }

    /**
     * Runs post initialization tasks.
     */
    private void postinit() {
        // Tell the GUI that loading is all done.
        GUIMediator.instance().loadFinished();
    }

    /**
     * Fails because preferences can't be set.
     */
    private void failPreferencesPermissions() {
        fail(I18n.tr("FrostWire could not create a temporary preferences folder.\n\nThis is generally caused by a lack of permissions.  Please make sure that FrostWire (and you) have access to create files/folders on your computer.  If the problem persists, please visit www.frostwire.com and click the 'Support' link.\n\nFrostWire will now exit.  Thank You."));
    }

    /**
     * Shows a msg & fails.
     */
    private void fail(final String msgKey) {
        try {
            SwingUtilities.invokeAndWait(() -> JOptionPane.showMessageDialog(null,
                    new MultiLineLabel(I18n.tr(msgKey), 400),
                    I18n.tr("Error"),
                    JOptionPane.ERROR_MESSAGE));
        } catch (InterruptedException ignored) {
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException)
                throw (RuntimeException) cause;
            if (cause instanceof Error)
                throw (Error) cause;
            throw new RuntimeException(cause);
        }
        System.exit(1);
    }
}
