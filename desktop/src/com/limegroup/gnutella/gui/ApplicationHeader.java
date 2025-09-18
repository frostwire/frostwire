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

import com.frostwire.gui.library.LibraryMediator;
import com.frostwire.gui.player.MediaPlayerComponent;
import com.frostwire.gui.searchfield.GoogleSearchField;
import com.frostwire.gui.searchfield.SearchField;
import com.frostwire.gui.tabs.LibraryTab;
import com.frostwire.gui.tabs.Tab;
import com.frostwire.gui.theme.SkinApplicationHeaderUI;
import com.frostwire.gui.theme.ThemeMediator;
import com.frostwire.gui.updates.UpdateMediator;
import com.limegroup.gnutella.gui.GUIMediator.Tabs;
import com.limegroup.gnutella.settings.SearchSettings;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.*;
import java.util.Map;

import static com.frostwire.gui.searchfield.GoogleSearchField.CLOUD_SEARCH_FIELD_HINT_TEXT;

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

        // Setup the program logo and update buttons
        JPanel logoUpdateButtonsPanel = new JPanel();
        logoUpdateButtonsPanel.setOpaque(false);
        logoPanel = new LogoPanel();
        createUpdateButton();
        //only one will be shown at the time.
        logoUpdateButtonsPanel.add(logoPanel);
        logoUpdateButtonsPanel.add(updateButton);
        add(logoUpdateButtonsPanel, "growx, alignx center");

        // Setup the tab buttons
        addTabButtons(tabs);

        // Setup the search field
        cloudSearchField = new GoogleSearchField();
        searchPanels = createSearchPanel();
        searchPanels.setOpaque(false);
        add(searchPanels, "alignx center, growx");

        // Setup the media player
        JComponent player = new MediaPlayerComponent().getMediaPanel();
        player.setOpaque(false);
        add(player, "dock east, growy, gapafter 10px!");
        GUIMediator.addRefreshListener(this);
        final ActionListener schemaListener = new SchemaListener();
        schemaListener.actionPerformed(null);
    }

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new CardLayout());
        createLibrarySearchField();
        panel.add(cloudSearchField, CLOUD_SEARCH_FIELD);
        panel.add(librarySearchField, LIBRARY_SEARCH_FIELD);
        return panel;
    }

    private void createLibrarySearchField() {
        librarySearchField = LibraryMediator.instance().getLibrarySearch().getSearchField();
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
        buttonContainer.setOpaque(false);
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
        try {
            ComponentUI ui = UIManager.getUI(this);
            if (ui == null) {
                ui = new SkinApplicationHeaderUI();
            }
            setUI(ui);
        } catch (Throwable ignored) {
            ignored.printStackTrace();
        }
    }

    @Override
    public String getUIClassID() {
        return "ApplicationHeaderUI";
    }

    private void requestSearchFocusImmediately() {
        if (cloudSearchField != null) {
            cloudSearchField.setPrompt(CLOUD_SEARCH_FIELD_HINT_TEXT);
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
