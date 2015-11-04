/*
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

package com.limegroup.gnutella.gui;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.gui.bittorrent.BTDownloadMediator;
import com.frostwire.gui.theme.SkinCheckBoxMenuItem;
import com.frostwire.gui.theme.SkinPopupMenu;
import com.frostwire.jlibtorrent.Session;
import com.limegroup.gnutella.gui.options.OptionsConstructor;
import com.limegroup.gnutella.gui.util.Constants;
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
public final class StatusLine {

    /**
     * The different connection status possibilities.
     */
    public static final int STATUS_DISCONNECTED = 0;
    public static final int STATUS_TURBOCHARGED = 1;

    /**
     * The main container for the status line component.
     */
    private JPanel BAR;

    /**
     * The left most panel containing the connection quality.
     * The switcher changes the actual ImageIcons on this panel.
     */
    private JLabel _connectionQualityMeter;
    private final ImageIcon[] _connectionQualityMeterIcons = new ImageIcon[7];

    private final VPNStatusButton _vpnStatus = new VPNStatusButton();

    /**
     * The button for the current language flag to allow language switching
     */
    private LanguageButton _languageButton;

    /**
     * The label with the firewall status.
     */
    private JLabel _firewallStatus;

    /**
     * The labels for displaying the bandwidth usage.
     */
    private JLabel _bandwidthUsageDown;
    private JLabel _bandwidthUsageUp;

    private IconButton _twitterButton;
    private IconButton _facebookButton;
    private IconButton _googlePlusButton;

    private IconButton seedingStatusButton;

    private DonationButtons _donationButtons;

    /**
     * Variables for the center portion of the status bar, which can display
     * the StatusComponent (progress bar during program load), the UpdatePanel
     * (notification that a new version of FrostWire is available), and the
     * StatusLinkHandler (ads for going PRO).
     */
    private StatusComponent STATUS_COMPONENT;
    private JPanel _centerPanel;
    private Component _centerComponent;
    private long _lastOnTotalNodesUpdate;

    ///////////////////////////////////////////////////////////////////////////
    //  Construction
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Creates a new status line in the disconnected state.
     */
    public StatusLine() {
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

        //  make the 'Language' button
        GUIMediator.setSplashScreenString(I18n.tr("Adding flags here and there..."));
        createLanguageButton();

        //  make the 'Firewall Status' label
        GUIMediator.setSplashScreenString(I18n.tr("Playing with pixels for the Firewall indicator..."));
        createFirewallLabel();

        //  make the 'Bandwidth Usage' label
        createBandwidthLabel();

        // make the social buttons
        GUIMediator.setSplashScreenString(I18n.tr("Learning to socialize on Facebook..."));
        createFacebookButton();
        GUIMediator.setSplashScreenString(I18n.tr("Learning to socialize on Twitter..."));
        createTwitterButton();
        GUIMediator.setSplashScreenString(I18n.tr("Learning to socialize on G+..."));
        createGooglePlusButton();

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
        RefreshListener REFRESH_LISTENER = new RefreshListener() {
            public void refresh() {
                if (StatusBarSettings.BANDWIDTH_DISPLAY_ENABLED.getValue()) {
                    updateBandwidth();
                }
                updateCenterPanel();
            }
        };
        GUIMediator.addRefreshListener(REFRESH_LISTENER);

        refresh();
    }

    private void createDonationButtonsComponent() {
        _donationButtons = new DonationButtons();
    }

    private void createTwitterButton() {
        _twitterButton = new IconButton("TWITTER");
        initSocialButton(_twitterButton, I18n.tr("Follow us @frostwire"), Constants.TWITTER_FROSTWIRE_URL);
    }

    private void createFacebookButton() {
        _facebookButton = new IconButton("FACEBOOK");
        initSocialButton(_facebookButton, I18n.tr("Like FrostWire on Facebook and stay in touch with the community. Get Help and Help Others."), Constants.FACEBOOK_FROSTWIRE_URL);
    }

    private void createGooglePlusButton() {
        _googlePlusButton = new IconButton("GOOGLEPLUS");
        _googlePlusButton.setPreferredSize(new Dimension(19, 16));
        initSocialButton(_googlePlusButton, I18n.tr("Circle FrostWire on G+"), Constants.GPLUS_FROSTWIRE_URL);
    }

    private void initSocialButton(IconButton socialButton, String toolTipText, final String url) {
        socialButton.setToolTipText(I18n.tr(toolTipText));
        socialButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
                GUIMediator.openURL(url);
            }
        });
    }

    private void createSeedingStatusLabel() {

        seedingStatusButton = new IconButton("", "SEEDING", true) {
            private static final long serialVersionUID = -8985154093868645203L;

            @Override
            public String getToolTipText() {
                boolean seedingStatus = SharingSettings.SEED_FINISHED_TORRENTS.getValue();

                return "<html>"+(seedingStatus ? I18n.tr("<b>Seeding</b><p>completed torrent downloads.</p>") : I18n.tr("<b>Not Seeding</b><p>File chunks might be shared only during a torrent download.</p>")+"</html>");
            }
        };

        seedingStatusButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                GUIMediator.instance().setOptionsVisible(true, OptionsConstructor.BITTORRENT_BASIC_KEY);
            }
        });

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

            // substract donation buttons as needed2
            if (_donationButtons != null) {
                remainingWidth -= _donationButtons.getWidth();
                remainingWidth -= GUIConstants.SEPARATOR;
            }

            //  subtract center component
            int indicatorWidth = _centerComponent.getWidth();
            remainingWidth -= indicatorWidth;

            //  add components to panel, if room
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(0, 0, 0, 0);
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.gridx = GridBagConstraints.RELATIVE;

            //  add connection quality indicator if there's room
            if (StatusBarSettings.CONNECTION_QUALITY_DISPLAY_ENABLED.getValue()) {
                remainingWidth = addStatusIndicator(_connectionQualityMeter, sepWidth, remainingWidth, gbc);
            }

            if (StatusBarSettings.VPN_DISPLAY_ENABLED.getValue()) {
                _vpnStatus.refresh();
                remainingWidth = addStatusIndicator(_vpnStatus, sepWidth, remainingWidth, gbc);
            }

            //  add the language button if there's room
            if (getLanguageSetting().getValue() && remainingWidth > indicatorWidth) {
                remainingWidth = addStatusIndicator(_languageButton, sepWidth, remainingWidth, gbc);
            }

            //  then add firewall display if there's room
            if (StatusBarSettings.FIREWALL_DISPLAY_ENABLED.getValue()) {
                remainingWidth = addStatusIndicator(_firewallStatus, sepWidth, remainingWidth, gbc);
                updateFirewall();
            }

            //  add bandwidth display if there's room
            indicatorWidth = GUIConstants.SEPARATOR + GUIConstants.SEPARATOR / 2 + sepWidth + Math.max((int) _bandwidthUsageDown.getMinimumSize().getWidth(), _bandwidthUsageDown.getWidth())
                    + Math.max((int) _bandwidthUsageUp.getMinimumSize().getWidth(), _bandwidthUsageUp.getWidth());
            if (StatusBarSettings.BANDWIDTH_DISPLAY_ENABLED.getValue() && remainingWidth > indicatorWidth) {
                BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR / 2), gbc);
                BAR.add(_bandwidthUsageDown, gbc);
                BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR), gbc);
                BAR.add(_bandwidthUsageUp, gbc);
                BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR / 2), gbc);
                BAR.add(createSeparator(), gbc);
                //remainingWidth -= indicatorWidth;
            }

            gbc = new GridBagConstraints();
            gbc.gridx = GridBagConstraints.RELATIVE;
            BAR.add(seedingStatusButton, gbc);
            BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR / 2), gbc);
            BAR.add(createSeparator(), gbc);
            updateSeedingStatus();

            gbc = new GridBagConstraints();
            gbc.gridx = GridBagConstraints.RELATIVE;
            BAR.add(_facebookButton, gbc);
            BAR.add(_twitterButton, gbc);
            BAR.add(_googlePlusButton, gbc);

            BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR / 2), gbc);
            //  make center panel stretchy
            gbc.weightx = 1;
            BAR.add(_centerPanel, gbc);
            gbc.weightx = 0;
            BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR / 2), gbc);

            // donation buttons
            if (_donationButtons != null && StatusBarSettings.DONATION_BUTTONS_DISPLAY_ENABLED.getValue()) {
                BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR / 2), gbc);
                BAR.add(_donationButtons, gbc);
                BAR.add(Box.createHorizontalStrut(10));
                BAR.add(Box.createHorizontalStrut(GUIConstants.SEPARATOR), gbc);
            }

            try {
                //some macosx versions are throwing a deep NPE when this is invoked all the way down at 
                //sun.lwawt.macosx.LWCToolkit.getScreenResolution(Unknown Source)
                BAR.validate();
            } catch (Throwable ignored) {}
            
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
     * Sets up _connectionQualityMeter's icons.
     */
    private void createConnectionQualityPanel() {
        updateTheme(); // loads images
        _connectionQualityMeter = new JLabel();
        _connectionQualityMeter.setOpaque(false);
        _connectionQualityMeter.setMinimumSize(new Dimension(34, 20));
        _connectionQualityMeter.setMaximumSize(new Dimension(90, 30));
        //   add right-click listener
        _connectionQualityMeter.addMouseListener(STATUS_BAR_LISTENER);
    }

    /**
     * Sets up the 'Language' button
     */
    private void createLanguageButton() {
        _languageButton = new LanguageButton();
        _languageButton.addMouseListener(STATUS_BAR_LISTENER);
        updateLanguage();
    }

    /**
     * Sets up the 'Firewall Status' label.
     */
    private void createFirewallLabel() {
        _firewallStatus = new JLabel();

        updateFirewall();

        // don't allow easy clipping
        _firewallStatus.setMinimumSize(new Dimension(20, 20));
        // add right-click listener
        _firewallStatus.addMouseListener(STATUS_BAR_LISTENER);
    }

    /**
        _lwsStatus = new JLabel();
     * Sets up the 'Bandwidth Usage' label.
     */
    private void createBandwidthLabel() {
        _bandwidthUsageDown = new LazyTooltip(GUIMediator.getThemeImage("downloading_small"));
        _bandwidthUsageUp = new LazyTooltip(GUIMediator.getThemeImage("uploading_small"));
        //updateBandwidth();
        // don't allow easy clipping
        _bandwidthUsageDown.setMinimumSize(new Dimension(60, 20));
        _bandwidthUsageUp.setMinimumSize(new Dimension(60, 20));
        // add right-click listeners
        _bandwidthUsageDown.addMouseListener(STATUS_BAR_LISTENER);
        _bandwidthUsageUp.addMouseListener(STATUS_BAR_LISTENER);
    }

    /**
     * Sets up the center panel.
     */
    private void createCenterPanel() {
        STATUS_COMPONENT = new StatusComponent();
        _centerComponent = new JLabel();
        _centerPanel = new JPanel(new GridBagLayout());

        _centerPanel.setOpaque(false);
        STATUS_COMPONENT.setProgressPreferredSize(new Dimension(250, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        _centerPanel.add(STATUS_COMPONENT, gbc);

        //  add right-click listeners
        _centerPanel.addMouseListener(STATUS_BAR_LISTENER);
        STATUS_COMPONENT.addMouseListener(STATUS_BAR_LISTENER);
    }

    /**
     * Updates the center panel if non-PRO.  Periodically rotates between
     * the update panel and the status link handler. 
     */
    private void updateCenterPanel() {
        long now = System.currentTimeMillis();
        if (_nextUpdateTime > now)
            return;

        _nextUpdateTime = now + 1000 * 5; // update every minute
        _centerPanel.removeAll();
        _centerComponent = new JLabel();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        _centerPanel.add(_centerComponent, gbc);

        refresh();
    }

    private void updateSeedingStatus() {
        boolean seedingStatus = SharingSettings.SEED_FINISHED_TORRENTS.getValue();
        seedingStatusButton.setText("<html>"+(seedingStatus ? I18n.tr("<b>Seeding</b>") : I18n.tr("<b>Not Seeding</b>"))+"</html>");
        seedingStatusButton.setIcon(seedingStatus ? GUIMediator.getThemeImage("seeding_small") : GUIMediator.getThemeImage("not_seeding_small"));
    }

    private long _nextUpdateTime = System.currentTimeMillis();

    /**
     * Updates the status text.
     */
    public void setStatusText(final String text) {
        GUIMediator.safeInvokeAndWait(new Runnable() {
            public void run() {
                STATUS_COMPONENT.setText(text);
            }
        });
    }

    /**
     * Updates the firewall text. 
     */
    public void updateFirewallLabel(boolean notFirewalled) {
        if (notFirewalled) {
            _firewallStatus.setIcon(GUIMediator.getThemeImage("firewall_no"));
            _firewallStatus.setToolTipText(I18n.tr("FrostWire has not detected a firewall"));
        } else {
            _firewallStatus.setIcon(GUIMediator.getThemeImage("firewall"));
            _firewallStatus.setToolTipText(I18n.tr("FrostWire has detected a firewall"));
        }
    }

    /**
     * Updates the image on the flag
     */
    public void updateLanguage() {
        _languageButton.updateLanguageFlag();
    }

    /**
     * Updates the firewall text. 
     */
    public void updateFirewall() {
        try {
            BTEngine engine = BTEngine.getInstance();
            updateFirewallLabel(!engine.isFirewalled());
        } catch (Throwable ignored) {
        }
    }

    /**
     * Updates the bandwidth statistics.
     */
    public void updateBandwidth() {
        try {
            //  format strings
            String sDown = GUIUtils.rate2speed(GUIMediator.instance().getBTDownloadMediator().getDownloadsBandwidth());
            String sUp = GUIUtils.rate2speed(GUIMediator.instance().getBTDownloadMediator().getUploadsBandwidth());

            // number of uploads (seeding) and downloads
            int downloads = GUIMediator.instance().getCurrentDownloads();
            int uploads = GUIMediator.instance().getCurrentUploads();

            _bandwidthUsageDown.setText(downloads + " @ " + sDown);
            _bandwidthUsageUp.setText(uploads + " @ " + sUp);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Notification that loading has finished.
     *
     * The loading label is removed and the update notification
     * component is added.  If necessary, the center panel will
     * rotate back and forth between displaying the update
     * notification and displaying the StatusLinkHandler.
     */
    void loadFinished() {
        updateCenterPanel();
        _centerPanel.revalidate();
        _centerPanel.repaint();
        refresh();
    }

    /**
     * Load connection quality theme icons
     */
    public void updateTheme() {
        _connectionQualityMeterIcons[StatusLine.STATUS_DISCONNECTED] = GUIMediator.getThemeImage("connect_small_0");
        _connectionQualityMeterIcons[StatusLine.STATUS_TURBOCHARGED] = GUIMediator.getThemeImage("connect_small_6");
    }

    /**
     * Alters the displayed connection quality.
     */
    public void setConnectionQuality(int quality) {
        // make sure we don't go over our bounds.
        if (quality >= _connectionQualityMeterIcons.length) {
            quality = _connectionQualityMeterIcons.length - 1;
        }

        _connectionQualityMeter.setIcon(_connectionQualityMeterIcons[quality]);

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

        updateTotalDHTNodesInTooltip(tip);
        _connectionQualityMeter.setText(status);
    }

    /**
     *  Adds a DhtStatsAlert listener to the session, has the session post the DHTStatsAlert
     *  when it receives it, it removes the listener, calculates the node count from all
     *  the routing buckets, then calls onTotalNodes(int) which safely updates
     *  the tooltip of the connection quality meter on the UI thread.
     */
    private void updateTotalDHTNodesInTooltip(String tip) {
        if (System.currentTimeMillis() - _lastOnTotalNodesUpdate < 5000) {
            return;
        }

        try {
            BTEngine engine = BTEngine.getInstance();
            final Session session = engine.getSession();
            if (session != null && session.isDHTRunning()) {
                session.postDHTStats();
                int totalDHTNodes = engine.getTotalDHTNodes();
                if (totalDHTNodes != -1) {
                    final String updatedToolTip = tip + ". (DHT: " + totalDHTNodes + " " + I18n.tr("nodes") + ")";
                    GUIMediator.safeInvokeAndWait(new Runnable() {
                        @Override
                        public void run() {
                            _lastOnTotalNodesUpdate = System.currentTimeMillis();
                            _connectionQualityMeter.setToolTipText(updatedToolTip);
                        }
                    });
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
      * Accessor for the <tt>JComponent</tt> instance that contains all
      * of the panels for the status line.
      *
      * @return the <tt>JComponent</tt> instance that contains all
      *  of the panels for the status line
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

        public void processMouseEvent(MouseEvent me) {
            if (me.isPopupTrigger()) {
                JPopupMenu jpm = new SkinPopupMenu();

                //  add 'Show Connection Quality' menu item
                JCheckBoxMenuItem jcbmi = new SkinCheckBoxMenuItem(new ShowConnectionQualityAction());
                jcbmi.setState(StatusBarSettings.CONNECTION_QUALITY_DISPLAY_ENABLED.getValue());
                jpm.add(jcbmi);

                jcbmi = new SkinCheckBoxMenuItem(new ShowVPNAction());
                jcbmi.setState(StatusBarSettings.VPN_DISPLAY_ENABLED.getValue());
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

                //  add 'Show Donation Buttons' menu item
                jcbmi = new SkinCheckBoxMenuItem(new ShowDonationButtonsAction());
                jcbmi.setState(StatusBarSettings.DONATION_BUTTONS_DISPLAY_ENABLED.getValue());
                jpm.add(jcbmi);

                jpm.pack();
                jpm.show(me.getComponent(), me.getX(), me.getY());
            }
        }
    };

    /**
     * Action for the 'Show Connection Quality' menu item.
     */
    private class ShowConnectionQualityAction extends AbstractAction {

        /**
         *
         */
        private static final long serialVersionUID = 7922422377962473634L;

        public ShowConnectionQualityAction() {
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

        public ShowVPNAction() {
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

        /**
         * 
         */
        private static final long serialVersionUID = 726208491122581283L;

        public ShowLanguageStatusAction() {
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

        /**
         * 
         */
        private static final long serialVersionUID = -8489901794229005217L;

        public ShowFirewallStatusAction() {
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

        /**
         * 
         */
        private static final long serialVersionUID = 1455679943975682049L;

        public ShowBandwidthConsumptionAction() {
            putValue(Action.NAME, I18n.tr("Show Bandwidth Consumption"));
        }

        public void actionPerformed(ActionEvent e) {
            StatusBarSettings.BANDWIDTH_DISPLAY_ENABLED.invert();
            refresh();
        }
    }

    private class ShowDonationButtonsAction extends AbstractAction {

        /**
         * 
         */
        private static final long serialVersionUID = 1455679943975682049L;

        public ShowDonationButtonsAction() {
            putValue(Action.NAME, I18n.tr("Show Donation Buttons"));
        }

        public void actionPerformed(ActionEvent e) {
            StatusBarSettings.DONATION_BUTTONS_DISPLAY_ENABLED.invert();
            refresh();
        }
    }

    private class LazyTooltip extends JLabel {

        /**
         * 
         */
        private static final long serialVersionUID = -5759748801999410032L;

        LazyTooltip(ImageIcon icon) {
            super(icon);
            ToolTipManager.sharedInstance().registerComponent(this);
        }

        @Override
        public String getToolTipText() {
            BTDownloadMediator btDownloadMediator = GUIMediator.instance().getBTDownloadMediator();

            String sDown = GUIUtils.rate2speed(btDownloadMediator.getDownloadsBandwidth());
            String sUp = GUIUtils.rate2speed(btDownloadMediator.getUploadsBandwidth());

            String totalDown = GUIUtils.toUnitbytes(btDownloadMediator.getTotalBytesDownloaded());
            String totalUp = GUIUtils.toUnitbytes(btDownloadMediator.getTotalBytesUploaded());
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
