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

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.gui.bittorrent.BTDownloadMediator;
import com.frostwire.gui.theme.IconRepainter;
import com.frostwire.gui.theme.SkinCheckBoxMenuItem;
import com.frostwire.gui.theme.SkinPopupMenu;
import com.limegroup.gnutella.gui.actions.LimeAction;
import com.limegroup.gnutella.gui.options.OptionsConstructor;
import com.limegroup.gnutella.gui.options.OptionsMediator;
import com.limegroup.gnutella.settings.ApplicationSettings;
import com.limegroup.gnutella.settings.SharingSettings;
import com.limegroup.gnutella.settings.StatusBarSettings;
import org.limewire.setting.BooleanSetting;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * The component for the space at the bottom of the main application
 * window, including the connected status and the media player.
 */
public final class StatusLine implements VPNStatusRefresher.VPNStatusListener {
    /**
     * The different connection status possibilities.
     */
    static final int STATUS_DISCONNECTED = 0;
    static final int STATUS_TURBOCHARGED = 1;
    private final ImageIcon[] _connectionQualityMeterIcons = new ImageIcon[7];
    private VPNStatusRefresher vpnStatusRefresher;
    private VPNStatusButton vpnStatusButton;
    private VPNDropProtectionCheckbox vpnDropProtectionCheckbox;
    /**
     * The main container for the status line component.
     */
    private JPanel BAR;
    /**
     * The left most panel containing the connection quality.
     * The switcher changes the actual ImageIcons on this panel.
     */
    private JLabel connectionQualityMeter;
    /**
     * The button for the current language flag to allow language switching
     */
    private LanguageButton languageButton;
    /**
     * The label with the firewall status.
     */
    private JLabel firewallStatus;
    /**
     * The labels for displaying the bandwidth usage.
     */
    private JLabel bandwidthUsageDown;
    private JLabel bandwidthUsageUp;
    /**
     * The right-click listener for the status bar.
     */
    private final MouseAdapter STATUS_BAR_LISTENER = new MouseAdapter() {
        public void mousePressed(MouseEvent me) {
            processMouseEvent(me);
        }

        public void mouseReleased(MouseEvent me) {
            processMouseEvent(me);
        }

        public void mouseClicked(MouseEvent me) {
            processMouseEvent(me);
        }

        void processMouseEvent(MouseEvent me) {
            final Component clickedComponent = me.getComponent();
            if (me.isPopupTrigger()) {
                JPopupMenu jpm = new SkinPopupMenu();
                //  add 'Show Connection Quality' menu item
                JCheckBoxMenuItem jcbmi = new SkinCheckBoxMenuItem(new ShowConnectionQualityAction());
                jcbmi.setState(StatusBarSettings.CONNECTION_QUALITY_DISPLAY_ENABLED.getValue());
                jpm.add(jcbmi);
                jcbmi = new SkinCheckBoxMenuItem(new ShowVPNAction());
                jcbmi.setState(StatusBarSettings.VPN_DISPLAY_ENABLED.getValue());
                jpm.add(jcbmi);
                jcbmi = new SkinCheckBoxMenuItem(new ShowVPNDropProtectionAction());
                jcbmi.setState(StatusBarSettings.VPN_DROP_PROTECTION_DISPLAY_ENABLED.getValue());
                jpm.add(jcbmi);
                //  add 'Show International Localization' menu item
                jcbmi = new SkinCheckBoxMenuItem(new ShowLanguageStatusAction());
                jcbmi.setState(getLanguageSetting().getValue());
                jpm.add(jcbmi);
                //  add 'Show Firewall Status' menu item
                jcbmi = new SkinCheckBoxMenuItem(new ShowFirewallStatusAction());
                jcbmi.setState(StatusBarSettings.FIREWALL_DISPLAY_ENABLED.getValue());
                jpm.add(jcbmi);
                //  add 'Show Bandwidth Consumption' menu item
                jcbmi = new SkinCheckBoxMenuItem(new ShowBandwidthConsumptionAction());
                jcbmi.setState(StatusBarSettings.BANDWIDTH_DISPLAY_ENABLED.getValue());
                jpm.add(jcbmi);

                jpm.pack();
                jpm.show(clickedComponent, me.getX(), me.getY());
            } else {
                // if they click on the speed indicators show them the active transfers.
                if (clickedComponent == bandwidthUsageUp || clickedComponent == bandwidthUsageDown) {
                    final GUIMediator.Tabs transfersTab = GUIMediator.Tabs.TRANSFERS.isEnabled() ?
                            GUIMediator.Tabs.TRANSFERS : GUIMediator.Tabs.SEARCH_TRANSFERS;
                    GUIMediator.instance().setWindow(transfersTab);
                }
            }
        }
    };
    private IconButton twitterButton;
    private IconButton facebookButton;
    private IconButton instagramButton;
    private IconButton seedingStatusButton;
    private IconButton discordButton;
    private DonationButtons donationButtons;
    private IconButton settingsButton;

    /**
     * Variables for the center portion of the status bar, which can display
     * the StatusComponent (progress bar during program load), the UpdatePanel
     * (notification that a new version of FrostWire is available), and the
     * StatusLinkHandler (ads for going PRO).
     */
    private StatusComponent STATUS_COMPONENT;
    private JPanel centerPanel;
    ///////////////////////////////////////////////////////////////////////////
    //  Construction
    /// ////////////////////////////////////////////////////////////////////////
    private Component centerComponent;
    private long _nextUpdateTime = System.currentTimeMillis();

    private boolean previousSeedingStatus;

    /**
     * Creates a new status line in the disconnected state.
     */
    StatusLine() {
        // Create the BAR panel immediately so it can be added to the layout
        getComponent();

        // Defer all expensive initialization to avoid EDT violation
        // StatusLine initialization triggers >2 second EDT blocking during startup
        SwingUtilities.invokeLater(() -> {
            GUIMediator.setSplashScreenString(I18n.tr("Loading Status Window..."));
            getComponent().addMouseListener(STATUS_BAR_LISTENER);
            GUIMediator.getAppFrame().addComponentListener(new ComponentListener() {
                public void componentResized(ComponentEvent arg0) {
                    refresh();
                }

                public void componentMoved(ComponentEvent arg0) {
                }

                public void componentShown(ComponentEvent arg0) {
                }

                public void componentHidden(ComponentEvent arg0) {
                }
            });
            GUIMediator.setSplashScreenString(I18n.tr("Creating donation buttons so you can give us a hand..."));
            createDonationButtonsComponent();
            //  make icons and panels for connection quality
            GUIMediator.setSplashScreenString(I18n.tr("Creating Connection Quality Indicator..."));
            createConnectionQualityPanel();
            // VPN status
            vpnStatusButton = new VPNStatusButton();
            vpnStatusRefresher = VPNStatusRefresher.getInstance();
            vpnStatusRefresher.addRefreshListener(vpnStatusButton);
            vpnStatusRefresher.addRefreshListener(this);
            // VPN-Drop protection checkbox
            vpnDropProtectionCheckbox = new VPNDropProtectionCheckbox();
            vpnStatusRefresher.addRefreshListener((vpnIsOn) -> {
                if (vpnDropProtectionCheckbox != null) {
                    vpnDropProtectionCheckbox.updateCheckboxState();
                }
            });
            //  make the 'Language' button
            GUIMediator.setSplashScreenString(I18n.tr("Adding flags here and there..."));
            createLanguageButton();
            //  make the 'Firewall Status' label
            GUIMediator.setSplashScreenString(I18n.tr("Playing with pixels for the Firewall indicator..."));
            createFirewallLabel();
            //  make the 'Bandwidth Usage' label
            bandwidthUsageDown = new LazyTooltip((ImageIcon) null);
            bandwidthUsageUp = new LazyTooltip((ImageIcon) null);
            createBandwidthLabel();
            // make the social buttons
            GUIMediator.setSplashScreenString(I18n.tr("Learning to socialize on Facebook..."));
            createFacebookButton();
            GUIMediator.setSplashScreenString(I18n.tr("Learning to socialize on Twitter..."));
            createTwitterButton();
            createInstagramButton();
            createDiscordButton();
            createSettingsButton();
            // male Seeding status label
            GUIMediator.setSplashScreenString(I18n.tr("Painting seeding sign..."));
            createSeedingStatusLabel();
            //  make the center panel
            GUIMediator.setSplashScreenString(I18n.tr("Creating center panel..."));
            createCenterPanel();
            // Set the bars to not be connected.
            setConnectionQuality(0);


            /*
             The refresh listener for updating the bandwidth usage every second.
            */
            GUIMediator.addRefreshListener(() -> {
                if (StatusBarSettings.BANDWIDTH_DISPLAY_ENABLED.getValue()) {
                    updateBandwidth();
                }
                updateCenterPanel();
            });
            refresh();
        });
    }

    public void updateVPNDropProtectionLabelState() {
        if (vpnStatusButton != null) {
            vpnStatusButton.onStatusUpdated(vpnStatusButton.getLastVPNStatus());
        }
    }

    public void updateVPNDropProtectionCheckboxState() {
        if (vpnDropProtectionCheckbox != null) {
            vpnDropProtectionCheckbox.updateCheckboxState();
        }
    }

    private void createDonationButtonsComponent() {
        donationButtons = new DonationButtons();
    }

    private void createTwitterButton() {
        twitterButton = new IconButton("TWITTER", true);
        initSocialButton(twitterButton, I18n.tr("Follow us @frostwire"), GUIConstants.TWITTER_FROSTWIRE_URL);
    }

    private void createInstagramButton() {
        instagramButton = new IconButton("INSTAGRAM", true);
        initSocialButton(instagramButton, I18n.tr("Follow FrostWire on Instagram"), GUIConstants.INSTAGRAM_FROSTWIRE_URL);
        instagramButton.setPreferredSize(new Dimension(22, 16));
    }

    private void createFacebookButton() {
        facebookButton = new IconButton("FACEBOOK", true);
        initSocialButton(facebookButton, I18n.tr("Like FrostWire on Facebook and stay in touch with the community. Get Help and Help Others."), GUIConstants.FACEBOOK_FROSTWIRE_URL);
    }

    private void createDiscordButton() {
        discordButton = new IconButton("DISCORD", true);
        initSocialButton(discordButton, I18n.tr("Join the FrostWire community on Discord"), GUIConstants.FROSTWIRE_CHAT_URL);
    }

    private void createSettingsButton() {
        // see icon_mapping.properties for the icon name (settings_gray_large.png)
        settingsButton = new IconButton("STATUS_LINE_SETTINGS_BUTTON", true);
        settingsButton.setAction(new SettingsButtonAction());
    }

    private void initSocialButton(IconButton socialButton, String toolTipText, final String url) {
        socialButton.setToolTipText(I18n.tr(toolTipText));
        socialButton.addActionListener(arg0 -> GUIMediator.openURL(url));
    }

    private void createSeedingStatusLabel() {
        seedingStatusButton = new IconButton("", "SEEDING", true) {
            @Override
            public String getToolTipText() {
                boolean seedingStatus = SharingSettings.SEED_FINISHED_TORRENTS.getValue();
                return "<html>" + (seedingStatus ? I18n.tr("<b>Seeding</b><p>completed torrent downloads.</p>") : I18n.tr("<b>Not Seeding</b><p>File chunks might be shared only during a torrent download.</p>") + "</html>");
            }
        };
        seedingStatusButton.setText(I18n.tr("Not Seeding")); // Default text
        seedingStatusButton.setIcon(GUIMediator.getThemeImage("not_seeding_small")); // Default icon
        previousSeedingStatus = false; // Set default value
        seedingStatusButton.addActionListener(e -> GUIMediator.instance().setOptionsVisible(true, OptionsConstructor.BITTORRENT_BASIC_KEY));
        ToolTipManager.sharedInstance().registerComponent(seedingStatusButton);
    }


    /**
     * Redraws the status bar based on changes to StatusBarSettings,
     * and makes sure it has room to add an indicator before adding it.
     */
    public void refresh() {
        //System.out.println("StatusLine refresh...");
        if (getComponent() != null) {
            getComponent().removeAll();
            //  figure out remaining width, and do not add indicators if no room
            int sepWidth = Math.max(2, createSeparator().getWidth());
            int remainingWidth = BAR.getWidth();
            if (remainingWidth <= 0)
                remainingWidth = ApplicationSettings.APP_WIDTH.getValue();
            //  subtract player as needed
            remainingWidth -= sepWidth;
            remainingWidth -= GUIConstants.SEPARATOR / 2;
            // subtract donation buttons as needed2
            if (donationButtons != null) {
                remainingWidth -= donationButtons.getWidth();
                remainingWidth -= GUIConstants.SEPARATOR;
            }
            //  subtract center component
            int indicatorWidth = 0;
            if (centerComponent != null) {
                indicatorWidth = centerComponent.getWidth();
                remainingWidth -= indicatorWidth;
            }
            //  add components to panel, if room
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(0, 0, 0, 0);
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.gridx = GridBagConstraints.RELATIVE;
            //  add connection quality indicator if there's room
            if (StatusBarSettings.CONNECTION_QUALITY_DISPLAY_ENABLED.getValue()) {
                remainingWidth = addStatusIndicator(connectionQualityMeter, sepWidth, remainingWidth, gbc);
            }
            if (StatusBarSettings.VPN_DISPLAY_ENABLED.getValue()) {
                vpnStatusRefresher.refresh(); // async call
                remainingWidth = addStatusIndicator(vpnStatusButton, sepWidth, remainingWidth, gbc);
            }
            //  add VPN-Drop protection checkbox if enabled and there's room
            if (StatusBarSettings.VPN_DROP_PROTECTION_DISPLAY_ENABLED.getValue() && vpnDropProtectionCheckbox != null) {
                remainingWidth = addStatusIndicator(vpnDropProtectionCheckbox, sepWidth, remainingWidth, gbc);
            }
            //  add the language button if there's room
            if (getLanguageSetting().getValue() && remainingWidth > indicatorWidth) {
                remainingWidth = addStatusIndicator(languageButton, sepWidth, remainingWidth, gbc);
            }
            //  then add firewall display if there's room
            if (StatusBarSettings.FIREWALL_DISPLAY_ENABLED.getValue()) {
                BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR), gbc);
                remainingWidth = addStatusIndicator(firewallStatus, sepWidth, remainingWidth, gbc);
                updateFirewall();
            }
            //  add bandwidth display if there's room
            indicatorWidth = GUIConstants.SEPARATOR + GUIConstants.SEPARATOR / 2 + sepWidth + Math.max((int) bandwidthUsageDown.getMinimumSize().getWidth(), bandwidthUsageDown.getWidth())
                    + Math.max((int) bandwidthUsageUp.getMinimumSize().getWidth(), bandwidthUsageUp.getWidth());
            if (StatusBarSettings.BANDWIDTH_DISPLAY_ENABLED.getValue() && remainingWidth > indicatorWidth) {
                BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR / 2), gbc);
                BAR.add(bandwidthUsageDown, gbc);
                BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR), gbc);
                BAR.add(bandwidthUsageUp, gbc);
                BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR / 2), gbc);
                BAR.add(createSeparator(), gbc);
                //remainingWidth -= indicatorWidth;
            }
            gbc = new GridBagConstraints();
            gbc.gridx = GridBagConstraints.RELATIVE;
            if (seedingStatusButton != null) {
                BAR.add(seedingStatusButton, gbc);
                BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR / 2), gbc);
                Component sep = createSeparator();
                if (sep != null) {
                    BAR.add(sep, gbc);
                }
                updateSeedingStatus();
                BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR), gbc);
            }
            if (facebookButton != null) {
                BAR.add(facebookButton, gbc);
                BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR), gbc);
            }
            if (twitterButton != null) {
                BAR.add(twitterButton, gbc);
                BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR), gbc);
            }
            if (instagramButton != null) {
                BAR.add(instagramButton, gbc);
                BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR), gbc);
            }
            Component sep = createSeparator();
            if (sep != null) {
                BAR.add(sep, gbc);
                BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR), gbc);
            }
            if (discordButton != null) {
                BAR.add(discordButton, gbc);
                BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR), gbc);
            }
            //  make center panel stretchy
            gbc.weightx = 1;
            if (centerPanel != null) {
                BAR.add(centerPanel, gbc);
            }
            gbc.weightx = 0;
            BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR / 2), gbc);
            // donation buttons
            if (donationButtons != null && StatusBarSettings.DONATION_BUTTONS_DISPLAY_ENABLED.getValue()) {
                BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR / 2), gbc);
                BAR.add(donationButtons, gbc);
                BAR.add(Box.createHorizontalStrut(10), gbc);
                BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR), gbc);
            }

            sep = createSeparator();
            if (sep != null) {
                BAR.add(sep, gbc);
            }
            if (settingsButton != null) {
                BAR.add(settingsButton);
            }

            try {
                //some macosx versions are throwing a deep NPE when this is invoked all the way down at
                //sun.lwawt.macosx.LWCToolkit.getScreenResolution(Unknown Source)
                BAR.validate();
            } catch (Throwable ignored) {
            }
            BAR.repaint();
        }
    }

    private int addStatusIndicator(JComponent component, int sepWidth, int remainingWidth, GridBagConstraints gbc) {
        int indicatorWidth;
        indicatorWidth = GUIConstants.SEPARATOR + Math.max((int) component.getMinimumSize().getWidth(), component.getWidth()) + sepWidth;
        if (remainingWidth > indicatorWidth) {
            BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR / 2), gbc);
            BAR.add(component, gbc);
            BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR / 2), gbc);
            BAR.add(createSeparator(), gbc);
            remainingWidth -= indicatorWidth;
        }
        return remainingWidth;
    }

    /**
     * Creates a vertical separator for visually separating status bar elements
     */
    private Component createSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        //  separators need preferred size in GridBagLayout
        sep.setPreferredSize(new Dimension(2, 30));
        sep.setMinimumSize(new Dimension(2, 30));
        return sep;
    }

    /**
     * Sets up connectionQualityMeter's icons - defers image loading to avoid EDT blocking
     */
    private void createConnectionQualityPanel() {
        // Create the label first without images to avoid EDT blocking
        connectionQualityMeter = new JLabel();
        connectionQualityMeter.setOpaque(false);
        connectionQualityMeter.setMinimumSize(new Dimension(34, 20));
        connectionQualityMeter.setMaximumSize(new Dimension(90, 30));
        //   add right-click listener
        connectionQualityMeter.addMouseListener(STATUS_BAR_LISTENER);

        // Defer image loading to avoid blocking EDT with file I/O
        SwingUtilities.invokeLater(this::updateTheme);
    }

    /**
     * Sets up the 'Language' button
     */
    private void createLanguageButton() {
        languageButton = new LanguageButton();
        languageButton.addMouseListener(STATUS_BAR_LISTENER);
        updateLanguage();
    }

    /**
     * Sets up the 'Firewall Status' label.
     */
    private void createFirewallLabel() {
        firewallStatus = new JLabel();
        firewallStatus.setOpaque(false);
        updateFirewall();
        // don't allow easy clipping
        firewallStatus.setMinimumSize(new Dimension(20, 20));
        // add right-click listener
        firewallStatus.addMouseListener(STATUS_BAR_LISTENER);
    }

    /**
     * _lwsStatus = new JLabel();
     * Sets up the 'Bandwidth Usage' label.
     */
    private void createBandwidthLabel() {
        // Load icons (this was deferred to avoid EDT blocking with file I/O)
        ImageIcon downloadIcon = (ImageIcon) IconRepainter.brightenIfDarkTheme(GUIMediator.getThemeImage("downloading_small"));
        ImageIcon uploadIcon = (ImageIcon) IconRepainter.brightenIfDarkTheme(GUIMediator.getThemeImage("uploading_small"));

        // Update existing placeholder labels with real icons
        if (bandwidthUsageDown != null) {
            bandwidthUsageDown.setIcon(downloadIcon);
        } else {
            bandwidthUsageDown = new LazyTooltip(downloadIcon);
        }

        if (bandwidthUsageUp != null) {
            bandwidthUsageUp.setIcon(uploadIcon);
        } else {
            bandwidthUsageUp = new LazyTooltip(uploadIcon);
        }

        //updateBandwidth();
        // don't allow easy clipping - increased to accommodate full transfer counts and speeds
        bandwidthUsageDown.setMinimumSize(new Dimension(110, 20));
        bandwidthUsageUp.setMinimumSize(new Dimension(110, 20));
        // add right-click listeners
        if (!bandwidthUsageDown.getMouseListeners().toString().contains("STATUS_BAR_LISTENER")) {
            bandwidthUsageDown.addMouseListener(STATUS_BAR_LISTENER);
        }
        if (!bandwidthUsageUp.getMouseListeners().toString().contains("STATUS_BAR_LISTENER")) {
            bandwidthUsageUp.addMouseListener(STATUS_BAR_LISTENER);
        }

        // Refresh the UI to show the new icons
        refresh();
    }

    /**
     * Sets up the center panel.
     */
    private void createCenterPanel() {
        STATUS_COMPONENT = new StatusComponent();
        centerComponent = new JLabel();
        centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);
        STATUS_COMPONENT.setProgressPreferredSize(new Dimension(250, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        centerPanel.add(STATUS_COMPONENT, gbc);
        //  add right-click listeners
        centerPanel.addMouseListener(STATUS_BAR_LISTENER);
        STATUS_COMPONENT.addMouseListener(STATUS_BAR_LISTENER);
    }

    /**
     * Updates the center panel if non-PRO.  Periodically rotates between
     * the update panel and the status link handler.
     */
    private void updateCenterPanel() {
        if (centerPanel == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (_nextUpdateTime > now)
            return;
        _nextUpdateTime = now + 1000 * 5; // update every minute
        centerPanel.removeAll();
        centerComponent = new JLabel();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        centerPanel.add(centerComponent, gbc);
        refresh();
    }

    private void updateSeedingStatus() {
        boolean seedingStatus = SharingSettings.SEED_FINISHED_TORRENTS.getValue();

        // Only update if there's a change in the seeding status
        if (seedingStatus != previousSeedingStatus) {
            // Update the label text and icon
            String newText = seedingStatus ? I18n.tr("Seeding") : I18n.tr("Not Seeding");
            ImageIcon newIcon = seedingStatus ? GUIMediator.getThemeImage("seeding_small") : GUIMediator.getThemeImage("not_seeding_small");
            newIcon = (ImageIcon) IconRepainter.brightenIfDarkTheme(newIcon);

            // Avoid flicker by checking if the content is already the same
            if (seedingStatusButton.getText() != null && !seedingStatusButton.getText().equals(newText)) {
                seedingStatusButton.setText(newText);
            }

            if (seedingStatusButton.getIcon() != null && !seedingStatusButton.getIcon().equals(newIcon)) {
                seedingStatusButton.setIcon(newIcon);
            }

            // Force a re-layout of the component to ensure proper sizing
            seedingStatusButton.revalidate();
            seedingStatusButton.repaint();

            // Update previous seeding status
            previousSeedingStatus = seedingStatus;
        }
    }


    /**
     * Updates the status text.
     */
    void setStatusText(final String text) {
        if (STATUS_COMPONENT != null) {
            GUIMediator.safeInvokeAndWait(() -> STATUS_COMPONENT.setText(text));
        }
    }

    /**
     * Updates the firewall text.
     */
    private void updateFirewallLabel(boolean notFirewalled) {
        String iconName = "firewall";
        String tooltip = I18n.tr("FrostWire has detected a firewall");
        if (notFirewalled) {
            iconName = "firewall_no";
            tooltip = I18n.tr("FrostWire has not detected a firewall");
        }
        firewallStatus.setIcon(IconRepainter.brightenIfDarkTheme(GUIMediator.getThemeImage(iconName)));
        firewallStatus.setToolTipText(tooltip);
    }

    /**
     * Updates the image on the flag
     */
    void updateLanguage() {
        languageButton.updateLanguageFlag();
    }

    /**
     * Updates the firewall text.
     */
    private void updateFirewall() {
        try {
            BTEngine engine = BTEngine.getInstance();
            updateFirewallLabel(!engine.isFirewalled());
        } catch (Throwable ignored) {
        }
    }

    /**
     * Updates the bandwidth statistics.
     */
    private void updateBandwidth() {
        try {
            //  format strings
            String sDown = GUIUtils.rate2speed(GUIMediator.instance().getBTDownloadMediator().getDownloadsBandwidth());
            String sUp = GUIUtils.rate2speed(GUIMediator.instance().getBTDownloadMediator().getUploadsBandwidth());
            // number of uploads (seeding) and downloads
            int downloads = GUIMediator.instance().getCurrentDownloads();
            int uploads = GUIMediator.instance().getCurrentUploads();
            bandwidthUsageDown.setText(downloads + " @ " + sDown);
            bandwidthUsageUp.setText(uploads + " @ " + sUp);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Notification that loading has finished.
     * <p>
     * The loading label is removed and the update notification
     * component is added.  If necessary, the center panel will
     * rotate back and forth between displaying the update
     * notification and displaying the StatusLinkHandler.
     */
    void loadFinished() {
        updateCenterPanel();
        if (centerPanel != null) {
            centerPanel.revalidate();
            centerPanel.repaint();
        }
        refresh();
    }

    /**
     * Load connection quality theme icons - this was deferred to avoid EDT blocking with file I/O
     */
    private void updateTheme() {
        try {
            _connectionQualityMeterIcons[StatusLine.STATUS_DISCONNECTED] = GUIMediator.getThemeImage("connect_small_0");
            _connectionQualityMeterIcons[StatusLine.STATUS_TURBOCHARGED] = GUIMediator.getThemeImage("connect_small_6");
            // Trigger UI refresh to display the newly loaded icons
            if (connectionQualityMeter != null) {
                connectionQualityMeter.revalidate();
                connectionQualityMeter.repaint();
            }
        } catch (Exception e) {
            // Log error but don't crash if theme loading fails
            e.printStackTrace();
        }
    }

    /**
     * Alters the displayed connection quality.
     */
    void setConnectionQuality(int quality) {
        // make sure we don't go over our bounds.
        if (quality >= _connectionQualityMeterIcons.length) {
            quality = _connectionQualityMeterIcons.length - 1;
        }
        connectionQualityMeter.setIcon(_connectionQualityMeterIcons[quality]);
        String status = null;
        String tip = null;
        switch (quality) {
            case STATUS_DISCONNECTED:
                status = I18n.tr("Disconnected");
                tip = I18n.tr("Check your internet connection, FrostWire can't connect.");
                break;
            case STATUS_TURBOCHARGED:
                status = I18n.tr("Turbo-Charged");
                tip = I18n.tr("Your connection to the network is extremely strong");
                break;
        }
        long dhtNodes = BTEngine.getInstance().dhtNodes();
        if (dhtNodes > 0) {
            String updatedToolTip = tip + ". (DHT: " + dhtNodes + " " + I18n.tr("nodes") + ")";
            connectionQualityMeter.setToolTipText(updatedToolTip);
        } else {
            connectionQualityMeter.setToolTipText(tip);
        }
        connectionQualityMeter.setText(status);
    }

    /**
     * Accessor for the `JComponent` instance that contains all
     * of the panels for the status line.
     *
     * @return the `JComponent` instance that contains all
     * of the panels for the status line
     */
    public JComponent getComponent() {
        if (BAR == null) {
            BAR = new JPanel(new GridBagLayout());
        }
        return BAR;
    }

    private BooleanSetting getLanguageSetting() {
        if (GUIMediator.isEnglishLocale()) {
            return StatusBarSettings.LANGUAGE_DISPLAY_ENGLISH_ENABLED;
        } else {
            return StatusBarSettings.LANGUAGE_DISPLAY_ENABLED;
        }
    }

    @Override
    public void onStatusUpdated(boolean vpnIsOn) {
        refresh();
    }

    /**
     * Action for the 'Show Connection Quality' menu item.
     */
    private class ShowConnectionQualityAction extends AbstractAction {
        ShowConnectionQualityAction() {
            putValue(Action.NAME, I18n.tr("Show Connection Quality"));
        }

        public void actionPerformed(ActionEvent e) {
            StatusBarSettings.CONNECTION_QUALITY_DISPLAY_ENABLED.invert();
            refresh();
        }
    }

    /**
     * Action for the 'Show Connection Quality' menu item.
     */
    private class ShowVPNAction extends AbstractAction {
        ShowVPNAction() {
            putValue(Action.NAME, I18n.tr("Show Connection Privacy Status"));
        }

        public void actionPerformed(ActionEvent e) {
            StatusBarSettings.VPN_DISPLAY_ENABLED.invert();
            refresh();
        }
    }

    /**
     * Action for the 'Show Firewall Status' menu item.
     */
    private class ShowLanguageStatusAction extends AbstractAction {
        ShowLanguageStatusAction() {
            putValue(Action.NAME, I18n.tr("Show Language Status"));
        }

        public void actionPerformed(ActionEvent e) {
            BooleanSetting setting = getLanguageSetting();
            setting.invert();
            StatusBarSettings.LANGUAGE_DISPLAY_ENABLED.setValue(setting.getValue());
            StatusBarSettings.LANGUAGE_DISPLAY_ENGLISH_ENABLED.setValue(setting.getValue());
            refresh();
        }
    }

    /**
     * Action for the 'Show Firewall Status' menu item.
     */
    private class ShowFirewallStatusAction extends AbstractAction {
        ShowFirewallStatusAction() {
            putValue(Action.NAME, I18n.tr("Show Firewall Status"));
        }

        public void actionPerformed(ActionEvent e) {
            StatusBarSettings.FIREWALL_DISPLAY_ENABLED.invert();
            refresh();
        }
    }

    /**
     * Action for the 'Show Bandwidth Consumption' menu item.
     */
    private class ShowBandwidthConsumptionAction extends AbstractAction {
        ShowBandwidthConsumptionAction() {
            putValue(Action.NAME, I18n.tr("Show Bandwidth Consumption"));
        }

        public void actionPerformed(ActionEvent e) {
            StatusBarSettings.BANDWIDTH_DISPLAY_ENABLED.invert();
            refresh();
        }
    }

    private class ShowDonationButtonsAction extends AbstractAction {
        ShowDonationButtonsAction() {
            putValue(Action.NAME, I18n.tr("Show Donation Buttons"));
        }

        public void actionPerformed(ActionEvent e) {
            StatusBarSettings.DONATION_BUTTONS_DISPLAY_ENABLED.invert();
            refresh();
        }
    }

    /**
     * Action for the 'Show VPN-Drop Protection' menu item.
     */
    private class ShowVPNDropProtectionAction extends AbstractAction {
        ShowVPNDropProtectionAction() {
            putValue(Action.NAME, I18n.tr("Show VPN-Drop Protection"));
        }

        public void actionPerformed(ActionEvent e) {
            StatusBarSettings.VPN_DROP_PROTECTION_DISPLAY_ENABLED.invert();
            refresh();
        }
    }

    private class SettingsButtonAction extends AbstractAction {
        SettingsButtonAction() {
            putValue(Action.NAME, I18n.tr("Settings"));
            putValue(Action.NAME, I18n.tr("Settings"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Settings"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Settings"));
            putValue(LimeAction.ICON_NAME, "STATUS_LINE_SETTINGS_BUTTON");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            OptionsMediator options = OptionsMediator.instance();
            options.setOptionsVisible(!options.isOptionsVisible());
        }
    }

    private class LazyTooltip extends JLabel {
        LazyTooltip(ImageIcon icon) {
            super(icon);
            setOpaque(false);
            ToolTipManager.sharedInstance().registerComponent(this);
        }

        @Override
        public String getToolTipText() {
            BTDownloadMediator btDownloadMediator = GUIMediator.instance().getBTDownloadMediator();
            String sDown = GUIUtils.rate2speed(btDownloadMediator.getDownloadsBandwidth());
            String sUp = GUIUtils.rate2speed(btDownloadMediator.getUploadsBandwidth());
            String totalDown = GUIUtils.getBytesInHuman(btDownloadMediator.getTotalBytesDownloaded());
            String totalUp = GUIUtils.getBytesInHuman(btDownloadMediator.getTotalBytesUploaded());
            int downloads = GUIMediator.instance().getCurrentDownloads();
            int uploads = GUIMediator.instance().getCurrentUploads();
            //  create good-looking table tooltip
            return "<html><table>" +
                    "<tr><td>" +
                    I18n.tr("Downloads:") +
                    "</td><td>" + downloads +
                    "</td><td>@</td><td align=right>" +
                    sDown + "</td></tr>" + "<tr><td>" +
                    I18n.tr("Uploads:") + "</td><td>" +
                    uploads + "</td><td>@</td><td align=right>" +
                    sUp + "</td></tr>" + "<tr><td>" +
                    I18n.tr("Total Downstream:") +
                    "</td><td>" + totalDown + "</td></tr>" +
                    "<tr><td>" + I18n.tr("Total Upstream:") +
                    "</td><td>" + totalUp +
                    "</td></tr>" + "</table></html>";
        }
    }
}
