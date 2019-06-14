/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.

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

import com.frostwire.gui.library.LibraryMediator;
import com.frostwire.gui.player.MediaPlayerComponent;
import com.frostwire.gui.searchfield.GoogleSearchField;
import com.frostwire.gui.searchfield.SearchField;
import com.frostwire.gui.tabs.LibraryTab;
import com.frostwire.gui.tabs.Tab;
import com.frostwire.gui.theme.SkinApplicationHeaderUI;
import com.frostwire.gui.theme.ThemeMediator;
import com.frostwire.gui.updates.UpdateMediator;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator.Tabs;
import com.limegroup.gnutella.gui.actions.FileMenuActions;
import com.limegroup.gnutella.gui.search.SearchInformation;
import com.limegroup.gnutella.gui.search.SearchMediator;
import com.limegroup.gnutella.settings.SearchSettings;
import com.limegroup.gnutella.util.URLDecoder;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.*;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public final class ApplicationHeader extends JPanel implements RefreshListener {
    /*
     * The property to store the selected icon in.
     */
    private static final String SELECTED_ICON = "SELECTED_ICON";
    /**
     * The property to store the unselected icon in.
     */
    private static final String DESELECTED_ICON = "DESELECTED_ICON";
    private static final String CLOUD_SEARCH_FIELD = "cloud_search_field";
    private static final String LIBRARY_SEARCH_FIELD = "library_search_field";
    private static final String CLOUD_SEARCH_FIELD_HINT_TEXT = I18n.tr("Search or enter a cloud sourced URL");
    /**
     * The clicker forwarder.
     */
    private final MouseListener CLICK_FORWARDER = new Clicker();
    /**
     * Button background for selected button
     */
    private final Image headerButtonBackgroundSelected;
    /**
     * Button background for unselected button
     */
    private final Image headerButtonBackgroundUnselected;
    private final GoogleSearchField cloudSearchField;
    private final JPanel searchPanels;
    private final LogoPanel logoPanel;
    private JLabel updateButton;
    private ImageIcon updateImageButtonOn;
    private ImageIcon updateImageButtonOff;
    private long updateButtonAnimationStartedTimestamp;
    private SearchField librarySearchField;

    ApplicationHeader(Map<Tabs, Tab> tabs) {
        setMinimumSize(new Dimension(1000, 54));
        setLayout(new MigLayout("ins 0, ay 50%, filly,", "[][][grow][][]"));
        headerButtonBackgroundSelected = GUIMediator.getThemeImage("selected_header_button_background").getImage();
        headerButtonBackgroundUnselected = GUIMediator.getThemeImage("unselected_header_button_background").getImage();
        cloudSearchField = new GoogleSearchField();
        searchPanels = createSearchPanel();
        add(searchPanels, "wmin 240px, wmax 370px, growprio 50, growx, gapright 10px, gapleft 5px");
        addTabButtons(tabs);
        createUpdateButton();
        JPanel logoUpdateButtonsPanel = new JPanel();
        logoPanel = new LogoPanel();
        //only one will be shown at the time.
        logoUpdateButtonsPanel.add(logoPanel);
        logoUpdateButtonsPanel.add(updateButton);
        add(logoUpdateButtonsPanel, "growx, alignx center");
        JComponent player = new MediaPlayerComponent().getMediaPanel();
        add(player, "dock east, growy, gapafter 10px!");
        GUIMediator.addRefreshListener(this);
        final ActionListener schemaListener = new SchemaListener();
        schemaListener.actionPerformed(null);
    }

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new CardLayout());
        initCloudSearchField();
        createLibrarySearchField();
        panel.add(cloudSearchField, CLOUD_SEARCH_FIELD);
        panel.add(librarySearchField, LIBRARY_SEARCH_FIELD);
        return panel;
    }

    private void createLibrarySearchField() {
        librarySearchField = LibraryMediator.instance().getLibrarySearch().getSearchField();
    }

    private void initCloudSearchField() {
        cloudSearchField.addActionListener(new SearchListener());
        cloudSearchField.setPrompt(CLOUD_SEARCH_FIELD_HINT_TEXT);
        cloudSearchField.setText(CLOUD_SEARCH_FIELD_HINT_TEXT);
        cloudSearchField.selectAll();
        cloudSearchField.requestFocus();
        Font origFont = cloudSearchField.getFont();
        Font newFont = origFont.deriveFont(origFont.getSize2D() + 2f);
        cloudSearchField.setFont(newFont);
        cloudSearchField.setMargin(new Insets(0, 2, 0, 0));
        cloudSearchField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (cloudSearchField.getText().equals(CLOUD_SEARCH_FIELD_HINT_TEXT)) {
                    cloudSearchField.setText("");
                }
            }
        });
    }

    private void createUpdateButton() {
        updateImageButtonOn = GUIMediator.getThemeImage("update_button_on");
        updateImageButtonOff = GUIMediator.getThemeImage("update_button_off");
        updateButton = new JLabel(updateImageButtonOn);
        updateButton.setToolTipText(I18n.tr("A new update has been downloaded."));
        Dimension d = new Dimension(32, 32);
        updateButton.setVisible(false);
        updateButton.setSize(d);
        updateButton.setPreferredSize(d);
        updateButton.setMinimumSize(d);
        updateButton.setMaximumSize(d);
        updateButton.setBorder(null);
        updateButton.setOpaque(false);
        updateButtonAnimationStartedTimestamp = -1;
        updateButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                UpdateMediator.instance().showUpdateMessage();
            }
        });
    }

    private void addTabButtons(final Map<Tabs, Tab> tabs) {
        JPanel buttonContainer = new JPanel(new MigLayout("insets 0, gap 0"));
        ButtonGroup group = new ButtonGroup();
        Font buttonFont = new Font("Helvetica", Font.BOLD, 10);
        buttonContainer.add(ThemeMediator.createAppHeaderSeparator(), "growy");
        for (Tabs t : GUIMediator.Tabs.values()) {
            final Tabs finalTab = t; //java...
            if (tabs.get(t) == null || !t.isEnabled()) {
                continue;
            }
            AbstractButton button = createTabButton(tabs.get(t));
            button.setFont(buttonFont);
            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    onMainApplicationHeaderTabActionPerformed(tabs, finalTab);
                }

                private void onMainApplicationHeaderTabActionPerformed(final Map<Tabs, Tab> tabs, final Tabs tab) {
                    prepareSearchTabAsSearchTrigger(tab);
                    showSearchField(tabs.get(tab));
                    GUIMediator.instance().setWindow(tab);
                }

                void prepareSearchTabAsSearchTrigger(final Tabs tab) {
                    String query = null;
                    if (tab == Tabs.SEARCH || tab == Tabs.SEARCH_TRANSFERS) {
                        if (!cloudSearchField.getText().isEmpty()) {
                            if (cloudSearchField.getText().equals(CLOUD_SEARCH_FIELD_HINT_TEXT)) {
                                cloudSearchField.setText("");
                            }
                            query = cloudSearchField.getText();
                        } else if (cloudSearchField.getText().isEmpty() && !librarySearchField.getText().isEmpty()) {
                            //they want internet search while on the library
                            query = librarySearchField.getText();
                            librarySearchField.setText("");
                            cloudSearchField.setText(query);
                        }
                        if (query != null) {
                            cloudSearchField.getActionListeners()[0].actionPerformed(null);
                        }
                    }
                }
            });
            group.add(button);
            buttonContainer.add(button);
            buttonContainer.add(ThemeMediator.createAppHeaderSeparator(), "growy, w 0px");
            button.setSelected(t.equals(GUIMediator.Tabs.SEARCH));
        }
        add(buttonContainer, "");
    }

    /**
     * Given a Tab mark that button as selected
     * <p>
     * Since we don't keep explicit references to the buttons this method
     * walks over the components in the ApplicationHeader until it finds
     * the AbstractButton that has the Tab object as a client property named "tab"
     *
     * @see MainFrame#setSelectedTab(Tabs)
     */
    void selectTab(Tab t) {
        Component[] components = getComponents();
        JPanel buttonContainer = (JPanel) components[1];
        Component[] buttons = buttonContainer.getComponents();
        for (Component c : buttons) {
            if (c instanceof AbstractButton) {
                AbstractButton b = (AbstractButton) c;
                if (b.getClientProperty("tab").equals(t)) {
                    b.setSelected(true);
                    return;
                }
            }
        }
    }

    private AbstractButton createTabButton(Tab t) {
        Icon icon = t.getIcon();
        Icon disabledIcon = null;
        Icon rolloverIcon = null;
        final JRadioButton button = new JRadioButton(I18n.tr(t.getTitle())) {
            protected void paintComponent(Graphics g) {
                if (isSelected()) {
                    g.drawImage(headerButtonBackgroundSelected, 0, 0, null);
                } else {
                    g.drawImage(headerButtonBackgroundUnselected, 0, 0, null);
                }
                super.paintComponent(g);
            }
        };
        button.putClientProperty("tab", t);
        button.putClientProperty(SELECTED_ICON, icon);
        if (icon != null) {
            disabledIcon = icon;//ImageManipulator.darken(icon);
            rolloverIcon = icon;//ImageManipulator.brighten(icon);
        }
        button.putClientProperty(DESELECTED_ICON, disabledIcon);
        button.setIcon(disabledIcon);
        button.setPressedIcon(rolloverIcon);
        button.setSelectedIcon(disabledIcon);
        button.setRolloverIcon(rolloverIcon);
        button.setRolloverSelectedIcon(rolloverIcon);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.addMouseListener(CLICK_FORWARDER);
        button.setToolTipText(t.getToolTip());
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setForeground(ThemeMediator.TAB_BUTTON_FOREGROUND_COLOR);
        Dimension buttonDim = new Dimension(65, 55);
        button.setPreferredSize(buttonDim);
        button.setMinimumSize(buttonDim);
        button.setMaximumSize(buttonDim);
        button.setSelected(false);
        return button;
    }

    void showSearchField(Tab t) {
        cloudSearchField.setText("");
        librarySearchField.setText("");
        CardLayout cl = (CardLayout) (searchPanels.getLayout());
        if (t instanceof LibraryTab) {
            cl.show(searchPanels, LIBRARY_SEARCH_FIELD);
        } else {
            cl.show(searchPanels, CLOUD_SEARCH_FIELD);
        }
    }

    @Override
    public void refresh() {
        showUpdateButton(!UpdateMediator.instance().isUpdated() && UpdateMediator.instance().isUpdateDownloaded());
    }

    private void showUpdateButton(boolean show) {
        if (updateButton.isVisible() == show) {
            return;
        }
        logoPanel.setVisible(!show);
        updateButton.setVisible(show);
        if (show) {
            //Start animating the button for 30 seconds.
            if (updateButtonAnimationStartedTimestamp == -1) {
                startUpdateButtonIntermittentAnimation();
            }
        }
    }

    private void startUpdateButtonIntermittentAnimation() {
        updateButtonAnimationStartedTimestamp = System.currentTimeMillis();
        //start animation thread.
        Thread t = new Thread("update-button-animation") {
            private long updateButtonAnimationLastChange;

            public void run() {
                long now = System.currentTimeMillis();
                updateButtonAnimationLastChange = now;
                boolean buttonState = true;
                long ANIMATION_DURATION = 30000;
                while (now - updateButtonAnimationStartedTimestamp < ANIMATION_DURATION) {
                    long ANIMATION_INTERVAL = 1000;
                    if (now - updateButtonAnimationLastChange >= ANIMATION_INTERVAL) {
                        switchButtonImage(buttonState);
                        buttonState = !buttonState;
                    }
                    try {
                        sleep(ANIMATION_INTERVAL);
                    } catch (Throwable ignored) {
                    }
                    now = System.currentTimeMillis();
                }
                switchButtonImage(false);
            }

            void switchButtonImage(final boolean state) {
                updateButtonAnimationLastChange = System.currentTimeMillis();
                GUIMediator.safeInvokeLater(() -> updateButton.setIcon(state ? updateImageButtonOn : updateImageButtonOff));
            }
        };
        t.start();
    }

    @Override
    public void updateUI() {
        ComponentUI ui = UIManager.getUI(this);
        if (ui == null) {
            ui = new SkinApplicationHeaderUI();
        }
        setUI(ui);
    }

    @Override
    public String getUIClassID() {
        return "ApplicationHeaderUI";
    }

    private void requestSearchFocusImmediately() {
        if (cloudSearchField != null) {
            cloudSearchField.setPrompt(CLOUD_SEARCH_FIELD_HINT_TEXT);
            cloudSearchField.setText(CLOUD_SEARCH_FIELD_HINT_TEXT);
            cloudSearchField.selectAll();
            cloudSearchField.requestFocus();
        }
    }

    public void requestSearchFocus() {
        // Workaround for bug manifested on Java 1.3 where FocusEvents
        // are improperly posted, causing BasicTabbedPaneUI to throw an
        // ArrayIndexOutOfBoundsException.
        // See:
        // http://developer.java.sun.com/developer/bugParade/bugs/4523606.html
        // http://developer.java.sun.com/developer/bugParade/bugs/4379600.html
        // http://developer.java.sun.com/developer/bugParade/bugs/4128120.html
        // for related problems.
        SwingUtilities.invokeLater(this::requestSearchFocusImmediately);
    }

    void startSearch(String query) {
        cloudSearchField.setText(query);
        cloudSearchField.getActionListeners()[0].actionPerformed(null);
    }

    /**
     * Forwards click events from a panel to the panel's component.
     */
    private static class Clicker extends MouseAdapter {
        public void mouseClicked(MouseEvent e) {
            JComponent c = (JComponent) e.getSource();
            if (!(c instanceof AbstractButton)) {
                AbstractButton b = (AbstractButton) c.getComponent(0);
                b.doClick();
            }
        }
    }

    private class SearchListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            // Keep the query if there was one before switching to the search tab.
            String query = cloudSearchField.getText();
            String queryTitle = query;
            GUIMediator.instance().setWindow(GUIMediator.Tabs.SEARCH);
            // Start a download from the search box by entering a URL.
            if (FileMenuActions.openMagnetOrTorrent(query)) {
                cloudSearchField.setText("");
                cloudSearchField.hidePopup();
                return;
            }
            if (query.contains("www.frostclick.com/cloudplayer/?type=yt") ||
                    query.contains("frostwire-preview.com/?type=yt")) {
                try {
                    query = query.split("detailsUrl=")[1];
                    query = URLDecoder.decode(query);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
            final SearchInformation info = SearchInformation.createTitledKeywordSearch(query, null, MediaType.getTorrentMediaType(), queryTitle);
            // If the search worked, store & clear it.
            if (SearchMediator.instance().triggerSearch(info) != 0) {
                if (info.isKeywordSearch()) {
                    cloudSearchField.addToDictionary();
                    // Clear the existing search.
                    cloudSearchField.setText("");
                    cloudSearchField.hidePopup();
                }
            }
        }
    }

    private class SchemaListener implements ActionListener {
        public void actionPerformed(ActionEvent event) {
            SearchSettings.MAX_QUERY_LENGTH.revertToDefault();
            //Truncate if you have too much text for a Gnutella search
            if (cloudSearchField.getText().length() > SearchSettings.MAX_QUERY_LENGTH.getValue()) {
                try {
                    cloudSearchField.setText(cloudSearchField.getText(0, SearchSettings.MAX_QUERY_LENGTH.getValue()));
                } catch (BadLocationException ignored) {
                }
            }
            requestSearchFocus();
        }
    }
}
