/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.options;

import com.frostwire.gui.searchfield.SearchField;
import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.gui.options.panes.*;
import com.limegroup.gnutella.gui.shell.FrostAssociations;
import com.limegroup.gnutella.settings.ApplicationSettings;
import com.limegroup.gnutella.settings.UISettings;
import org.limewire.setting.IntSetting;
import org.limewire.setting.SettingsGroupManager;
import org.limewire.util.CommonUtils;
import org.limewire.util.OSUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class constructs the options tree on the left side of the options dialog.
 * <p>
 * The panes that show up when a leaf in the tree is selected are created
 * lazily in {@link OptionsPaneFactory}.
 * <p>
 * If you want to add a new {@link OptionsPane},
 * add a call to {@link OptionsConstructor#addOption} in the constructor here
 * and add the construction of the pane to
 * {@link OptionsPaneFactory#createOptionsPane(OptionsTreeNode)}.
 */
public final class OptionsConstructor {
    public static final String SHARED_KEY = "OPTIONS_SHARED_MAIN_TITLE";
    public static final String BITTORRENT_BASIC_KEY = "OPTIONS_BITTORRENT_BASIC_TITLE";
    private static final String BITTORRENT_ADVANCED_KEY = "OPTIONS_BITTORRENT_ADVANCED_TITLE";
    public static final String LIBRARY_KEY = "OPTIONS_LIBRARY_MAIN_TITLE";
    static final String SAVE_BASIC_KEY = "OPTIONS_SAVE_BASIC_MAIN_TITLE";
    static final String SHARED_BASIC_KEY = "OPTIONS_SHARED_BASIC_TITLE";
    private static final String BITTORRENT_KEY = "OPTIONS_BITTORRENT_MAIN_TITLE";
    private static final String SHUTDOWN_KEY = "OPTIONS_SHUTDOWN_MAIN_TITLE";
    private static final String PLAYER_KEY = "OPTIONS_PLAYER_MAIN_TITLE";
    private static final String STATUS_BAR_KEY = "OPTIONS_STATUS_BAR_MAIN_TITLE";
    private static final String ITUNES_KEY = "OPTIONS_ITUNES_MAIN_TITLE";
    private static final String ITUNES_IMPORT_KEY = "OPTIONS_ITUNES_PREFERENCE_MAIN_TITLE";
    private static final String BUGS_KEY = "OPTIONS_BUGS_MAIN_TITLE";
    private static final String APPS_KEY = "OPTIONS_APPS_MAIN_TITLE";
    private static final String SEARCH_KEY = "OPTIONS_SEARCH_MAIN_TITLE";
    private static final String FILTERS_KEY = "OPTIONS_FILTERS_MAIN_TITLE";
    private static final String RESULTS_KEY = "OPTIONS_RESULTS_MAIN_TITLE";
    private static final String ADVANCED_KEY = "OPTIONS_ADVANCED_MAIN_TITLE";
    private static final String PREFERENCING_KEY = "OPTIONS_PREFERENCING_MAIN_TITLE";
    private static final String FIREWALL_KEY = "OPTIONS_FIREWALL_MAIN_TITLE";
    private static final String EXPERIMENTAL_KEY = "OPTIONS_EXPERIMENTAL_MAIN_TITLE";
    private static final String GUI_KEY = "OPTIONS_GUI_MAIN_TITLE";
    private static final String STARTUP_KEY = "OPTIONS_STARTUP_MAIN_TITLE";
    private static final String PROXY_KEY = "OPTIONS_PROXY_MAIN_TITLE";
    private static final String NETWORK_INTERFACE_KEY = "OPTIONS_NETWORK_INTERFACE_MAIN_TITLE";
    private static final String ASSOCIATIONS_KEY = "OPTIONS_ASSOCIATIONS_MAIN_TITLE";
    /**
     * Handle to the top-level <tt>JDialog</tt window that contains all
     * of the other GUI components.
     */
    private final JDialog DIALOG;
    /**
     * Stored for convenience to allow using this in helper methods
     * during construction.
     */
    private final OptionsTreeManager TREE_MANAGER;
    private final SearchField filterTextField;
    private final Map<String, OptionsTreeNode> keysToNodes;

    /**
     * The constructor create all of the options windows and their
     * components.
     *
     * @param treeManager the <tt>OptionsTreeManager</tt> instance to
     *                    use for constructing the main panels and
     *                    adding elements
     * @param paneManager the <tt>OptionsPaneManager</tt> instance to
     *                    use for constructing the main panels and
     *                    adding elements
     */
    public OptionsConstructor(final OptionsTreeManager treeManager, final OptionsPaneManager paneManager) {
        TREE_MANAGER = treeManager;
        /*
          Stored for convenience to allow using this in helper methods
          during construction.
         */
        keysToNodes = new LinkedHashMap<>();
        final String title = I18n.tr("Options");
        final boolean shouldBeModal = !OSUtils.isMacOSX();
        DIALOG = new JDialog(GUIMediator.getAppFrame(), title, shouldBeModal);
        DIALOG.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        GUIUtils.addHideAction((JComponent) DIALOG.getContentPane());
        if (UISettings.UI_OPTIONS_DIALOG_HEIGHT.getValue() < UISettings.UI_OPTIONS_DIALOG_HEIGHT.getDefaultValue()) {
            UISettings.UI_OPTIONS_DIALOG_HEIGHT.revertToDefault();
        }
        if (UISettings.UI_OPTIONS_DIALOG_WIDTH.getValue() < UISettings.UI_OPTIONS_DIALOG_WIDTH.getDefaultValue()) {
            UISettings.UI_OPTIONS_DIALOG_WIDTH.revertToDefault();
        }
        DialogSizeSettingUpdater.install(DIALOG, UISettings.UI_OPTIONS_DIALOG_WIDTH, UISettings.UI_OPTIONS_DIALOG_HEIGHT);
        // most Mac users expect changes to be saved when the window
        // is closed, so save them
        DIALOG.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                try {
                    DialogOption answer = null;
                    if (OptionsMediator.instance().isDirty()) {
                        answer = GUIMediator.showYesNoCancelMessage(I18n.tr("You have made changes to some of FrostWire's settings. Would you like to save these changes?"));
                        if (answer == DialogOption.YES) {
                            OptionsMediator.instance().applyOptions();
                            SettingsGroupManager.instance().save();
                        }
                    }
                    if (answer != DialogOption.CANCEL) {
                        DIALOG.dispose();
                        OptionsMediator.instance().disposeOptions();
                    }
                } catch (IOException ioe) {
                    // nothing we should do here.  a message should
                    // have been displayed to the user with more
                    // information
                }
            }
        });
        PaddedPanel mainPanel = new PaddedPanel();
        Box splitBox = new Box(BoxLayout.X_AXIS);
        BoxPanel treePanel = new BoxPanel(BoxLayout.Y_AXIS);
        BoxPanel filterPanel = new BoxPanel(BoxLayout.X_AXIS);
        treePanel.add(filterPanel);
        filterTextField = new SearchField();
        filterTextField.setPrompt(I18n.tr("Search here"));
        filterTextField.setMinimumSize(new Dimension(100, 27));
        filterTextField.addActionListener(e -> filter());
        filterPanel.add(filterTextField);
        filterPanel.add(Box.createHorizontalStrut(2));
        treePanel.add(Box.createVerticalStrut(3));
        Component treeComponent = TREE_MANAGER.getComponent();
        treePanel.add(treeComponent);
        Component paneComponent = paneManager.getComponent();
        splitBox.add(treePanel);
        splitBox.add(paneComponent);
        mainPanel.add(splitBox);
        mainPanel.add(Box.createVerticalStrut(17));
        mainPanel.add(new OptionsButtonPanel().getComponent());
        DIALOG.getContentPane().add(mainPanel);
        OptionsTreeNode node = initializePanels();
        paneManager.show(node);
    }

    @SuppressWarnings({"unchecked"})
    private OptionsTreeNode initializePanels() {
        //bittorrent
        addGroupTreeNode(OptionsMediator.ROOT_NODE_KEY, BITTORRENT_KEY, I18n.tr("BitTorrent"));
        addOption(BITTORRENT_KEY, BITTORRENT_BASIC_KEY, I18n.tr("Basic"), TorrentSaveFolderPaneItem.class, TorrentSeedingSettingPaneItem.class);
        addOption(BITTORRENT_KEY, BITTORRENT_ADVANCED_KEY, I18n.tr("Advanced"), TorrentGlobalSpeedPaneItem.class, TorrentConnectionPaneItem.class);
        // library
        // REMOVE:WI-FI
        //addOption(OptionsMediator.ROOT_NODE_KEY, LIBRARY_KEY, I18n.tr("Library"), LibraryFoldersPaneItem.class, WiFiSharingPaneItem.class, LibraryInternetRadioPaneItem.class);
        addOption(OptionsMediator.ROOT_NODE_KEY, LIBRARY_KEY, I18n.tr("Library"), LibraryFoldersPaneItem.class);
        // player
        addOption(OptionsMediator.ROOT_NODE_KEY, PLAYER_KEY, I18n.tr("Player"), PlayerPaneItem.class);
        // search options
        addOption(OptionsMediator.ROOT_NODE_KEY, SEARCH_KEY, I18n.tr("Searching"), SearchEnginesPaneItem.class, MaximumSearchesPaneItem.class, SmartSearchDBPaneItem.class, DetailsPaneItem.class);
        //status bar
        addOption(OptionsMediator.ROOT_NODE_KEY, STATUS_BAR_KEY, I18n.tr("Status Bar"), StatusBarConnectionQualityPaneItem.class, StatusBarFirewallPaneItem.class, StatusBarBandwidthPaneItem.class); // Removed Lime Store
        //itunes
        if (isItunesSupported()) {
            addGroupTreeNode(OptionsMediator.ROOT_NODE_KEY, ITUNES_KEY, I18n.tr("iTunes"));
            addOption(ITUNES_KEY, ITUNES_IMPORT_KEY, I18n.tr("Importing"), iTunesPreferencePaneItem.class);
        }
        if (!OSUtils.isWindows() && !OSUtils.isAnyMac()) {
            addOption(OptionsMediator.ROOT_NODE_KEY, APPS_KEY, I18n.tr("Helper Apps"), BrowserPaneItem.class, ImageViewerPaneItem.class, VideoPlayerPaneItem.class, AudioPlayerPaneItem.class);
        }
        //view options
        if (OSUtils.isWindows()) {
            addOption(OptionsMediator.ROOT_NODE_KEY, GUI_KEY, I18n.tr("View"), PopupsPaneItem.class, ShowPromoOverlaysPaneItem.class, ShowFrostWireRecommendationsPaneItem.class, AutoCompletePaneItem.class);
        } else {
            addOption(OptionsMediator.ROOT_NODE_KEY, GUI_KEY, I18n.tr("View"), PopupsPaneItem.class, ShowPromoOverlaysPaneItem.class, AutoCompletePaneItem.class);
        }
        // filter options
        addGroupTreeNode(OptionsMediator.ROOT_NODE_KEY, FILTERS_KEY, I18n.tr("Filters"));
        //TODO: bring back to build when ready, currently only UI working
        //addOption(FILTERS_KEY, IP_FILTER_KEY, I18n.tr("IP Filter"), IPFilterPaneItem.class);
        addOption(FILTERS_KEY, RESULTS_KEY, I18n.tr("Keywords"), IgnoreResultsPaneItem.class);
        // advanced options
        addGroupTreeNode(OptionsMediator.ROOT_NODE_KEY, ADVANCED_KEY, I18n.tr("Advanced"));
        addOption(ADVANCED_KEY, PREFERENCING_KEY, I18n.tr("Updates"), AutomaticInstallerDownloadPaneItem.class);
        addOption(ADVANCED_KEY, EXPERIMENTAL_KEY, I18n.tr("Experimental"), ExperimentalFeaturesPaneItem.class);
        addOption(ADVANCED_KEY, FIREWALL_KEY, I18n.tr("Firewall"), RouterConfigurationPaneItem.class);
        addOption(ADVANCED_KEY, PROXY_KEY, I18n.tr("Proxy"), ProxyPaneItem.class, ProxyLoginPaneItem.class);
        addOption(ADVANCED_KEY, NETWORK_INTERFACE_KEY, I18n.tr("Network Interface"), NetworkInterfacePaneItem.class);
        if (FrostAssociations.anyAssociationsSupported()) {
            addOption(ADVANCED_KEY, ASSOCIATIONS_KEY, I18n.tr("File Associations"), AssociationPreferencePaneItem.class);
        }
        if (!CommonUtils.isPortable() && GUIUtils.shouldShowStartOnStartupWindow()) {
            addOption(ADVANCED_KEY, STARTUP_KEY, I18n.tr("System Boot"), StartupPaneItem.class);
        }
        if (!OSUtils.isAnyMac()) {
            addOption(OptionsMediator.ROOT_NODE_KEY, SHUTDOWN_KEY, I18n.tr("System Tray"), ShutdownPaneItem.class);
        }
        // debug
        addOption(OptionsMediator.ROOT_NODE_KEY, BUGS_KEY, I18n.tr("Bug Reports"), BugsPaneItem.class);
        OptionsTreeNode node = keysToNodes.get(ApplicationSettings.OPTIONS_LAST_SELECTED_KEY.getValue());
        if (node == null) {
            ApplicationSettings.OPTIONS_LAST_SELECTED_KEY.revertToDefault();
            node = keysToNodes.get(ApplicationSettings.OPTIONS_LAST_SELECTED_KEY.getValue());
        }
        return node;
    }

    private boolean isItunesSupported() {
        return !CommonUtils.isPortable() && (OSUtils.isMacOSX() || OSUtils.isWindows());
    }

    /**
     * Adds a parent node to the tree.  This node serves navigational
     * purposes only, and so has no corresponding <tt>OptionsPane</tt>.
     * This method allows for multiple tiers of parent nodes, not only
     * top-level parents.
     *
     * @param parentKey the key of the parent node to add this parent
     *                  node to
     * @param childKey  the key of the new parent node that is a child of
     *                  the <tt>parentKey</tt> argument
     */
    private void addGroupTreeNode(final String parentKey, final String childKey, String label) {
        TREE_MANAGER.addNode(parentKey, childKey, label, label);
    }

    /**
     * Adds the specified key and <tt>OptionsPane</tt> to current set of
     * options. This adds this <tt>OptionsPane</tt> to the set of
     * <tt>OptionsPane</tt>s the user can select.
     *
     * @param parentKey the key of the parent node to add the new node to
     */
    private OptionsTreeNode addOption(final String parentKey, final String childKey, final String label, @SuppressWarnings("unchecked") Class<? extends AbstractPaneItem>... clazzes) {
        OptionsTreeNode node = TREE_MANAGER.addNode(parentKey, childKey, label, label + " " + extractLabels(clazzes));
        node.setClasses(clazzes);
        keysToNodes.put(childKey, node);
        return node;
    }

    private String extractLabels(Class<?>... clazzes) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> clazz : clazzes) {
            Field[] fields = clazz.getFields();
            for (Field field : fields) {
                if ((field.getModifiers() & Modifier.FINAL) != 0 && field.getType() == String.class) {
                    try {
                        sb.append(field.get(null));
                        sb.append(" ");
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Makes the options window either visible or not visible depending on the
     * boolean argument.
     *
     * @param visible <tt>boolean</tt> value specifying whether the options
     *                window should be made visible or not visible
     * @param key     the unique identifying key of the panel to show
     */
    final void setOptionsVisible(boolean visible, final String key) {
        if (!visible) {
            DIALOG.dispose();
            OptionsMediator.instance().disposeOptions();
        } else {
            GUIUtils.centerOnScreen(DIALOG);
            //  initial tree selection
            if (key == null) {
                TREE_MANAGER.setSelection(ApplicationSettings.OPTIONS_LAST_SELECTED_KEY.getValue());
            } else {
                TREE_MANAGER.setSelection(key);
            }
            // make tree component the default component instead of the search field
            TREE_MANAGER.getComponent().requestFocusInWindow();
            DIALOG.setVisible(true);
        }
    }

    /**
     * Returns if the Options Box is visible.
     *
     * @return true if the Options Box is visible.
     */
    public final boolean isOptionsVisible() {
        return DIALOG.isVisible();
    }

    /**
     * Returns the main <tt>JDialog</tt> instance for the options window,
     * allowing other components to position themselves accordingly.
     *
     * @return the main options <tt>JDialog</tt> window
     */
    JDialog getMainOptionsComponent() {
        return DIALOG;
    }

    private void filter() {
        TREE_MANAGER.setFilterText(filterTextField.getText());
    }

    /**
     * Inner class that computes meaningful default dialog sizes for the options
     * dialog for different font size increments.
     * <p>
     * It also updates the width and height setting if the user changes the dialog
     * size manually.
     */
    static class DialogSizeSettingUpdater {
        static void install(JDialog dialog, IntSetting widthSetting, IntSetting heightSetting) {
            int width = widthSetting.getValue();
            int height = heightSetting.getValue();
            dialog.setSize(width, height);
            dialog.addComponentListener(new SizeChangeListener(widthSetting, heightSetting));
        }

        private static class SizeChangeListener extends ComponentAdapter {
            private final IntSetting widthSetting;
            private final IntSetting heightSetting;

            SizeChangeListener(IntSetting widthSetting, IntSetting heightSetting) {
                this.widthSetting = widthSetting;
                this.heightSetting = heightSetting;
            }

            @Override
            public void componentResized(ComponentEvent e) {
                Component c = e.getComponent();
                if (c.getWidth() != widthSetting.getValue() || c.getHeight() != heightSetting.getValue()) {
                    widthSetting.setValue(c.getWidth());
                    heightSetting.setValue(c.getHeight());
                }
            }
        }
    }
}
