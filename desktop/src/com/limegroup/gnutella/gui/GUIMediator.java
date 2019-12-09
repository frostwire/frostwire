/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui;

import com.frostwire.gui.HideExitDialog;
import com.frostwire.gui.bittorrent.BTDownload;
import com.frostwire.gui.bittorrent.BTDownloadMediator;
import com.frostwire.gui.components.slides.Slide;
import com.frostwire.gui.library.LibraryMediator;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.player.MediaSource;
import com.frostwire.gui.tabs.Tab;
import com.frostwire.gui.tabs.TransfersTab;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.util.ThreadPool;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.actions.AbstractAction;
import com.limegroup.gnutella.gui.bugs.FatalBugManager;
import com.limegroup.gnutella.gui.notify.NotifyUserProxy;
import com.limegroup.gnutella.gui.options.OptionsMediator;
import com.limegroup.gnutella.gui.search.SearchMediator;
import com.limegroup.gnutella.gui.shell.FrostAssociations;
import com.limegroup.gnutella.gui.shell.ShellAssociationManager;
import com.limegroup.gnutella.settings.ApplicationSettings;
import com.limegroup.gnutella.settings.LibrarySettings;
import com.limegroup.gnutella.settings.QuestionsHandler;
import com.limegroup.gnutella.settings.StartupSettings;
import com.limegroup.gnutella.util.LaunchException;
import com.limegroup.gnutella.util.Launcher;
import org.limewire.concurrent.ThreadExecutor;
import org.limewire.service.ErrorService;
import org.limewire.service.Switch;
import org.limewire.setting.IntSetting;
import org.limewire.util.OSUtils;
import org.limewire.util.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class acts as a central point of access for all gui components, a sort
 * of "hub" for the frontend. This should be the only common class that all
 * frontend components have access to, reducing the overall dependencies and
 * therefore increasing the modularity of the code.
 * <p/>
 * <p/>
 * Any functions or services that should be accessible to multiple classes
 * should be added to this class. These currently include such functions as
 * easily displaying standardly-formatted messages to the user, obtaining
 * locale-specific strings, and obtaining image resources, among others.
 * <p/>
 * <p/>
 * All of the methods in this class should be called from the event- dispatch
 * (Swing) thread.
 */
public final class GUIMediator {
    /**
     * Message key for the disconnected message
     */
    private final static String DISCONNECTED_MESSAGE = I18n.tr("Your machine does not appear to have an active Internet connection or a firewall is blocking FrostWire from accessing the internet. FrostWire will automatically keep trying to connect you to the network unless you select \"Disconnect\" from the File menu.");
    /**
     * <tt>List</tt> of <tt>RefreshListener</tt> classes to notify of UI refresh
     * events.
     */
    private static final List<RefreshListener> REFRESH_LIST = new ArrayList<>();
    /**
     * String to be displayed in title bar of LW client.
     */
    private static final String APP_TITLE = I18n.tr("FrostWire: Share Big Files");
    /**
     * Flag for whether or not a message has been displayed to the user --
     * useful in deciding whether or not to display other dialogues.
     */
    private static boolean _displayedMessage;
    /**
     * Singleton for easy access to the mediator.
     */
    private static GUIMediator _instance;
    /**
     * The main <tt>JFrame</tt> for the application.
     */
    private static JFrame FRAME;
    /**
     * The popup menu on the icon in the system tray.
     */
    private static PopupMenu TRAY_MENU;
    /**
     * Handle to the <tt>OptionsMediator</tt> class that is responsible for
     * displaying customizable options to the user.
     */
    private static OptionsMediator OPTIONS_MEDIATOR;
    /**
     * The shell association manager.
     */
    private static ShellAssociationManager ASSOCIATION_MANAGER;
    /**
     * Flag for whether or not the app has ever been made visible during this
     * session.
     */
    private static boolean _visibleOnce = false;
    /**
     * Flag for whether or not the app is allowed to become visible.
     */
    private static boolean _allowVisible = false;
    private static final ThreadPool pool = new ThreadPool("GUIMediator-updateConnectionQuality", 1, 1, Integer.MAX_VALUE, new LinkedBlockingQueue<>(), true);
    private final RefreshTimer timer;
    private boolean _remoteDownloadsAllowed;
    /**
     * Constant handle to the <tt>MainFrame</tt> instance that handles
     * constructing all of the primary gui components.
     */
    private final MainFrame MAIN_FRAME;
    /**
     * Constant handle to the <tt>DownloadMediator</tt> class that is
     * responsible for displaying active downloads to the user.
     */
    private BTDownloadMediator BT_DOWNLOAD_MEDIATOR;
    /**
     * Constant handle to the <tt>DownloadView</tt> class that is responsible
     * for displaying the status of the network and connectivity to the user.
     */
    private StatusLine STATUS_LINE;
    private long _lastConnectivityCheckTimestamp;
    private boolean _wasInternetReachable;

    /**
     * Private constructor to ensure that this class cannot be constructed from
     * another class.
     */
    private GUIMediator() {
        MAIN_FRAME = new MainFrame(getAppFrame());
        OPTIONS_MEDIATOR = MAIN_FRAME.getOptionsMediator();
        _remoteDownloadsAllowed = true;
        this.timer = new RefreshTimer();
    }

    /**
     * Singleton accessor for this class.
     *
     * @return the <tt>GUIMediator</tt> instance
     */
    public static synchronized GUIMediator instance() {
        if (_instance == null)
            _instance = new GUIMediator();
        return _instance;
    }

    /**
     * Accessor for whether or not the GUIMediator has been constructed yet.
     */
    public static boolean isConstructed() {
        return _instance != null;
    }

    /**
     * Returns a boolean specifying whether or not the wrapped <tt>JFrame</tt>
     * is visible or not.
     *
     * @return <tt>true</tt> if the <tt>JFrame</tt> is visible, <tt>false</tt>
     * otherwise
     */
    public static boolean isAppVisible() {
        return getAppFrame().isShowing();
    }

    /**
     * Specifies whether or not the main application window should be visible or
     * not.
     *
     * @param visible specifies whether or not the application should be made
     *                visible or not
     */
    static void setAppVisible(final boolean visible) {
        safeInvokeLater(() -> {
            try {
                if (visible)
                    getAppFrame().toFront();
                getAppFrame().setVisible(visible);
            } catch (NullPointerException npe) {
                System.out.println("GUIMediator - NULL POINTER EXCEPTION HAPPENED");
                if (OSUtils.isNativeThemeWindows()) {
                    try {
                        GUIMediator
                                .showError(I18n
                                        .tr("FrostWire has encountered a problem during startup and cannot proceed. You may be able to fix this problem by changing FrostWire\'s Windows Compatibility. Right-click on the FrostWire icon on your Desktop and select \'Properties\' from the popup menu. Click the \'Compatibility\' tab at the top, then click the \'Run this program in compatibility mode for\' check box, and then select \'Windows 2000\' in the box below the check box. Then click the \'OK\' button at the bottom and restart FrostWire."));
                        System.exit(0);
                    } catch (Throwable t) {
                        if (visible)
                            FatalBugManager.handleFatalBug(npe);
                        else
                            ErrorService.error(npe);
                    }
                } else {
                    if (visible)
                        FatalBugManager.handleFatalBug(npe);
                    else
                        ErrorService.error(npe);
                }
            } catch (Throwable t) {
                if (visible)
                    FatalBugManager.handleFatalBug(t);
                else
                    ErrorService.error(t);
            }
            if (visible) {
                SearchMediator.requestSearchFocus();
                // forcibly revalidate the FRAME
                // after making it visible.
                // on Java 1.5, it does not validate correctly.
                SwingUtilities.invokeLater(() -> {
                    getAppFrame().getContentPane().invalidate();
                    getAppFrame().getContentPane().validate();
                });
            }
            // If the app has already been made visible, don't display extra
            // dialogs. We could display the pro dialog here, but it causes
            // some odd issues when LimeWire is brought back up from the
            // tray
            if (visible && !_visibleOnce) {
                // Show the startup dialogs in the swing thread.
                showDialogsForFirstVisibility();
                _visibleOnce = true;
            }
        });
    }

    /**
     * Displays various dialog boxes that should only be shown the first time
     * the application is made visible.
     */
    private static void showDialogsForFirstVisibility() {
        if (_displayedMessage)
            return;
        _displayedMessage = true;
        getAssociationManager().checkAndGrab(true);
        if (TipOfTheDayMessages.hasLocalizedMessages() && StartupSettings.SHOW_TOTD.getValue()) {
            // Construct it first...
            TipOfTheDayMediator.instance();
            ThreadExecutor.startThread(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
                SwingUtilities.invokeLater(() -> TipOfTheDayMediator.instance().displayTipWindow());
            }, "TOTD");
        }
    }

    /**
     * Closes any dialogues that are displayed at startup and sets the flag to
     * indicate that we've displayed a message.
     */
    static void closeStartupDialogs() {
        if (SplashWindow.instance().isShowing())
            SplashWindow.instance().toBack();
        if (TipOfTheDayMediator.isConstructed())
            TipOfTheDayMediator.instance().hide();
    }

    /**
     * Returns a <tt>Dimension</tt> instance containing the dimensions of the
     * wrapped JFrame.
     *
     * @return a <tt>Dimension</tt> instance containing the width and height of
     * the wrapped JFrame
     */
    static Dimension getAppSize() {
        return getAppFrame().getSize();
    }

    /**
     * Returns a <tt>Point</tt> instance containing the x, y position of the
     * wrapped <ttJFrame</tt> on the screen.
     *
     * @return a <tt>Point</tt> instance containing the x, y position of the
     * wrapped JFrame
     */
    static Point getAppLocation() {
        return getAppFrame().getLocation();
    }

    /**
     * Returns the main application <tt>JFrame</tt> instance.
     *
     * @return the main application <tt>JFrame</tt> instance
     */
    public static JFrame getAppFrame() {
        if (FRAME == null) {
            FRAME = new LimeJFrame();
            FRAME.setTitle(APP_TITLE);
        }
        return FRAME;
    }

    /**
     * Returns the popup menu on the icon in the system tray.
     *
     * @return The tray popup menu
     */
    public static PopupMenu getTrayMenu() {
        if (TRAY_MENU == null) {
            TRAY_MENU = new PopupMenu();
        }
        return TRAY_MENU;
    }

    /**
     * Returns whether or not the options window is visible
     *
     * @return <tt>true</tt> if the options window is visible, <tt>false</tt>
     * otherwise
     */
    static boolean isOptionsVisible() {
        return OPTIONS_MEDIATOR != null && OPTIONS_MEDIATOR.isOptionsVisible();
    }

    /**
     * Sets the visibility state of the options window.
     *
     * @param visible the visibility state to set the window to
     */
    public void setOptionsVisible(boolean visible) {
        if (OPTIONS_MEDIATOR == null)
            return;
        OPTIONS_MEDIATOR.setOptionsVisible(visible);
    }

    /**
     * Gets a handle to the options window main <tt>JComponent</tt> instance.
     *
     * @return the options window main <tt>JComponent</tt>, or <tt>null</tt> if
     * the options window has not yet been constructed (the window is
     * guaranteed to be constructed if it is visible)
     */
    static Component getMainOptionsComponent() {
        if (OPTIONS_MEDIATOR == null)
            return null;
        return OPTIONS_MEDIATOR.getMainOptionsComponent();
    }

    /**
     * @return the <tt>ShellAssociationManager</tt> instance.
     */
    public static ShellAssociationManager getAssociationManager() {
        if (ASSOCIATION_MANAGER == null) {
            ASSOCIATION_MANAGER = new ShellAssociationManager(FrostAssociations.getSupportedAssociations());
        }
        return ASSOCIATION_MANAGER;
    }

    /**
     * Determines whether or not the PlaylistMediator is being used this
     * session.
     */
    static boolean isPlaylistVisible() {
        return true;
    }

    /**
     * Runs the appropriate methods to start LimeWire up hidden.
     */
    static void startupHidden() {
        // sends us to the system tray on windows, ignored otherwise.
        GUIMediator.showTrayIcon();
        // If on OSX, we must set the frame state appropriately.
        if (OSUtils.isMacOSX())
            GUIMediator.hideView();
    }

    /**
     * Notification that visibility is now allowed.
     */
    static void allowVisibility() {
        _allowVisible = true;
    }

    /**
     * Handles a 'reopen' event appropriately. Used primarily for allowing
     * LimeWire to be made visible after it was started from system startup on
     * OSX.
     */
    static void handleReopen() {
        // Do not do anything
        // if visibility is not allowed yet, as initialization
        // is not yet finished.
        if (_allowVisible) {
            if (!_visibleOnce)
                restoreView(); // First make sure it's not minimized
            setAppVisible(true); // Then make it visible
            // Otherwise (if the above operations were reversed), a tiny
            // LimeWire icon would appear in the 'minimized' area of the dock
            // for a split second, and the Console would report strange errors
        }
    }

    /**
     * Hides the GUI by either sending it to the System Tray or minimizing the
     * window. Minimize behavior occurs on platforms which do not support the
     * System Tray.
     *
     * @see GUIMediator#restoreView()
     */
    private static void hideView() {
        getAppFrame().setState(Frame.ICONIFIED);
        if (OSUtils.supportsTray() && ResourceManager.instance().isTrayIconAvailable()) {
            GUIMediator.setAppVisible(false);
        }
    }

    /**
     * Makes the GUI visible by either restoring it from the System Tray or the
     * task bar.
     *
     * @see GUIMediator#hideView()
     */
    public static void restoreView() {
        // Frame must be visible for setState to work. Make visible
        // before restoring.
        if (OSUtils.supportsTray() && ResourceManager.instance().isTrayIconAvailable()) {
            // below is a little hack to get around odd windowing
            // behavior with the system tray on windows. This enables
            // us to get LimeWire to the foreground after it's run from
            // the startup folder with all the nice little animations
            // that we want
            // cache whether or not to use our little hack, since setAppVisible
            // changes the value of _visibleOnce
            boolean doHack = false;
            if (!_visibleOnce)
                doHack = true;
            GUIMediator.setAppVisible(true);
            if (ApplicationSettings.DISPLAY_TRAY_ICON.getValue())
                GUIMediator.showTrayIcon();
            else
                GUIMediator.hideTrayIcon();
            if (doHack)
                restoreView();
        }
        getAppFrame().setState(Frame.NORMAL);
    }

    /**
     * Determines the appropriate shutdown behavior based on user settings. This
     * implementation decides between exiting the application immediately, or
     * exiting after all file transfers in progress are complete.
     */
    public static void close(boolean fromFrame) {
        boolean minimizeToTray = ApplicationSettings.MINIMIZE_TO_TRAY.getValue();
        if (!OSUtils.isMacOSX() && ApplicationSettings.SHOW_HIDE_EXIT_DIALOG.getValue()) {
            HideExitDialog dlg = new HideExitDialog(getAppFrame());
            dlg.setVisible(true);
            int result = dlg.getResult();
            if (result == HideExitDialog.NONE) {
                return;
            } else {
                minimizeToTray = result == HideExitDialog.HIDE;
            }
        }
        if (minimizeToTray) {
            if (OSUtils.supportsTray()) {
                if (ResourceManager.instance().isTrayIconAvailable()) {
                    applyWindowSettings();
                    GUIMediator.showTrayIcon();
                    hideView();
                }
            }
        } else if (OSUtils.isMacOSX() && fromFrame) {
            // If on OSX, don't close in response to clicking on the 'X'
            // as that's not normal behavior. This can only be done on Java14
            // though, because we need access to the
            // com.apple.eawt.ApplicationListener.handleReOpenApplication event
            // in order to restore the GUI.
            GUIMediator.setAppVisible(false);
        } else {
            shutdown();
        }
    }

    /**
     * Shutdown the program cleanly.
     */
    public static void shutdown() {
        LibraryMediator.getLibrary().close();
        instance().timer.stopTimer(); // TODO: refactor this singleton pattern
        hideVideoPlayerWindow();
        Finalizer.shutdown();
    }

    private static void hideVideoPlayerWindow() {
        // hide video player if visible
        try {
            if (MPlayerMediator.instance() != null) {
                try {
                    MPlayerMediator.instance().showPlayerWindow(false);
                } catch (Throwable t) {
                    //we tried
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Shows the "About" menu with more information about the program.
     */
    public static void showAboutWindow() {
        new AboutWindow().showDialog();
    }

    public static void showSendFeedbackDialog() {
        new SendFeedbackDialog().showDialog();
    }

    /**
     * Shows the user notification area. The user notification icon and tooltip
     * created by the NotifyUser object are not modified.
     */
    private static void showTrayIcon() {
        NotifyUserProxy.instance().showTrayIcon();
    }

    /**
     * Hides the user notification area.
     */
    private static void hideTrayIcon() {
        // Do not use hideNotify() here, since that will
        // create multiple tray icons.
        NotifyUserProxy.instance().hideTrayIcon();
    }

    /**
     * Sets the window height, width and location properties to remember the
     * next time the program is started.
     */
    static void applyWindowSettings() {
        ApplicationSettings.RUN_ONCE.setValue(true);
        if (GUIMediator.isAppVisible()) {
            if ((GUIMediator.getAppFrame().getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
                ApplicationSettings.MAXIMIZE_WINDOW.setValue(true);
            } else {
                // set the screen size and location for the
                // next time the application is run.
                Dimension dim = GUIMediator.getAppSize();
                // only save reasonable sizes to get around a bug on
                // OS X that could make the window permanently
                // invisible
                if ((dim.height > 100) && (dim.width > 100)) {
                    Point loc = GUIMediator.getAppLocation();
                    ApplicationSettings.APP_WIDTH.setValue(dim.width);
                    ApplicationSettings.APP_HEIGHT.setValue(dim.height);
                    ApplicationSettings.WINDOW_X.setValue(loc.x);
                    ApplicationSettings.WINDOW_Y.setValue(loc.y);
                }
            }
        }
    }

    /**
     * Serves as a single point of access for any icons used in the program.
     *
     * @param name the name of the icon to return without path information, as in
     *             "plug"
     * @return the <tt>ImageIcon</tt> object specified in the param string
     */
    public static ImageIcon getThemeImage(final String name) {
        return ResourceManager.getThemeImage(name);
    }

    /**
     * Returns an ImageIcon for the specified resource.
     */
    public static ImageIcon getImageFromResourcePath(final String loc) {
        return ResourceManager.getImageFromResourcePath(loc);
    }

    static void resetLocale() {
        ResourceManager.resetLocaleOptions();
        GUIUtils.resetLocale();
    }

    /**
     * Acts as a proxy for the <tt>MessageService</tt> class. Displays a
     * locale-specific message to the user in the form of a yes or no question.
     * <p/>
     * <p/>
     * The <tt>messageKey</tt> parameter must be the key for a locale- specific
     * message <tt>String</tt> and not a hard-coded value.
     *
     * @param message the locale-specific message to display
     * @return an integer indicating a yes or a no response from the user
     */
    public static DialogOption showYesNoMessage(final String message, final DialogOption defaultOption) {
        return MessageService.instance().showYesNoMessage(message, defaultOption);
    }

    /**
     * Acts as a proxy for the <tt>MessageService</tt> class. Displays a
     * locale-specific message to the user in the form of a yes or no question.
     *
     * @param message the locale-specific message to display
     * @param title   the locale-specific title dialog to display
     * @param msgType type messages example JOptionPane.QUESTION_MESSAGE
     * @return an integer indicating a yes or a no response from the user
     */
    public static DialogOption showYesNoMessage(final String message, final String title, final int msgType) {
        return MessageService.instance().showYesNoMessage(message, title, msgType);
    }

    /**
     * Acts as a proxy for the <tt>MessageService</tt> class. Displays a
     * locale-specific message to the user in the form of a yes or no question.
     * <p>
     * The <tt>messageKey</tt> parameter must be the key for a locale- specific
     * message <tt>String</tt> and not a hard-coded value.
     *
     * @param message      the locale-specific message to display
     * @param defaultValue the IntSetting to store/retrieve the default value
     * @return an integer indicating a yes or a no response from the user
     */
    public static DialogOption showYesNoMessage(final String message, final IntSetting defaultValue, final DialogOption defaultOption) {
        return MessageService.instance().showYesNoMessage(message, defaultValue, defaultOption);
    }

    @SuppressWarnings("unused")
    public static DialogOption showYesNoTitledMessage(final String message, final String title, final DialogOption defaultOption) {
        return MessageService.instance().showYesNoMessage(message, title, defaultOption);
    }

    /**
     * Displays a locale-specific message to the user in the form of a
     * yes/no/{other} question.
     * <p/>
     * <p/>
     * The <tt>messageKey</tt> parameter must be the key for a locale- specific
     * message <tt>String</tt> and not a hard-coded value.
     *
     * @param message      the locale-specific message to display
     * @param defaultValue the IntSetting to store/retrieve the default value
     * @param otherOptions the name of the other option
     * @return an integer indicating a yes or a no response from the user
     */
    public static DialogOption showYesNoOtherMessage(final String message, final IntSetting defaultValue, String otherOptions) {
        return MessageService.instance().showYesNoOtherMessage(message, defaultValue, otherOptions);
    }

    /**
     * Acts as a proxy for the <tt>MessageService</tt> class. Displays a
     * locale-specific message to the user in the form of a yes or no or cancel
     * question.
     * <p/>
     * <p/>
     * The <tt>messageKey</tt> parameter must be the key for a locale- specific
     * message <tt>String</tt> and not a hard-coded value.
     *
     * @param message the locale-specific message to display
     * @return an integer indicating a yes or a no response from the user
     */
    public static DialogOption showYesNoCancelMessage(final String message) {
        return MessageService.instance().showYesNoCancelMessage(message);
    }

    /**
     * Acts as a proxy for the <tt>MessageService</tt> class. Displays a
     * locale-specific message to the user in the form of a yes or no or cancel
     * question.
     * <p/>
     * <p/>
     * The <tt>messageKey</tt> parameter must be the key for a locale- specific
     * message <tt>String</tt> and not a hard-coded value.
     *
     * @param message      for the locale-specific message to display
     * @param defaultValue the IntSetting to store/retrieve the default value
     * @return an integer indicating a yes or a no response from the user
     */
    @SuppressWarnings("unused")
    public static DialogOption showYesNoCancelMessage(final String message, final IntSetting defaultValue) {
        return MessageService.instance().showYesNoCancelMessage(message, defaultValue);
    }

    /**
     * Acts as a proxy for the <tt>MessageService</tt> class. Displays a
     * locale-specific message to the user.
     * <p/>
     * <p/>
     * The <tt>messageKey</tt> parameter must be the key for a locale- specific
     * message <tt>String</tt> and not a hard-coded value.
     *
     * @param messageKey the key for the locale-specific message to display
     */
    public static void showMessage(final String messageKey) {
        MessageService.instance().showMessage(messageKey);
    }

    /**
     * Acts as a proxy for the <tt>MessageService</tt> class. Displays a
     * locale-specific message to the user.
     * <p/>
     * <p/>
     * The <tt>messageKey</tt> parameter must be the key for a locale- specific
     * message <tt>String</tt> and not a hard-coded value.
     *
     * @param message the locale-specific message to display
     * @param ignore  the BooleanSetting that stores/retrieves whether or not to
     *                display this message.
     */
    public static void showMessage(final String message, final Switch ignore) {
        MessageService.instance().showMessage(message, ignore);
    }

    /**
     * Acts as a proxy for the <tt>MessageService</tt> class. Displays a
     * locale-specific disposable message to the user.
     * <p/>
     * <p/>
     * The <tt>messageKey</tt> parameter must be the key for a locale- specific
     * message <tt>String</tt> and not a hard-coded value.
     *
     * @param messageKey the key for the locale-specific message to display
     * @param ignore     the BooleanSetting that stores/retrieves whether or not to
     *                   display this message.
     * @param msgType    The <tt>JOptionPane</tt> message type. @see
     *                   javax.swing.JOptionPane.
     */
    private static void showDisposableMessage(@SuppressWarnings("SameParameterValue") final String messageKey, final String message, @SuppressWarnings("SameParameterValue") final Switch ignore, @SuppressWarnings("SameParameterValue") int msgType) {
        MessageService.instance().showDisposableMessage(messageKey, message, ignore, msgType);
    }

    /**
     * Acts as a proxy for the <tt>MessageService</tt> class. Hides a
     * locale-specific disposable message.
     * <p/>
     * <p/>
     * The <tt>messageKey</tt> parameter must be the key for a locale- specific
     * message <tt>String</tt> and not a hard-coded value.
     *
     * @param messageKey the key for the locale-specific message to display
     */
    private static void hideDisposableMessage(@SuppressWarnings("SameParameterValue") final String messageKey) {
        MessageService.instance().hideDisposableMessage(messageKey);
    }

    /**
     * Acts as a proxy for the <tt>MessageService</tt> class. Displays a
     * locale-specific message to the user.
     * <p/>
     * <p/>
     * The <tt>messageKey</tt> parameter must be the key for a locale- specific
     * message <tt>String</tt> and not a hard-coded value.
     *
     * @param message the locale-specific message to display.
     */
    public static void showError(final String message) {
        closeStartupDialogs();
        MessageService.instance().showError(message);
    }

    /**
     * Acts as a proxy for the <tt>MessageService</tt> class. Displays a
     * locale-specific message to the user.
     * <p/>
     * <p/>
     * The <tt>messageKey</tt> parameter must be the key for a locale- specific
     * message <tt>String</tt> and not a hard-coded value.
     *
     * @param message the key for the locale-specific message to display.
     * @param ignore  the BooleanSetting for that stores/retrieves whether or not to
     *                display this message.
     */
    public static void showError(final String message, final Switch ignore) {
        closeStartupDialogs();
        MessageService.instance().showError(message, ignore);
    }

    /**
     * Acts as a proxy for the <tt>MessageService</tt> class. Displays a
     * locale-specific warning message to the user.
     * <p/>
     * <p/>
     * The <tt>messageKey</tt> parameter must be the key for a locale- specific
     * message <tt>String</tt> and not a hard-coded value.
     *
     * @param message the locale-specific message to display.
     */
    public static void showWarning(final String message) {
        closeStartupDialogs();
        MessageService.instance().showWarning(message);
    }

    /**
     * Acts as a proxy for the Launcher class so that other classes only need to
     * know about this mediator class.
     * <p/>
     * <p/>
     * Opens the specified url in a browser.
     *
     * @param url the url to open
     * @return an int indicating the success of the browser launch
     */
    public static int openURL(String url) {
        try {
            return Launcher.openURL(url);
        } catch (Throwable e) {
            GUIMediator.showError(I18n.tr("FrostWire could not locate your web browser to display the following web page: {0}.", url));
            return -1;
        }
    }

    /**
     * Acts as a proxy for the Launcher class so that other classes only need to
     * know about this mediator class.
     * <p/>
     * <p/>
     * Launches the file specified in its associated application.
     *
     * @param file a <tt>File</tt> instance denoting the abstract pathname of the
     *             file to launch
     */
    public static void launchFile(File file) {
        try {
            Launcher.launchFile(file);
        } catch (SecurityException se) {
            showError(I18n.tr("FrostWire will not launch the specified file for security reasons."));
        } catch (LaunchException e) {
            GUIMediator.showError(I18n.tr("FrostWire could not launch the specified file.\n\nExecuted command: {0}.", StringUtils.explode(e.getCommand(), " ")));
        } catch (IOException e) {
            showError(I18n.tr("FrostWire could not launch the specified file."));
        }
    }

    /**
     * Acts as a proxy for the Launcher class so that other classes only need to
     * know about this mediator class.
     * <p/>
     * <p/>
     * Opens <tt>file</tt> in a platform specific file manager.
     *
     * @param file a <tt>File</tt> instance denoting the abstract pathname of the
     *             file to launch
     */
    public static void launchExplorer(File file) {
        try {
            Launcher.launchExplorer(file);
        } catch (SecurityException e) {
            showError(I18n.tr("FrostWire will not launch the specified file for security reasons."));
        } catch (LaunchException e) {
            GUIMediator.showError(I18n.tr("FrostWire could not launch the specified file.\n\nExecuted command: {0}.", StringUtils.explode(e.getCommand(), " ")));
        } catch (IOException e) {
            showError(I18n.tr("FrostWire could not launch the specified file."));
        }
    }

    /**
     * Notifies the user that LimeWire is disconnected
     */
    @SuppressWarnings("unused")
    public static void disconnected() {
        showDisposableMessage(DISCONNECTED_MESSAGE,
                I18n.tr("Your machine does not appear to have an active Internet connection or a firewall is blocking FrostWire from accessing the internet. FrostWire will automatically keep trying to connect you to the network unless you select \"Disconnect\" from the File menu."),
                QuestionsHandler.NO_INTERNET_RETRYING, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Modifies the text displayed to the user in the splash screen to provide
     * application loading information.
     *
     * @param text the text to display
     */
    public static void setSplashScreenString(String text) {
        if (!_allowVisible)
            SplashWindow.instance().setStatusText(text);
        else if (isConstructed())
            instance().getStatusLine().setStatusText(text);
    }

    /**
     * Returns the point for the placing the specified component on the center
     * of the screen.
     *
     * @param comp the <tt>Component</tt> to use for getting the relative center
     *             point
     * @return the <tt>Point</tt> for centering the specified <tt>Component</tt>
     * on the screen
     */
    static Point getScreenCenterPoint(Component comp) {
        final Dimension COMPONENT_DIMENSION = comp.getSize();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int appWidth = Math.min(screenSize.width, COMPONENT_DIMENSION.width);
        // compare against a little bit less than the screen size,
        // as the screen size includes the task bar
        int appHeight = Math.min(screenSize.height - 40, COMPONENT_DIMENSION.height);
        return new Point((screenSize.width - appWidth) / 2, (screenSize.height - appHeight) / 2);
    }

    /**
     * Adds the specified <tt>RefreshListener</tt> instance to the list of
     * listeners to be notified when a UI refresh event occurs.
     *
     * @param listener new <tt>RefreshListener</tt> to add
     */
    public static void addRefreshListener(RefreshListener listener) {
        if (!REFRESH_LIST.contains(listener))
            REFRESH_LIST.add(listener);
    }

    /**
     * Returns the <tt>Locale</tt> instance currently in use.
     *
     * @return the <tt>Locale</tt> instance currently in use
     */
    public static Locale getLocale() {
        return ResourceManager.getLocale();
    }

    /**
     * Returns true if the current locale is English.
     */
    static boolean isEnglishLocale() {
        return LanguageUtils.isEnglishLocale(getLocale());
    }

    /**
     * safely run code synchronously in the event dispatching thread.
     */
    public static void safeInvokeAndWait(Runnable runnable) {
        if (EventQueue.isDispatchThread())
            runnable.run();
        else {
            try {
                SwingUtilities.invokeAndWait(runnable);
            } catch (InvocationTargetException ite) {
                Throwable t = ite.getTargetException();
                if (t instanceof Error)
                    throw (Error) t;
                else if (t instanceof RuntimeException)
                    throw (RuntimeException) t;
                else
                    ErrorService.error(t);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * InvokesLater if not already in the dispatch thread.
     */
    public static void safeInvokeLater(Runnable runnable) {
        if (EventQueue.isDispatchThread())
            runnable.run();
        else
            SwingUtilities.invokeLater(runnable);
    }

    public static void openURL(final String link, final long delay) {
        if (delay > 0) {
            new Thread(() -> {
                try {
                    Thread.sleep(delay);
                } catch (Throwable e) {
                    // ignore
                }
                openURL(link);
            }).start();
        } else {
            openURL(link);
        }
    }

    public static void setClipboardContent(String str) {
        try {
            StringSelection data = new StringSelection(str);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(data, data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Notification that the core has been initialized.
     */
    void coreInitialized() {
        timer.startTimer();
    }

    /**
     * Returns the <tt>MainFrame</tt> instance. <tt>MainFrame</tt> maintains
     * handles to all of the major gui classes.
     *
     * @return the <tt>MainFrame</tt> instance
     */
    public final MainFrame getMainFrame() {
        return MAIN_FRAME;
    }

    /**
     * Returns the status line instance for other classes to access
     */
    public StatusLine getStatusLine() {
        if (STATUS_LINE == null) {
            STATUS_LINE = getMainFrame().getStatusLine();
        }
        return STATUS_LINE;
    }

    /**
     * Refreshes the various gui components that require refreshing.
     */
    final void refreshGUI() {
        for (RefreshListener listener : REFRESH_LIST) {
            try {
                listener.refresh();
            } catch (Throwable t) {
                // Show the error for each RefreshListener individually
                // so that we continue refreshing the other items.
                ErrorService.error(t);
            }
        }
        updateConnectionQualityAsync();
    }

    private void updateConnectionQualityAsync() {
        pool.execute(() -> {
            final int quality = getConnectionQuality();
            safeInvokeLater(() -> {
                if (quality != StatusLine.STATUS_DISCONNECTED) {
                    hideDisposableMessage(DISCONNECTED_MESSAGE);
                }
                updateConnectionUI(quality);
            });
        });
    }

    /**
     * Returns the connection quality.
     */
    private int getConnectionQuality() {
        if (isInternetReachable()) {
            return StatusLine.STATUS_TURBOCHARGED;
        } else {
            return StatusLine.STATUS_DISCONNECTED;
        }
    }

    /**
     * Sets the visibility state of the options window, and sets the selection
     * to a option pane associated with a given key.
     *
     * @param visible the visibility state to set the window to
     * @param key     the unique identifying key of the panel to show
     */
    public void setOptionsVisible(boolean visible, final String key) {
        if (OPTIONS_MEDIATOR == null)
            return;
        OPTIONS_MEDIATOR.setOptionsVisible(visible, key);
    }

    /**
     * Sets the tab pane to display the given tab.
     * Tip: Try to leave this method call last on your actions.
     *
     * @param tabEnum index of the tab to display
     */
    public void setWindow(GUIMediator.Tabs tabEnum) {
        if (tabEnum == Tabs.TRANSFERS || tabEnum == Tabs.SEARCH_TRANSFERS) {
            if (Tabs.TRANSFERS.isEnabled()) {
                tabEnum = Tabs.TRANSFERS;
            } else if (Tabs.SEARCH_TRANSFERS.isEnabled()) {
                tabEnum = Tabs.SEARCH_TRANSFERS;
            }
        }
        getMainFrame().getApplicationHeader().showSearchField(getMainFrame().getTab(tabEnum));
        getMainFrame().setSelectedTab(tabEnum);
        // If we've never selected a directory holder in the library, then we have it select "Default Save Folder"
        if (LibrarySettings.LAST_SELECTED_LIBRARY_DIRECTORY_HOLDER_OFFSET.getValue() == -1) {
            selectDefaultSaveFolderOnLibraryFirstTime(tabEnum);
        } else {
            LibraryMediator.instance().getLibraryExplorer().selectDirectoryHolderAt(LibrarySettings.LAST_SELECTED_LIBRARY_DIRECTORY_HOLDER_OFFSET.getValue());
        }
    }

    /**
     * If the window to be shown is the Library tab, we automatically select "Finished Downloads"
     * so the users have a clue of what they can do with the Library, and so that they see their
     * finished downloads in case they came here the first time to see what they downloaded.
     */
    private void selectDefaultSaveFolderOnLibraryFirstTime(GUIMediator.Tabs tab) {
        if (!tab.navigatedTo && tab.equals(GUIMediator.Tabs.LIBRARY)) {
            LibraryMediator.instance().getLibraryExplorer().selectFinishedDownloads();
            tab.navigatedTo = true;
        }
    }

    public GUIMediator.Tabs getSelectedTab() {
        return getMainFrame().getSelectedTab();
    }

    /**
     * Sets the connected/disconnected visual status of the client.
     *
     * @param quality the connected/disconnected status of the client
     */
    private void updateConnectionUI(int quality) {
        getStatusLine().setConnectionQuality(quality);
    }

    /**
     * Returns the total number of currently active uploads.
     *
     * @return the total number of currently active uploads
     */
    int getCurrentUploads() {
        return getBTDownloadMediator().getActiveUploads();
    }

    /**
     * Returns the total number of downloads for this session.
     *
     * @return the total number of downloads for this session
     */
    @SuppressWarnings("unused")
    public final int getTotalDownloads() {
        return getBTDownloadMediator().getTotalDownloads();
    }

    final int getCurrentDownloads() {
        return getBTDownloadMediator().getActiveDownloads();
    }

    public final void openTorrentFile(File torrentFile, boolean partialSelection) {
        BTDownloadMediator btDownloadMediator = getBTDownloadMediator();
        List<BTDownload> downloads = getBTDownloadMediator().getDownloads();
        Runnable onOpenRunnable = () -> {
            showTransfers(TransfersTab.FilterMode.ALL);
            TorrentInfo ti = new TorrentInfo(torrentFile);
            for (BTDownload btDownload : downloads) {
                if (btDownload.getHash().equals(ti.infoHash().toHex())) {
                    btDownloadMediator.selectBTDownload(btDownload);
                }
            }
        };
        btDownloadMediator.openTorrentFile(torrentFile, partialSelection, onOpenRunnable);
    }

    public void openTorrentForSeed(File torrentFile, File saveDir) {
        getBTDownloadMediator().openTorrentFileForSeed(torrentFile, saveDir);
        showTransfers(TransfersTab.FilterMode.ALL);
    }

    public final void openTorrentURI(String uri, boolean partialDownload) {
        showTransfers(TransfersTab.FilterMode.ALL);
        getBTDownloadMediator().openTorrentURI(uri, partialDownload);
    }

    /**
     * Notification that loading is finished. Updates the status line and bumps
     * the AWT thread priority.
     */
    void loadFinished() {
        SwingUtilities.invokeLater(() -> {
            Thread awt = Thread.currentThread();
            awt.setPriority(awt.getPriority() + 1);
            getStatusLine().loadFinished();
        });
    }

    public void showTransfers(TransfersTab.FilterMode mode) {
        Tabs tabEnum = Tabs.TRANSFERS;
        if (Tabs.TRANSFERS.isEnabled()) {
            tabEnum = Tabs.TRANSFERS;
        } else if (Tabs.SEARCH_TRANSFERS.isEnabled()) {
            tabEnum = Tabs.SEARCH_TRANSFERS;
        }
        setWindow(tabEnum);
        ((TransfersTab) getMainFrame().getTab(Tabs.TRANSFERS)).showTransfers(mode);
    }

    public Tab getTab(Tabs tabs) {
        return MAIN_FRAME.getTab(tabs);
    }

    /**
     * Launches the specified audio/video in the player.
     *
     * @param song - song to play now
     */
    public void launchMedia(MediaSource song, boolean isPreview) {
        if (MediaPlayer.instance().getCurrentMedia() != null)
            try {
                MediaPlayer.instance().stop();
                // it needs to pause for a bit, otherwise it'll play the same song.
                // must be a sync bug somewhere, but this fixes it
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        //MediaPlayer.instance().loadSong(song);
        boolean playNextSong = true;
        if (song.getFile() != null && MediaType.getVideoMediaType().matches(song.getFile().getAbsolutePath())) {
            playNextSong = false;
        }
        MediaPlayer.instance().asyncLoadMedia(song, isPreview, playNextSong);
    }

    /**
     * Notification that the button state has changed.
     */
    public void buttonViewChanged() {
        IconManager.instance().wipeButtonIconCache();
        updateButtonView(getAppFrame());
    }

    private void updateButtonView(Component c) {
        if (c instanceof IconButton) {
            ((IconButton) c).updateUI();
        }
        Component[] children = null;
        if (c instanceof Container) {
            children = ((Container) c).getComponents();
        }
        if (children != null) {
            for (Component aChildren : children) {
                updateButtonView(aChildren);
            }
        }
    }

    /**
     * Sets the cursor on FrostWire's frame.
     *
     * @param cursor the cursor that should be shown on the frame and all its child
     *               components that don't have their own cursor set
     */
    public void setFrameCursor(Cursor cursor) {
        getAppFrame().setCursor(cursor);
    }

    public BTDownloadMediator getBTDownloadMediator() {
        if (BT_DOWNLOAD_MEDIATOR == null) {
            BT_DOWNLOAD_MEDIATOR = getMainFrame().getBTDownloadMediator();
        }
        return BT_DOWNLOAD_MEDIATOR;
    }

    private boolean isInternetReachable() {
        long _internetConnectivityInterval = 5000;
        long now = System.currentTimeMillis();
        if (now - _lastConnectivityCheckTimestamp < _internetConnectivityInterval) {
            return _wasInternetReachable;
        }
        _lastConnectivityCheckTimestamp = now;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                _wasInternetReachable = false;
                return false;
            }
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isUp() && !iface.isLoopback()) {
                    _wasInternetReachable = true;
                    return true;
                }
            }
        } catch (Exception e) {
            _wasInternetReachable = false;
            return false;
        }
        _wasInternetReachable = false;
        return false;
    }

    boolean isRemoteDownloadsAllowed() {
        return _remoteDownloadsAllowed;
    }

    public void setRemoteDownloadsAllowed(boolean remoteDownloadsAllowed) {
        _remoteDownloadsAllowed = remoteDownloadsAllowed;
    }

    public void openTorrentSearchResult(TorrentSearchResult sr, boolean partial) {
        getBTDownloadMediator().openTorrentSearchResult(sr, partial);
    }

    public void openSoundcloudTrackUrl(String trackUrl, SoundcloudSearchResult sr, boolean fromPastedUrl) {
        showTransfers(TransfersTab.FilterMode.ALL);
        getBTDownloadMediator().downloadSoundcloudFromTrackUrlOrSearchResult(trackUrl, sr, fromPastedUrl);
    }

    public void openSlide(Slide slide) {
        getBTDownloadMediator().openSlide(slide);
        // we might already have it, let's show all instead of downloading.
        showTransfers(TransfersTab.FilterMode.ALL);
    }

    public void openHttp(final String httpUrl, final String title, final String saveFileAs, final double fileSize) {
        showTransfers(TransfersTab.FilterMode.ALL);
        getBTDownloadMediator().openHttp(httpUrl, title, saveFileAs, fileSize);
    }

    public void startSearch(String query) {
        getMainFrame().getApplicationHeader().startSearch(query);
    }

    public void playInOS(MediaSource source) {
        if (source == null) {
            return;
        }
        if (source.getFile() != null) {
            GUIMediator.launchFile(source.getFile());
        } else if (source.getPlaylistItem() != null) {
            GUIMediator.launchFile(new File(source.getPlaylistItem().getFilePath()));
        } else if (source.getURL() != null) {
            GUIMediator.openURL(source.getURL());
        }
    }

    public enum Tabs {
        SEARCH(I18n.tr("&Search")),
        TRANSFERS(I18n.tr("&Transfers")),
        SEARCH_TRANSFERS(I18n.tr("&Search")),
        LIBRARY(I18n.tr("&Library"));
        boolean navigatedTo;
        private final Action navAction;

        Tabs(String nameWithAmpersand) {
            navAction = new NavigationAction(nameWithAmpersand, I18n.tr("Display the {0} Screen",
                    GUIUtils.stripAmpersand(nameWithAmpersand)));
        }

        public boolean isEnabled() {
            return navAction.isEnabled();
        }

        void setEnabled(boolean enabled) {
            navAction.setEnabled(enabled);
        }

        private class NavigationAction extends AbstractAction {
            NavigationAction(String name, String description) {
                super(name);
                putValue(Action.LONG_DESCRIPTION, description);
            }

            public void actionPerformed(ActionEvent e) {
                instance().setWindow(Tabs.this);
            }
        }
    }
}
