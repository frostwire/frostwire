/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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
import com.frostwire.search.relay.BTEngineListenerChain;
import com.frostwire.search.relay.BlockHeaderSource;
import com.frostwire.search.relay.DhtAdvertiser;
import com.frostwire.search.relay.DhtKarmaChainSource;
import com.frostwire.search.relay.DhtPeerDiscoverySource;
import com.frostwire.search.relay.DirectTcpPeerAuthenticator;
import com.frostwire.search.relay.HttpBlockHeaderFetcher;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IdentityRecord;
import com.frostwire.search.relay.IdentityRecordPublisher;
import com.frostwire.search.relay.IncomingRelayServer;
import com.frostwire.search.relay.IndexAnnouncementPublisher;
import com.frostwire.search.relay.KarmaChainCommitScheduler;
import com.frostwire.search.relay.KarmaChainPublisher;
import com.frostwire.search.relay.KarmaChainTable;
import com.frostwire.search.relay.KarmaChainWriter;
import com.frostwire.search.relay.KarmaEndorsementTrigger;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.LocalIndexTable;
import com.frostwire.search.relay.PeerAuthenticator;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerDiscovery;
import com.frostwire.search.relay.PeerDiscoveryScheduler;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.RelayRole;
import com.frostwire.search.relay.RelaySearchService;
import com.frostwire.search.relay.RemoteKarmaChainFetcher;
import com.frostwire.search.relay.SharedTorrentIndexerInstaller;
import com.frostwire.search.relay.icebridge.client.IceBridgeClient;
import com.frostwire.search.relay.icebridge.client.IceBridgeProcessLauncher;
import com.frostwire.search.relay.icebridge.client.IceBridgeSearchTransport;
import com.frostwire.search.relay.icebridge.client.IncomingSearchRequestHandler;
import com.frostwire.search.relay.icebridge.client.PeerRegistrySync;
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
import com.limegroup.gnutella.gui.search.DistributedSearchEngineWire;
import com.limegroup.gnutella.gui.search.IceBridgeUrlHandler;
import com.limegroup.gnutella.gui.search.LocalSearchEngineWire;
import com.limegroup.gnutella.settings.*;
import com.limegroup.gnutella.util.FrostWireUtils;
import com.limegroup.gnutella.util.MacOSXUtils;
import java.awt.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import org.limewire.util.CommonUtils;
import org.limewire.util.I18NConvert;
import org.limewire.util.NetworkUtils;

/** Initializes (creates, starts, & displays) the LimeWire Core & UI. */
final class Initializer {
  /** True if is running from a system startup. */
  private volatile boolean isStartup = false;

  Initializer() {}

  /**
   * Initializes all of the necessary application classes.
   *
   * <p>If this throws any exceptions, then LimeWire was not able to construct properly and must be
   * shut down.
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
    ThemeMediator.loadThemeAtStartup(saved);
    // Various startup tasks...
    // System.out.println("Initializer.initialize() setup callbacks and listeners");
    setupCallbacksAndListeners();
    validateStartup(args);
    // Creates LimeWire itself.
    // System.out.println("Initializer.initialize() create Limewire");
    LimeWireGUI limewireGUI = createLimeWire();
    LimeWireCore limeWireCore = limewireGUI.getLimeWireCore();
    // Various tasks that can be done after core is glued & started.
    // System.out.println("Initializer.initialize() glue core");
    glueCore(limeWireCore);
    // Validate any arguments or properties outside of the LW environment.
    // System.out.println("Initializer.initialize() run external checks");
    runExternalChecks(limeWireCore, args);
    limeWireCore.getExternalControl().startServer();
    // Starts some system monitoring for deadlocks.
    // System.out.println("Initializer.initialize() monitor deadlocks");
    DeadlockSupport.startDeadlockMonitoring();
    // stopwatch.resetAndLog("Start deadlock monitor");
    // Installs properties & resources.
    // System.out.println("Initializer.initialize() install properties");
    installProperties();
    installResources();
    // Construct the SetupManager, which may or may not be shown.
    final SetupManager setupManager = new SetupManager();
    // stopwatch.resetAndLog("construct SetupManager");
    // Move from the AWT splash to the Swing splash & start early core.
    // System.out.println("Initializer.initialize() switch splashes");
    switchSplashes(awtSplash);
    startEarlyCore(limeWireCore);
    // Initialize early UI components, display the setup manager (if necessary),
    // and ensure the save directory is valid.
    // System.out.println("Initializer.initialize() init early UI");
    initializeEarlyUI();
    startSetupManager(setupManager);
    startBittorrentCore();
    com.frostwire.mcp.desktop.MCPStartupHook.initialize();
    // Load the UI, system tray & notification handlers,
    // and hide the splash screen & display the UI.
    // System.out.println("Initializer.initialize() load UI");
    loadUI();
    loadTrayAndNotifications();
    hideSplashAndShowUI();
    // Initialize late tasks, like Icon initialization & install listeners.
    loadLateTasksForUI();
    // Start the core & run any queued control requests, and load DAAP.
    // System.out.println("Initializer.initialize() start core");
    startRelayStack();
    IceBridgeUrlHandler.register();
    startCore(limeWireCore);
    runQueuedRequests(limeWireCore);
    if (OSUtils.isMacOSX()) {
      GURLHandler.getInstance().register();
      MacEventHandler.instance();
    }
    // Run any after-init tasks.
    postinit();
  }

  /** Initializes the very early things. */
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

  /** Installs all callbacks & listeners. */
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
   * Ensures this should continue running, by checking for expiration failures or startup settings.
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
    if (args.length >= 1 && "-startup".equals(args[0])) isStartup = true;
    if (isStartup) {
      // if the user doesn't want to start on system startup, exit the
      // JVM immediately
      if (!StartupSettings.RUN_ON_STARTUP.getValue()) System.exit(0);
    }
  }

  /** Wires together LimeWire. */
  private LimeWireGUI createLimeWire() {
    return LimeWireModule.instance().getLimeWireGUIModule().getLimeWireGUI();
  }

  /** Wires together remaining non-Guiced pieces. */
  private void glueCore(LimeWireCore limeWireCore) {
    limeWireCore.getLimeCoreGlue().install();
  }

  /**
   * Initializes any code that is dependent on external controls. Specifically, GURLHandler &
   * MacEventHandler on OS X, ensuring that multiple LimeWire's can't run at once, and processing
   * any arguments that were passed to LimeWire.
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

  /** Installs any system properties. */
  private void installProperties() {
    System.setProperty("http.agent", UserAgentGenerator.getUserAgent());
    if (OSUtils.isMacOSX()) {
      System.setProperty("apple.laf.useScreenMenuBar", "true");
    }
  }

  /** Sets up ResourceManager. */
  private void installResources() {
    GUIMediator.safeInvokeAndWait(ResourceManager::instance);
  }

  /** Starts any early core-related functionality. */
  private void startEarlyCore(LimeWireCore limeWireCore) {
    // Add this running program to the Windows Firewall Exceptions list
    boolean inFirewallException = FirewallUtils.addToFirewall();
    // stopwatch.resetAndLog("add firewall exception");
    if (!inFirewallException) {
      limeWireCore.getLifecycleManager().loadBackgroundTasks();
      // stopwatch.resetAndLog("load background tasks");
    }
  }

  /** Switches from the AWT splash to the Swing splash. */
  private void switchSplashes(Frame awtSplash) {
    GUIMediator.safeInvokeAndWait(
        () -> {
          // Show the splash screen if we're not starting automatically on
          // system startup
          if (!isStartup) {
            SplashWindow.instance().begin();
            // stopwatch.resetAndLog("begin splash window");
          }
        });
    if (awtSplash != null) {
      awtSplash.dispose();
      // stopwatch.resetAndLog("dispose AWT splash");
    }
  }

  /** Initializes any early UI tasks, such as HTML loading and the Bug Manager. */
  private void initializeEarlyUI() {
    // Pre-load locales to avoid EDT violations when locales are later accessed on the EDT.
    // This must be done before SetupManager creates the LanguagePanel
    LanguageUtils.preloadLocales();
    // Pre-load flag images for all locales to avoid blocking the EDT during combo box layout
    LanguageFlagFactory.preloadFlags(LanguageUtils.getLocales(null));
    // Pre-load header button background images to avoid MediaTracker blocking the EDT during
    // MainFrame construction
    ApplicationHeader.preloadImages();

    // Load up the HTML engine.
    GUIMediator.setSplashScreenString(I18n.tr("Loading HTML Engine..."));
    // stopwatch.resetAndLog("update splash for HTML engine");
    GUIMediator.safeInvokeAndWait(
        () -> {
          // stopwatch.resetAndLog("enter evt queue");
          JLabel label = new JLabel();
          // setting font and color to null to minimize generated css
          // script
          // which causes a parser exception under circumstances
          label.setFont(null);
          label.setForeground(null);
          BasicHTML.createHTMLView(label, "<html>.</html>");
          // stopwatch.resetAndLog("create HTML view");
        });
    // stopwatch.resetAndLog("return from evt queue");
    // Initialize the bug manager
    BugManager.instance();
    // stopwatch.resetAndLog("BugManager instance");
  }

  /** Starts the SetupManager, if necessary. */
  private void startSetupManager(final SetupManager setupManager) {
    // Run through the initialization sequence -- this must always be
    // called before GUIMediator constructs the LibraryTree!
    GUIMediator.safeInvokeAndWait(setupManager::createIfNeeded);
  }

  /** Loads the UI. */
  private void loadUI() {
    GUIMediator.setSplashScreenString(I18n.tr("Loading User Interface..."));
    GUIMediator.safeInvokeAndWait(GUIMediator::instance);
    GUIMediator.setSplashScreenString(I18n.tr("Loading Core Components..."));
  }

  /** Loads the system tray & other notifications. */
  private void loadTrayAndNotifications() {
    // Create the user desktop notifier object.
    // This must be done before the GUI is made visible,
    // otherwise the user can close it and not see the
    // tray icon.
    GUIMediator.safeInvokeAndWait(
        () -> {
          // stopwatch.resetAndLog("enter evt queue");
          NotifyUserProxy.instance();
          if (!ApplicationSettings.DISPLAY_TRAY_ICON.getValue())
            NotifyUserProxy.instance().hideTrayIcon();
          SettingsWarningManager.checkSettingsLoadSaveFailure();
        });
  }

  /** Hides the splash screen and sets the UI for allowing viz. */
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

  /** Runs any late UI tasks, such as initializing Icons, I18n support. */
  private void loadLateTasksForUI() {
    // Initialize IconManager on the main thread to avoid EDT blocking.
    // IconManager constructor is lightweight (creates BasicFileIconController
    // and defers native icon loading via invokeLater).
    GUIMediator.setSplashScreenString(I18n.tr("Loading Icons..."));
    IconManager.instance();
    GUIMediator.safeInvokeAndWait(
        () -> {
          GUIMediator.setSplashScreenString(I18n.tr("Loading Internationalization Support..."));
        });
    I18NConvert.instance();
  }

  /** Starts the core. */
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

  /**
   * Wires the distributed-search direct peer-search stack: opens the local torrent index, loads (or
   * generates) the node's cryptographic identity, installs the auto-indexer on BTEngine, opens the
   * karma chain table, wires download-completion endorsements to a Bitcoin-anchored karma chain,
   * starts the periodic commit-and-publish scheduler, hands the index to the LOCAL search engine so
   * user searches can query it, and starts the direct peer-search server so peers can query our
   * index over plain TCP.
   *
   * <p>Runs before {@link #startCore(LimeWireCore)} so the indexer is already installed when saved
   * downloads are restored and their {@code downloadAdded} events fire. {@code
   * BTEngineListenerChain.install} appends the {@code DownloadManagerImpl} listener later without
   * disturbing the indexer.
   */
  private void startRelayStack() {
    com.frostwire.util.Logger relayLog = com.frostwire.util.Logger.getLogger(Initializer.class);
    try {
      File homeDir =
          com.frostwire.search.relay.RelayConstants.relayHomeDir(CommonUtils.getUserSettingsDir());
      relayLog.info("Relay stack home dir: " + homeDir.getAbsolutePath());

      // 1. Open the local torrent index (SQLite + FTS5).
      File dbFile = new File(homeDir, LocalIndexTable.DEFAULT_DB_NAME);
      relayLog.info("Opening local index at: " + dbFile.getAbsolutePath());
      LocalIndexTable localIndex = LocalIndexTable.open(dbFile);
      relayLog.info("Local index opened, size=" + localIndex.size());

      // 2. Load or generate the node's Ed25519 / X25519 identity.
      File identityFile =
          com.frostwire.search.relay.RelayConstants.identityFile(CommonUtils.getUserSettingsDir());
      relayLog.info("Loading identity from: " + identityFile.getAbsolutePath());
      IdentityKeys identity = IdentityKeys.loadOrCreate(identityFile);
      relayLog.info("Identity loaded, nodeId=" + com.frostwire.util.Hex.encode(identity.nodeId()));

      // 3. Install the auto-indexer on BTEngine (chains onto any
      //    listener already present; DownloadManagerImpl is added later).
      BTEngine btEngine = BTEngine.getInstance();
      SharedTorrentIndexerInstaller.install(btEngine, localIndex, identity);
      relayLog.info("SharedTorrentIndexer installed");

      // 4. Open the karma chain table and wire download-completion
      //    endorsements to a Bitcoin-anchored chain.
      KarmaChainTable karmaTable = KarmaChainTable.open(dbFile);
      File bitcoinCacheDir =
          new File(homeDir, com.frostwire.search.relay.RelayConstants.BITCOIN_HEADER_CACHE_DIR);
      BlockHeaderSource blockSource = new HttpBlockHeaderFetcher(bitcoinCacheDir);
      KarmaChainWriter karmaWriter = new KarmaChainWriter(identity, blockSource, karmaTable);
      BTEngineListenerChain.install(
          btEngine, new KarmaEndorsementTrigger(localIndex, identity.ed25519PubRaw(), karmaWriter));

      // 5. Start the periodic commit-and-publish scheduler so the
      //    chain advances and stays visible to peers even when no
      //    downloads are happening.
      KarmaChainPublisher karmaPublisher = new KarmaChainPublisher(karmaWriter, identity);
      new KarmaChainCommitScheduler(
              karmaWriter,
              karmaPublisher,
              com.frostwire.search.relay.RelayConstants.KARMA_COMMIT_INTERVAL_SEC)
          .start();

      // 6. Wire the karma cache into the LOCAL search engine so
      //    user searches can weight results by the publisher's karma.
      RemoteKarmaChainFetcher karmaFetcher =
          new RemoteKarmaChainFetcher(new DhtKarmaChainSource(btEngine));
      PeerKarmaCache karmaCache = new PeerKarmaCache(karmaFetcher);
      LocalSearchEngineWire.setKarmaCache(karmaCache);

      // 7. Hand the index to the LOCAL search engine.
      LocalSearchEngineWire.setIndex(localIndex);

      // 8. Construct the shared peer directory (used by both
      //    the direct peer-search server's role and the discovery scheduler)
      //    and start the direct peer-search server. Discovered peers
      //    will be registered into this same directory.
      PeerDirectory directory = new PeerDirectory(karmaCache);
      startRelayServer(identity, localIndex, directory);

      // 9. Start the DHT advertiser so other FrostWire nodes can
      //    discover us: re-publishes our IdentityRecord (BEP 46)
      //    and announces under the BEP 5 peer topic.
      startDhtAdvertiser(btEngine, identity, localIndex);

      // 10. Start the peer discovery scheduler so we can
      //     discover other FrostWire nodes via BEP 5. Newly
      //     discovered endpoints are registered in the
      //     SHARED PeerDirectory with placeholder pubkeys
      //     (derived from SHA-256(host:port)). When a peer
      //     sends us a request, we learn their real pubkey
      //     and can upgrade the entry.
      startPeerDiscovery(directory, btEngine, identity);

      // Log IceBridge configuration (from settings) early. This shows what will be used
      // for any IceBridge child process launched this session. Env vars can still override.
      logIceBridgeConfiguration();

      // 11. Launch the local IceBridge daemon (or connect to remote), create the search
      //     transport, and wire the DISTRIBUTED search engine so the
      //     user can search both the local index and authenticated
      //     peers from the normal Search UI.
      if (SearchEnginesSettings.ICEBRIDGE_ENABLED.getValue()
          && SearchEnginesSettings.DISTRIBUTED_SEARCH_ENABLED.getValue()) {
        startIceBridgeSearch(localIndex, directory, identity, relayLog);
      } else {
        relayLog.info("IceBridge disabled via settings.");
      }
    } catch (Exception e) {
      // Non-fatal: the relay stack is optional; the app can run without it.
      relayLog.warn("Failed to start relay stack; distributed search disabled", e);
    }
  }

  /**
   * Launch the local IceBridge daemon as a subprocess, create the {@link
   * com.frostwire.search.relay.DistributedSearchTransport} that bridges the daemon's HTTP control
   * API to the search engine, register an incoming-request handler so remote peers can search our
   * index, and wire everything into the DISTRIBUTED search engine.
   *
   * <p>If the IceBridge jar is not found or the daemon fails to start, the DISTRIBUTED engine is
   * left un-wired (not ready). The rest of FrostWire — LOCAL search, karma, DHT — still works.
   */
  /**
   * Logs the current IceBridge configuration from settings (and notes env override potential).
   * Called on startup.
   */
  private static void logIceBridgeConfiguration() {
    com.frostwire.util.Logger log = com.frostwire.util.Logger.getLogger(Initializer.class);
    log.info("=== IceBridge Configuration ===");
    log.info(
        "  ICEBRIDGE_ENABLED             = " + SearchEnginesSettings.ICEBRIDGE_ENABLED.getValue());
    log.info(
        "  ICEBRIDGE_USE_REMOTE          = "
            + SearchEnginesSettings.ICEBRIDGE_USE_REMOTE.getValue());
    log.info(
        "  ICEBRIDGE_REMOTE_URL          = "
            + SearchEnginesSettings.ICEBRIDGE_REMOTE_URL.getValue());
    boolean hasRemoteToken =
        !SearchEnginesSettings.ICEBRIDGE_REMOTE_AUTH_TOKEN.getValue().isEmpty();
    log.info("  ICEBRIDGE_REMOTE_AUTH_TOKEN   = " + (hasRemoteToken ? "[set]" : "(empty)"));
    log.info(
        "  ICEBRIDGE_BIND_HOST           = "
            + SearchEnginesSettings.ICEBRIDGE_BIND_HOST.getValue());
    log.info(
        "  ICEBRIDGE_RUDP_PORT           = "
            + SearchEnginesSettings.ICEBRIDGE_RUDP_PORT.getValue());
    log.info(
        "  ICEBRIDGE_RELAY_LISTEN_PORT   = "
            + SearchEnginesSettings.ICEBRIDGE_RELAY_LISTEN_PORT.getValue());
    log.info(
        "  ICEBRIDGE_ROLE                = " + SearchEnginesSettings.ICEBRIDGE_ROLE.getValue());
    log.info(
        "  ICEBRIDGE_CONTROL_HTTP_PORT   = "
            + SearchEnginesSettings.ICEBRIDGE_CONTROL_HTTP_PORT.getValue());
    log.info("  (Env vars ICEBRIDGE_* can override some of the above at process launch time)");
    log.info("===============================");
  }

  private void startIceBridgeSearch(
      LocalIndex localIndex,
      PeerDirectory directory,
      IdentityKeys identity,
      com.frostwire.util.Logger relayLog) {
    try {
      IceBridgeClient client;

      // Settings take precedence; env vars allow advanced override (e.g. testing).
      boolean useRemote = SearchEnginesSettings.ICEBRIDGE_USE_REMOTE.getValue();
      String remoteUrl = System.getenv("ICEBRIDGE_REMOTE_URL");
      if (remoteUrl == null || remoteUrl.isEmpty()) {
        remoteUrl = SearchEnginesSettings.ICEBRIDGE_REMOTE_URL.getValue();
      }
      String token = System.getenv("ICEBRIDGE_AUTH_TOKEN");
      if (token == null || token.isEmpty()) {
        token = SearchEnginesSettings.ICEBRIDGE_REMOTE_AUTH_TOKEN.getValue();
      }

      int effectiveRudpPort = SearchEnginesSettings.ICEBRIDGE_RUDP_PORT.getValue();
      String envRudp = System.getenv("ICEBRIDGE_RUDP_PORT");
      if (envRudp != null && !envRudp.isEmpty()) {
        try {
          effectiveRudpPort = Integer.parseInt(envRudp);
        } catch (NumberFormatException ignored) {
        }
      }

      if (useRemote && remoteUrl != null && !remoteUrl.isEmpty()) {
        // Support talking directly to a remote IceBridge relay (e.g. standalone launched
        // separately).
        // Desktop will use the remote as its IceBridge backend instead of forking a local daemon.
        client = new IceBridgeClient(remoteUrl);
        if (token != null && !token.isEmpty()) {
          client.setAuthToken(token);
        }
        relayLog.info("Using remote IceBridge at " + remoteUrl + " (no local subprocess)");
        relayLog.info(
            "  (remote auth token "
                + (token != null && !token.isEmpty() ? "provided" : "not set")
                + ")");
        // Assume user ensures the remote is healthy.
      } else {
        File jarPath = resolveIceBridgeJar();
        if (jarPath == null) {
          relayLog.warn(
              "IceBridge jar not found; distributed search disabled. "
                  + "Run ./gradlew icebridgeJar to build it.");
          return;
        }

        // Share the relay identity with the IceBridge daemon so that
        // the Ed25519 pubkey used for DHT discovery is the same one
        // the daemon uses for rUDP routing.
        File identityFile =
            com.frostwire.search.relay.RelayConstants.identityFile(
                CommonUtils.getUserSettingsDir());

        String bindHost = SearchEnginesSettings.ICEBRIDGE_BIND_HOST.getValue();

        // Respect role from settings
        String role = SearchEnginesSettings.ICEBRIDGE_ROLE.getValue();
        if (role == null || role.isEmpty()) role = "BOTH";

        int relayListenPort = SearchEnginesSettings.ICEBRIDGE_RELAY_LISTEN_PORT.getValue();
        IceBridgeProcessLauncher launcher =
            new IceBridgeProcessLauncher(
                jarPath, identityFile, 0, effectiveRudpPort, relayListenPort, role, bindHost);
        launcher.start();
        relayLog.info("IceBridge daemon (local child) started:");
        relayLog.info("  controlPort=" + launcher.controlPort() + " (auto-assigned)");
        relayLog.info("  rudpPort=" + launcher.rudpPort());
        relayLog.info("  relayPort=" + launcher.relayPort() + " (identity)");
        relayLog.info("  bindHost=" + bindHost + " role=" + role);

        // Wait for the daemon to become healthy (up to 15s).
        client = launcher.client();
        boolean healthy = false;
        for (int i = 0; i < 150; i++) {
          if (client.health()) {
            healthy = true;
            break;
          }
          if (!launcher.isAlive()) {
            relayLog.warn("IceBridge process exited before becoming healthy");
            return;
          }
          Thread.sleep(100);
        }
        if (!healthy) {
          relayLog.warn(
              "IceBridge daemon did not become healthy in time; " + "distributed search disabled");
          launcher.close();
          return;
        }

        effectiveRudpPort = launcher.rudpPort(); // in case auto

        // Now that the child is healthy, add our IceBridge relay endpoint (the one others will
        // connect to for identity) to the host cache so it appears in the UI table.
        addSelfToIceBridgeHostCache("127.0.0.1", launcher.relayPort(), role);
      }

      // Create the transport and start the background poller.
      IceBridgeSearchTransport transport = new IceBridgeSearchTransport(client);
      transport.start();

      // Register an incoming-request handler so remote peers can
      // search our local index through IceBridge.
      RelaySearchService searchService =
          new RelaySearchService(
              localIndex,
              identity,
              com.frostwire.gui.bittorrent.BtTransferShareVisibility.INSTANCE);
      IncomingSearchRequestHandler incomingHandler =
          new IncomingSearchRequestHandler(
              transport, searchService, directory, identity, localIndex);
      incomingHandler.start();

      // Sync PeerDirectory ↔ IceBridge mesh: push verified peers, pull mesh
      // registry into directory (forwarder-first discovery), register self.
      String advertiseHost = System.getenv("ICEBRIDGE_ADVERTISE_HOST");
      if (advertiseHost == null || advertiseHost.isEmpty()) {
        advertiseHost = System.getProperty("frostwire.icebridge.advertiseHost", "");
      }
      if (advertiseHost == null || advertiseHost.isEmpty()) {
        advertiseHost =
            com.frostwire.search.relay.RelayConstants.RELAY_LISTEN_PORT > 0
                ? java.net.InetAddress.getLocalHost().getHostAddress()
                : "127.0.0.1";
      }
      String roleStr = SearchEnginesSettings.ICEBRIDGE_ROLE.getValue();
      com.frostwire.search.relay.icebridge.IceBridgeConfig.Role syncRole =
          com.frostwire.search.relay.icebridge.IceBridgeConfig.Role.BOTH;
      try {
        if (roleStr != null && !roleStr.isEmpty()) {
          syncRole =
              com.frostwire.search.relay.icebridge.IceBridgeConfig.Role.valueOf(
                  roleStr.toUpperCase());
        }
      } catch (IllegalArgumentException ignored) {
      }
      PeerRegistrySync peerSync =
          new PeerRegistrySync(
              client, directory, advertiseHost, effectiveRudpPort, identity, syncRole);
      peerSync.start();
      relayLog.info(
          "PeerRegistrySync advertiseHost="
              + advertiseHost
              + " rudpPort="
              + effectiveRudpPort
              + " role="
              + syncRole);

      // Wire the DISTRIBUTED search engine.
      DistributedSearchEngineWire.wire(localIndex, directory, identity, transport);
      boolean usingRemote = useRemote && remoteUrl != null && !remoteUrl.isEmpty();
      relayLog.info(
          "Relay stack ready; Distributed search engine wired via IceBridge"
              + (usingRemote ? " (remote)" : " (local daemon)"));
    } catch (Throwable t) {
      relayLog.warn("Failed to start IceBridge; distributed search disabled", t);
    }
  }

  /**
   * Resolve the path to the IceBridge fat JAR.
   *
   * <p>Search order:
   *
   * <ol>
   *   <li>{@code frostwire.icebridge.jar} system property
   *   <li>{@code build/libs/icebridge.jar} relative to the working directory (dev mode)
   *   <li>{@code icebridge.jar} in the user settings directory (production)
   * </ol>
   *
   * @return the jar file, or {@code null} if not found
   */
  private static File resolveIceBridgeJar() {
    String prop = System.getProperty("frostwire.icebridge.jar");
    if (prop != null && !prop.isEmpty()) {
      File f = new File(prop);
      if (f.isFile()) {
        return f;
      }
    }
    File devJar = new File("build/libs/icebridge.jar");
    if (devJar.isFile()) {
      return devJar;
    }
    File prodJar = new File(CommonUtils.getUserSettingsDir(), "icebridge.jar");
    if (prodJar.isFile()) {
      return prodJar;
    }
    return null;
  }

  /**
   * Construct a DHT advertiser and start it on a daemon executor. The uTP port we advertise is the
   * rUDP port, and the relay listen port (configurable) so peers can find our TCP endpoint via BEP
   * 5 and our identity record via BEP 46.
   */
  private void startDhtAdvertiser(BTEngine btEngine, IdentityKeys identity, LocalIndex localIndex) {
    try {
      int port = SearchEnginesSettings.ICEBRIDGE_RELAY_LISTEN_PORT.getValue();
      int rudpPort = SearchEnginesSettings.ICEBRIDGE_RUDP_PORT.getValue();
      String envRudp = System.getenv("ICEBRIDGE_RUDP_PORT");
      if (envRudp != null && !envRudp.isEmpty()) {
        try {
          rudpPort = Integer.parseInt(envRudp);
        } catch (NumberFormatException ignored) {
        }
      }
      IdentityRecordPublisher publisher =
          new IdentityRecordPublisher(identity, port, rudpPort, "BOTH");
      IndexAnnouncementPublisher indexPublisher =
          new IndexAnnouncementPublisher(localIndex, identity);
      // More aggressive DHT announcements so relayers and peers are found faster.
      new DhtAdvertiser(publisher, indexPublisher, 30).start();
    } catch (Throwable t) {
      com.frostwire.util.Logger.getLogger(Initializer.class)
          .warn("Failed to start DHT advertiser; node will not be discoverable", t);
    }
  }

  /**
   * Start the peer discovery scheduler so we can find other FrostWire nodes via BEP 5, authenticate
   * them via the direct TCP identity handshake, and populate the local {@link PeerDirectory}. By
   * this point BTEngine is already running (the indexer and karma trigger were installed in steps 3
   * and 4), so {@code btEngine} is the live DHT-capable session.
   */
  private void startPeerDiscovery(
      PeerDirectory directory, BTEngine btEngine, IdentityKeys ownIdentity) {
    try {
      DhtPeerDiscoverySource source = new DhtPeerDiscoverySource(btEngine);
      PeerAuthenticator authenticator = new DirectTcpPeerAuthenticator();
      byte[] ownPub = (ownIdentity != null) ? ownIdentity.ed25519PubRaw() : null;
      PeerDiscovery discovery = new PeerDiscovery(source, directory, authenticator, ownPub);

      // Host cache is UI/bootstrap history only. Do NOT upsert unverified
      // placeholders into PeerDirectory (would drive failed TCP auth spam).
      // Discovery still uses DHT + authenticators; successful relays re-enter
      // the host cache via PeerDiscovery.markSuccess.

      // Aggressive relay/peer discovery for faster mesh formation and seeing relayers.
      // Default was 5min; 60s makes it much more responsive for testing with standalone relays.
      new PeerDiscoveryScheduler(discovery, 30).start();
      // Immediate discovery tick so peers/relayers appear quickly instead of waiting for first
      // scheduled tick.
      try {
        discovery.discoverAndRegister();
      } catch (Exception ignored) {
      }
    } catch (Throwable t) {
      com.frostwire.util.Logger.getLogger(Initializer.class)
          .warn("Failed to start peer discovery; will not learn about other peers", t);
    }
  }

  /**
   * Construct the direct peer-search service + role + TCP server, and start listening. The server
   * is daemon-threaded and does not prevent JVM exit. If the listen port is already in use, the
   * failure is logged and the direct peer-search server is left disabled; the rest of the direct
   * peer-search stack still functions.
   */
  private void startRelayServer(
      IdentityKeys identity, LocalIndex localIndex, PeerDirectory directory) {
    try {
      int port = SearchEnginesSettings.ICEBRIDGE_RELAY_LISTEN_PORT.getValue();
      int rudpPort = SearchEnginesSettings.ICEBRIDGE_RUDP_PORT.getValue();
      RelaySearchService service =
          new RelaySearchService(
              localIndex,
              identity,
              com.frostwire.gui.bittorrent.BtTransferShareVisibility.INSTANCE);
      RelayRole role = new RelayRole(service, directory, identity);
      IdentityRecord identityRecord =
          IdentityRecord.createSigned(
              identity.nodeId(),
              identity.ed25519(),
              identity.x25519PubRaw(),
              port,
              rudpPort,
              "BOTH");
      IncomingRelayServer server = new IncomingRelayServer(role, identityRecord, port, "0.0.0.0");
      server.start();
      com.frostwire.util.Logger.getLogger(Initializer.class)
          .info("Direct peer-search server listening on 0.0.0.0:" + server.port());

      // Add our direct relay identity listener to the IceBridge host cache for visibility.
      addSelfToIceBridgeHostCache("127.0.0.1", port, "BOTH");
    } catch (java.io.IOException e) {
      com.frostwire.util.Logger.getLogger(Initializer.class)
          .warn(
              "Failed to start direct peer-search server on port "
                  + SearchEnginesSettings.ICEBRIDGE_RELAY_LISTEN_PORT.getValue()
                  + "; incoming direct peer-search requests will not be served",
              e);
    }
  }

  /**
   * Adds our own relay endpoint to the IceBridge host cache (so it shows in the settings table) and
   * attempts a local self-ping. This uses the direct TCP identity handshake protocol on the relay
   * port, *not* the IceBridge HTTP control API (which is what desktop uses locally to drive its
   * IceBridge daemon process).
   */
  private void addSelfToIceBridgeHostCache(String host, int port, String role) {
    if (port <= 0) return;
    try {
      var cache = com.frostwire.search.relay.icebridge.IceBridgeHostCache.getInstance();
      // Try a quick local TCP identity fetch (self-ping). If it works we mark success.
      try {
        com.frostwire.search.relay.OutgoingRelayClient client =
            new com.frostwire.search.relay.OutgoingRelayClient();
        var rec = client.fetchIdentity(host, port);
        if (rec.isPresent() && rec.get().verifySignature()) {
          cache.markSuccess(host, port, role);
          return;
        }
      } catch (Exception ignored) {
        // fall through to plain add
      }
      cache.addOrUpdate(host, port, role);
    } catch (Throwable ignored) {
      // best effort
    }
  }

  private void startBittorrentCore() {
    SharingSettings.initTorrentDataDirSetting();
    SharingSettings.initTorrentsDirSetting();
    File homeDir =
        com.frostwire.search.relay.RelayConstants.relayHomeDir(CommonUtils.getUserSettingsDir());
    if (!homeDir.exists()) {
      homeDir.mkdirs();
    }
    // We don't save the port we use, just the range, and this is done in
    // RouterConfigurationPaneItem.
    // We use this range to select a random port every time we apply the settings.
    int randomPortInRange =
        NetworkUtils.getPortInRange(
            ConnectionSettings.MANUAL_PORT_RANGE.getValue(),
            ConnectionSettings.PORT_RANGE_0.getDefaultValue(),
            ConnectionSettings.PORT_RANGE_1.getDefaultValue(),
            ConnectionSettings.PORT_RANGE_0.getValue(),
            ConnectionSettings.PORT_RANGE_1.getValue());
    String iface =
        NetworkUtils.getLibtorrentFormattedNetworkInterface(
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
    // I2P Configuration
    ctx.i2pEnabled = ConnectionSettings.I2P_ENABLED.getValue();
    ctx.i2pHostname = ConnectionSettings.I2P_HOSTNAME.getValue();
    ctx.i2pPort = ConnectionSettings.I2P_PORT.getValue();
    ctx.i2pAllowMixed = ConnectionSettings.I2P_ALLOW_MIXED.getValue();
    ctx.i2pInboundQuantity = ConnectionSettings.I2P_INBOUND_QUANTITY.getValue();
    ctx.i2pOutboundQuantity = ConnectionSettings.I2P_OUTBOUND_QUANTITY.getValue();
    ctx.i2pInboundLength = ConnectionSettings.I2P_INBOUND_LENGTH.getValue();
    ctx.i2pOutboundLength = ConnectionSettings.I2P_OUTBOUND_LENGTH.getValue();
    ctx.natpmpGateway = ConnectionSettings.NATPMP_GATEWAY.getValue();
    ctx.natpmpLeaseDuration = ConnectionSettings.NATPMP_LEASE_DURATION.getValue();
    ctx.allowMultipleConnectionsPerPid =
        ConnectionSettings.ALLOW_MULTIPLE_CONNECTIONS_PER_PID.getValue();
    FrostWireUtils.getFrostWireVersionBuild(ctx.version);
    BTEngine.ctx = ctx;
    BTEngine.onCtxSetupComplete();
    BTEngine btEngine = BTEngine.getInstance();
    btEngine.start();
    VPNStatusRefresher.getInstance().addRefreshListener(new VPNDropGuard());
  }

  /** Runs control requests that we queued early in initializing. */
  private void runQueuedRequests(LimeWireCore limeWireCore) {
    // Activate a download for magnet URL locally if one exists
    limeWireCore.getExternalControl().runQueuedControlRequest();
  }

  /** Runs post initialization tasks. */
  private void postinit() {
    // Tell the GUI that loading is all done.
    GUIMediator.instance().loadFinished();
  }

  /** Fails because preferences can't be set. */
  private void failPreferencesPermissions() {
    fail(
        I18n.tr(
            "FrostWire could not create a temporary preferences folder.\n\nThis is generally caused by a lack of permissions.  Please make sure that FrostWire (and you) have access to create files/folders on your computer.  If the problem persists, please visit www.frostwire.com and click the 'Support' link.\n\nFrostWire will now exit.  Thank You."));
  }

  /** Shows a msg & fails. */
  private void fail(final String msgKey) {
    try {
      SwingUtilities.invokeAndWait(
          () ->
              JOptionPane.showMessageDialog(
                  null,
                  new MultiLineLabel(I18n.tr(msgKey), 400),
                  I18n.tr("Error"),
                  JOptionPane.ERROR_MESSAGE));
    } catch (InterruptedException ignored) {
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) throw (RuntimeException) cause;
      if (cause instanceof Error) throw (Error) cause;
      throw new RuntimeException(cause);
    }
    System.exit(1);
  }
}
