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

import com.frostwire.gui.bittorrent.BTDownloadMediator;
import com.frostwire.gui.library.LibraryMediator;
import com.frostwire.gui.tabs.*;
import com.frostwire.gui.theme.ThemeMediator;
import com.frostwire.gui.updates.UpdateManager;
import com.limegroup.gnutella.gui.GUIMediator.Tabs;
import com.limegroup.gnutella.gui.dnd.DNDUtils;
import com.limegroup.gnutella.gui.dnd.TransferHandlerDropTargetListener;
import com.limegroup.gnutella.gui.menu.MenuMediator;
import com.limegroup.gnutella.gui.options.OptionsMediator;
import com.limegroup.gnutella.gui.search.MagnetClipboardListener;
import com.limegroup.gnutella.settings.ApplicationSettings;
import net.miginfocom.swing.MigLayout;
import org.limewire.setting.SettingsGroupManager;
import com.frostwire.util.OSUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.DropTarget;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Map;

import static com.limegroup.gnutella.settings.UISettings.UI_SEARCH_TRANSFERS_SPLIT_VIEW;

/**
 * This class constructs the main `JFrame` for the program as well as
 * all of the other GUI classes.
 */
public final class MainFrame {
    /**
     * The main `JFrame` for the application.
     */
    private final JFrame FRAME;
    /**
     * Handle to the `JTabbedPane` instance.
     */
    private final JPanel TABBED_PANE;
    private BTDownloadMediator BT_DOWNLOAD_MEDIATOR;
    /**
     * Constant handle to the `LibraryView` class that is
     * responsible for displaying files in the user's repository.
     */
    private LibraryMediator LIBRARY_MEDIATOR;
    /**
     * Constant handle to the `OptionsMediator` class that is
     * responsible for displaying customizable options to the user.
     */
    private OptionsMediator OPTIONS_MEDIATOR;
    /**
     * Constant handle to the `StatusLine` class that is
     * responsible for displaying the status of the network and
     * connectivity to the user.
     */
    private StatusLine STATUS_LINE;
    /**
     * The array of tabs in the main application window.
     */
    private final Map<GUIMediator.Tabs, Tab> TABS = new HashMap<>(3);
    /**
     * The last state of the X/Y location and the time it was set.
     * This is necessary to preserve the maximize size & prior size,
     * as on Windows a move event is occasionally triggered when
     * maximizing, prior to the state actually becoming maximized.
     */
    private WindowState lastState = null;
    private final ApplicationHeader APPLICATION_HEADER;

    /**
     * Initializes the primary components of the main application window,
     * including the `JFrame` and the `JTabbedPane`
     * contained in that window.
     */
    MainFrame(JFrame frame) {
        //starts the Frostwire update manager, and will trigger a task in 5 seconds.
        // RELEASE
        UpdateManager.scheduleUpdateCheckTask(0);
        // DEBUG
        //UpdateManager.scheduleUpdateCheckTask(0,"http://update1.frostwire.com/example.php");
        FRAME = frame;
        new DropTarget(FRAME, new TransferHandlerDropTargetListener(DNDUtils.DEFAULT_TRANSFER_HANDLER));
        TABBED_PANE = new JPanel(new CardLayout());
        TABBED_PANE.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeMediator.LIGHT_BORDER_COLOR));
        // Add a listener for saving the dimensions of the window &
        // position the search icon overlay correctly.
        FRAME.addComponentListener(new ComponentListener() {
            public void componentHidden(ComponentEvent e) {
            }

            public void componentShown(ComponentEvent e) {
            }

            public void componentMoved(ComponentEvent e) {
                lastState = new WindowState();
                saveWindowState();
            }

            public void componentResized(ComponentEvent e) {
                saveWindowState();
            }
        });
        // Listen for the size/state changing.
        FRAME.addWindowStateListener(e -> saveWindowState());
        // Listen for the window closing, to save settings.
        FRAME.addWindowListener(new WindowAdapter() {
            public void windowDeiconified(WindowEvent e) {
                // Handle reactivation on systems which do not support
                // the system tray.  Windows systems call the
                // WindowsNotifyUser.restoreApplication()
                // method to restore applications from minimize and
                // auto-shutdown modes.  Non-windows systems restore
                // the application using the following code.
                if (!OSUtils.supportsTray() || !ResourceManager.instance().isTrayIconAvailable())
                    GUIMediator.restoreView();
            }

            public void windowClosing(WindowEvent e) {
                saveWindowState();
                SettingsGroupManager.instance().save();
                GUIMediator.close(true);
            }
        });
        FRAME.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        this.setFrameDimensions();
        FRAME.setJMenuBar(MenuMediator.instance().getMenuBar());
        JPanel contentPane = new JPanel();
        FRAME.setContentPane(contentPane);
        contentPane.setLayout(new MigLayout("insets 0, gap 0"));
        // Build tabs first to populate TABS map (but defer addTabs to avoid EDT violations)
        buildTabs();
        APPLICATION_HEADER = new ApplicationHeader(TABS);
        contentPane.add(APPLICATION_HEADER, "growx, dock north");
        contentPane.add(TABBED_PANE, "wrap");
        contentPane.add(getStatusLine().getComponent(), "dock south, shrink 0");

        // Defer expensive layout calculations to avoid EDT blocking
        // Set a default minimum size first to avoid issues
        FRAME.setMinimumSize(new Dimension(800, 600));

        // Calculate the real minimum size in a background thread
        new Thread(() -> {
            try {
                Thread.sleep(100); // Give UI time to settle
                SwingUtilities.invokeLater(() -> {
                    try {
                        setMinimalSize(FRAME, APPLICATION_HEADER, APPLICATION_HEADER, TABBED_PANE, getStatusLine().getComponent());
                    } catch (Exception e) {
                        // Ignore errors during deferred layout calculation
                    }
                });
            } catch (InterruptedException ignored) {
            }
        }, "MainFrame-MinimalSizeCalculator").start();

        if (ApplicationSettings.MAGNET_CLIPBOARD_LISTENER.getValue()) {
            FRAME.addWindowListener(MagnetClipboardListener.getInstance());
        }
        if (OSUtils.isMacOSX()) {
            FRAME.getRootPane().putClientProperty("apple.awt.fullscreenable", true);
        }
    }

    private void setMinimalSize(JFrame frame, JComponent horizontal, JComponent... verticals) {
        int width = horizontal.getMinimumSize().width;
        int height = 0;
        for (JComponent c : verticals) {
            height += c.getMinimumSize().height;
        }
        // for some reason I can pack the frame
        // this disallow me of getting the right size of the title bar
        // and in general the insets's frame
        // lets add some fixed value for now
        height += 50;
        frame.setMinimumSize(new Dimension(width, height));
    }

    public ApplicationHeader getApplicationHeader() {
        return APPLICATION_HEADER;
    }

    /**
     * Saves the state of the Window to settings.
     */
    private void saveWindowState() {
        int state = FRAME.getExtendedState();
        if (state == Frame.NORMAL) {
            // save the screen size and location
            Dimension dim = GUIMediator.getAppSize();

            int minAppWidth = ApplicationSettings.APP_WIDTH.getDefaultValue();
            int minAppHeight = ApplicationSettings.APP_HEIGHT.getDefaultValue();
            int appWidth = dim.width;
            int appHeight = dim.height;

            boolean forceMinimumDimensions = false;

            if (appWidth < minAppWidth) {
                appWidth = minAppWidth;
                forceMinimumDimensions = true;
            }

            if (appHeight < minAppHeight) {
                appHeight = minAppHeight;
                forceMinimumDimensions = true;
            }

            if (forceMinimumDimensions) {
                FRAME.setSize(new Dimension(appWidth, appHeight));
                FRAME.getContentPane().setSize(new Dimension(appWidth, appHeight));
                FRAME.getContentPane().setPreferredSize(new Dimension(appWidth, appHeight));
            }

            Point loc = GUIMediator.getAppLocation();
            ApplicationSettings.APP_WIDTH.setValue(appWidth);
            ApplicationSettings.APP_HEIGHT.setValue(appHeight);
            ApplicationSettings.WINDOW_X.setValue(loc.x);
            ApplicationSettings.WINDOW_Y.setValue(loc.y);
            ApplicationSettings.MAXIMIZE_WINDOW.setValue(false);

        } else if ((state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
            ApplicationSettings.MAXIMIZE_WINDOW.setValue(true);
            if (lastState != null && lastState.time == System.currentTimeMillis()) {
                ApplicationSettings.WINDOW_X.setValue(lastState.x);
                ApplicationSettings.WINDOW_Y.setValue(lastState.y);
                lastState = null;
            }
        }
    }

    /**
     * Build the Tab Structure based on advertising mode and Windows
     */
    private void buildTabs() {
        SearchTab searchTab = new SearchTab();
        LibraryTab libraryTab = new LibraryTab(getLibraryMediator());
        // keep references to the tab objects.
        TABS.put(Tabs.SEARCH, searchTab);
        TABS.put(Tabs.LIBRARY, libraryTab);

        TABBED_PANE.setPreferredSize(new Dimension(10000, 10000));
        // Add Search and Library tabs immediately so they're visible
        addTabsPartial(UI_SEARCH_TRANSFERS_SPLIT_VIEW.getValue());

        // Defer TransfersTab creation to avoid EDT violations from expensive component initialization
        SwingUtilities.invokeLater(() -> {
            TransfersTab transfersTab = new TransfersTab(getBTDownloadMediator());
            TABS.put(Tabs.TRANSFERS, transfersTab);
            SearchTransfersTab searchTransfers = new SearchTransfersTab(searchTab, transfersTab);
            TABS.put(Tabs.SEARCH_TRANSFERS, searchTransfers);
            // Add all tabs now that TRANSFERS and SEARCH_TRANSFERS are populated
            addTabs(UI_SEARCH_TRANSFERS_SPLIT_VIEW.getValue());
            TABBED_PANE.setRequestFocusEnabled(false);
        });
    }

    /**
     * Add only Search and Library tabs initially (lightweight tabs that don't block EDT)
     */
    private void addTabsPartial(boolean useSearchTransfersSplitView) {
        updateEnabledTabs(useSearchTransfersSplitView);
        // Only add Search and Library tabs initially
        Tab searchTab = TABS.get(Tabs.SEARCH);
        if (searchTab != null && Tabs.SEARCH.isEnabled()) {
            addTab(searchTab);
        }
        Tab libraryTab = TABS.get(Tabs.LIBRARY);
        if (libraryTab != null && Tabs.LIBRARY.isEnabled()) {
            addTab(libraryTab);
        }
    }

    private void addTabs(boolean useSearchTransfersSplitView) {
        updateEnabledTabs(useSearchTransfersSplitView);
        // Add all enabled tabs - CardLayout will handle duplicates by updating the existing component
        // This preserves Search and Library tabs added by addTabsPartial() while adding Transfers
        for (Tabs tabEnum : Tabs.values()) {
            final Tab tab = TABS.get(tabEnum);
            if (tabEnum.isEnabled() && tab != null) {
                TABBED_PANE.add(tab.getComponent(), tab.getTitle());
            }
        }
    }

    /**
     * Adds a tab to the `JTabbedPane` based on the data supplied
     * in the `Tab` instance.
     *
     * @param tab the `Tab` instance containing data for the tab to
     *            add
     */
    private void addTab(Tab tab) {
        TABBED_PANE.add(tab.getComponent(), tab.getTitle());
    }

    /**
     * @param useSearchTransfersSplitView true if you want the old split style.
     */
    private void updateEnabledTabs(boolean useSearchTransfersSplitView) {
        Tabs.SEARCH.setEnabled(!useSearchTransfersSplitView);
        Tabs.TRANSFERS.setEnabled(!useSearchTransfersSplitView);
        Tabs.SEARCH_TRANSFERS.setEnabled(useSearchTransfersSplitView);
        Tabs.LIBRARY.setEnabled(true);
    }

    /**
     * Sets the x,y location as well as the height and width of the main
     * application `Frame`.
     */
    private void setFrameDimensions() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        GraphicsConfiguration gc = gd.getDefaultConfiguration();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int locX;
        int locY;
        int appWidth = Math.min(screenSize.width - insets.left - insets.right, ApplicationSettings.APP_WIDTH.getValue());
        int appHeight = Math.min(screenSize.height - insets.top - insets.bottom, ApplicationSettings.APP_HEIGHT.getValue());
        // Set the location of our window based on whether or not
        // the user has run the program before, and therefore may have
        // modified the location of the main window.
        if (ApplicationSettings.RUN_ONCE.getValue()) {
            locX = Math.max(insets.left, ApplicationSettings.WINDOW_X.getValue());
            locY = Math.max(insets.top, ApplicationSettings.WINDOW_Y.getValue());
        } else {
            locX = (screenSize.width - appWidth) / 2;
            locY = (screenSize.height - appHeight) / 2;
        }
        // Make sure the Window is visible and not for example
        // somewhere in the very bottom right corner.
        if (locX + appWidth > screenSize.width) {
            locX = Math.max(insets.left, screenSize.width - insets.left - insets.right - appWidth);
        }
        if (locY + appHeight > screenSize.height) {
            locY = Math.max(insets.top, screenSize.height - insets.top - insets.bottom - appHeight);
        }
        FRAME.setLocation(locX, locY);
        FRAME.setSize(new Dimension(appWidth, appHeight));
        FRAME.getContentPane().setSize(new Dimension(appWidth, appHeight));
        FRAME.getContentPane().setPreferredSize(new Dimension(appWidth, appHeight));
        //re-maximize if we shutdown while maximized.
        if (ApplicationSettings.MAXIMIZE_WINDOW.getValue() && Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH)) {
            FRAME.setExtendedState(Frame.MAXIMIZED_BOTH);
        }
    }

    final BTDownloadMediator getBTDownloadMediator() {
        if (BT_DOWNLOAD_MEDIATOR == null) {
            BT_DOWNLOAD_MEDIATOR = BTDownloadMediator.instance();
        }
        return BT_DOWNLOAD_MEDIATOR;
    }

    /**
     * Returns a reference to the `LibraryMediator` instance.
     *
     * @return a reference to the `LibraryMediator` instance
     */
    private LibraryMediator getLibraryMediator() {
        if (LIBRARY_MEDIATOR == null) {
            LIBRARY_MEDIATOR = LibraryMediator.instance();
        }
        return LIBRARY_MEDIATOR;
    }

    /**
     * Returns a reference to the `StatusLine` instance.
     *
     * @return a reference to the `StatusLine` instance
     */
    final StatusLine getStatusLine() {
        if (STATUS_LINE == null) {
            STATUS_LINE = new StatusLine();
        }
        return STATUS_LINE;
    }

    /**
     * Returns a reference to the `OptionsMediator` instance.
     *
     * @return a reference to the `OptionsMediator` instance
     */
    final OptionsMediator getOptionsMediator() {
        if (OPTIONS_MEDIATOR == null) {
            OPTIONS_MEDIATOR = OptionsMediator.instance();
        }
        return OPTIONS_MEDIATOR;
    }

    final Tabs getSelectedTab() {
        Component comp = getCurrentTabComponent();
        if (comp != null) {
            for (Tabs t : TABS.keySet()) {
                JComponent tabComponent = TABS.get(t).getComponent();
                if (comp.equals(tabComponent)) {
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * Sets the selected index in the wrapped `JTabbedPane`.
     *
     * @param tab index to select
     */
    void setSelectedTab(GUIMediator.Tabs tab) {
        CardLayout cl = (CardLayout) (TABBED_PANE.getLayout());
        Tab t = TABS.get(tab);
        cl.show(TABBED_PANE, t.getTitle());
        APPLICATION_HEADER.selectTab(t);
    }

    private Component getCurrentTabComponent() {
        Component currentPanel = null;
        for (Component component : TABBED_PANE.getComponents()) {
            if (component.isVisible()) {
                if (component instanceof JPanel)
                    currentPanel = component;
                else if (component instanceof JScrollPane)
                    currentPanel = ((JScrollPane) component).getViewport().getComponent(0);
            }
        }
        return currentPanel;
    }

    final Tab getTab(Tabs tabs) {
        return TABS.get(tabs);
    }

    public void resizeSearchTransferDivider(int newLocation) {
        SearchTransfersTab searchTab = (SearchTransfersTab) TABS.get(Tabs.SEARCH_TRANSFERS);
        searchTab.setDividerLocation(newLocation);
    }

    /**
     * simple state.
     */
    private static class WindowState {
        private final int x;
        private final int y;
        private final long time;

        WindowState() {
            x = ApplicationSettings.WINDOW_X.getValue();
            y = ApplicationSettings.WINDOW_Y.getValue();
            time = System.currentTimeMillis();
        }
    }
}
