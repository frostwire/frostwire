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

package com.frostwire.gui.bittorrent;

import com.frostwire.bittorrent.CopyrightLicenseBroker;
import com.frostwire.bittorrent.CopyrightLicenseBroker.LicenseCategory;
import com.frostwire.gui.bittorrent.LicenseToggleButton.LicenseIcon;
import com.frostwire.licenses.License;
import com.frostwire.licenses.Licenses;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LimeTextField;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.List;

@SuppressWarnings("serial")
class CopyrightLicenseSelectorPanel extends JPanel {
    private static final String CREATIVE_COMMONS_CARD_NAME = I18n.tr("Creative Commons");
    private static final String OPEN_SOURCE_CARD_NAME = I18n.tr("Open Source");
    private static final String PUBLIC_DOMAIN_CARD_NAME = I18n.tr("Public Domain");
    private final JCheckBox confirmRightfulUseOfLicense;
    private final JLabel authorsNameLabel;
    private final LimeTextField authorsName;
    private final JLabel titleLabel;
    private final LimeTextField title;
    private final JLabel attributionUrlLabel;
    private final LimeTextField attributionUrl;
    private final JPanel licenseTypesCardLayoutContainer;
    private final JRadioButton licenseTypeCC;
    private final JRadioButton licenseTypeOpenSource;
    private final JRadioButton licenseTypePublicDomain;
    //CC License modifiers.
    private final LicenseToggleButton ccButton;
    private final LicenseToggleButton byButton;
    private final LicenseToggleButton ncButton;
    private final LicenseToggleButton ndButton;
    private final LicenseToggleButton saButton;
    //OpenSource License buttons
    private final LicenseToggleButton apacheButton;
    private final LicenseToggleButton bsd2ClauseButton;
    private final LicenseToggleButton bsd3ClauseButton;
    private final LicenseToggleButton gpl3Button;
    private final LicenseToggleButton lgplButton;
    private final LicenseToggleButton mitButton;
    private final LicenseToggleButton mozillaButton;
    private final LicenseToggleButton cddlButton;
    private final LicenseToggleButton eclipseButton;
    private final List<LicenseToggleButton> openSourceLicenseButtons;
    //Public Domain License Buttons
    private final LicenseToggleButton cc0Button;
    private final LicenseToggleButton publicDomainButton;
    private final JButton pickedLicenseLabel;
    private CopyrightLicenseBroker licenseBroker;

    CopyrightLicenseSelectorPanel() {
        setLayout(new MigLayout("fill"));
        GUIUtils.setTitledBorderOnPanel(this, I18n.tr("Choose a Copyright License for this work"));
        confirmRightfulUseOfLicense = new JCheckBox("<html><strong>"
                + I18n.tr("I am the Content Creator of this work or I have been granted the rights to share this content under the following license by the Content Creator(s).") + "</strong></html>");
        authorsNameLabel = new JLabel("<html>" + I18n.tr("Author's Name") + "</html>");
        authorsName = new LimeTextField();
        authorsName.setToolTipText(I18n.tr("The name of the creator or creators of this work."));
        titleLabel = new JLabel("<html>" + I18n.tr("Work's Title") + "</html>");
        title = new LimeTextField();
        title.setToolTipText(I18n.tr("The name of this work, i.e. the titleLabel of a music album, the titleLabel of a book, the titleLabel of a movie, etc."));
        title.setPrompt(I18n.tr("album name, movie title, book title, game title."));
        attributionUrlLabel = new JLabel("<html>" + I18n.tr("Attribution URL") + "</html>");
        attributionUrl = new LimeTextField();
        attributionUrl.setToolTipText(I18n.tr("The Content Creator's website to give attribution about this work if shared by others."));
        attributionUrl.setPrompt("http://www.contentcreator.com/website/here");
        licenseTypesCardLayoutContainer = new JPanel(new CardLayout());
        licenseTypeCC = new JRadioButton(I18n.tr(CREATIVE_COMMONS_CARD_NAME));
        licenseTypeOpenSource = new JRadioButton(I18n.tr(OPEN_SOURCE_CARD_NAME));
        licenseTypePublicDomain = new JRadioButton(I18n.tr(PUBLIC_DOMAIN_CARD_NAME));
        ccButton = new LicenseToggleButton(
                LicenseToggleButton.LicenseIcon.CC,
                CREATIVE_COMMONS_CARD_NAME,
                I18n.tr("Offering your work under a Creative Commons license does not mean giving up your copyright. It means offering some of your rights to any member of the public but only under certain conditions."), true, false);
        byButton = new LicenseToggleButton(LicenseIcon.BY, "Attribution",
                I18n.tr("You let others copy, distribute, display, and perform your copyrighted work but only if they give credit the way you request."), true, false);
        ncButton = new LicenseToggleButton(LicenseIcon.NC, "NonCommercial",
                I18n.tr("<strong>No commercial use allowed.</strong><br>You let others copy, distribute, display, and perform your work &mdash; and derivative works based upon it &mdash; but for noncommercial purposes only."), true, true);
        ndButton = new LicenseToggleButton(LicenseIcon.ND, "NoDerivatives",
                I18n.tr("<strong>No remixing allowed.</strong><br>You let others copy, distribute, display, and perform only verbatim copies of your work, not derivative works based upon it."), false, true);
        saButton = new LicenseToggleButton(LicenseIcon.SA, "Share-Alike",
                I18n.tr("You allow others to distribute derivative works only under a license identical to the license that governs your work."), true, true);
        apacheButton = new LicenseToggleButton(LicenseIcon.APACHE, "Apache 2.0", "Apache License 2.0", false, true);
        bsd2ClauseButton = new LicenseToggleButton(LicenseIcon.BSD, "BSD 2-Clause", "BSD 2-Clause \"Simplified\" or \"FreeBSD\" license.", false, true);
        bsd3ClauseButton = new LicenseToggleButton(LicenseIcon.BSD, "BSD 3-Clause", "BSD 3-Clause \"New\" or \"Revised\" license.", false, true);
        gpl3Button = new LicenseToggleButton(LicenseIcon.GPL3, "GPLv3", "GNU General Public License (GPL) version 3", false, true);
        lgplButton = new LicenseToggleButton(LicenseIcon.LGPL3, "LGPL", "GNU Library or \"Lesser\" General Public License (LGPL)", false, true);
        mozillaButton = new LicenseToggleButton(LicenseIcon.MOZILLA, "Mozilla 2.0", "Mozilla Public License 2.0", false, true);
        mitButton = new LicenseToggleButton(LicenseIcon.OPENSOURCE, "MIT", "MIT license", false, true);
        cddlButton = new LicenseToggleButton(LicenseIcon.OPENSOURCE, "CDDL-1.0", "Common Development and Distribution License (CDDL-1.0)", false, true);
        eclipseButton = new LicenseToggleButton(LicenseIcon.OPENSOURCE, "EPL-1.0", "Eclipse Public License, Vesion 1.0 (EPL-1.0)", false, true);
        openSourceLicenseButtons = new LinkedList<>();
        initOpenSourceButtonList();
        publicDomainButton = new LicenseToggleButton(LicenseIcon.PUBLICDOMAIN, "Public Domain Mark 1.0", I18n.tr("This work has been identified as being free of known restrictions under copyright law, including all related and neighboring rights."), true, true);
        cc0Button = new LicenseToggleButton(LicenseIcon.CC0, "CC0 1.0", I18n.tr("The person who associated a work with this deed has dedicated the work to the public domain by waiving all of his or her rights to the work worldwide under copyright law, including all related and neighboring rights, to the extent allowed by law."), false, true);
        pickedLicenseLabel = new JButton();
        initListeners();
        initComponents();
    }

    CopyrightLicenseBroker getLicenseBroker() {
        return licenseBroker;
    }

    boolean hasConfirmedRightfulUseOfLicense() {
        return confirmRightfulUseOfLicense.isSelected();
    }

    private void initOpenSourceButtonList() {
        openSourceLicenseButtons.add(apacheButton);
        openSourceLicenseButtons.add(bsd2ClauseButton);
        openSourceLicenseButtons.add(bsd3ClauseButton);
        openSourceLicenseButtons.add(gpl3Button);
        openSourceLicenseButtons.add(lgplButton);
        openSourceLicenseButtons.add(mozillaButton);
        openSourceLicenseButtons.add(mitButton);
        openSourceLicenseButtons.add(cddlButton);
        openSourceLicenseButtons.add(eclipseButton);
    }

    private void onCreativeCommonsButtonToggled(LicenseToggleButton button) {
        if (confirmRightfulUseOfLicense.isSelected()) {
            if (button.getLicenseIcon() == LicenseIcon.ND && button.isSelected()) {
                saButton.setSelected(false);
            } else if (button.getLicenseIcon() == LicenseIcon.SA && button.isSelected()) {
                ndButton.setSelected(false);
            }
            updatePickedLicenseLabel();
        }
    }

    private void onOpenSourceButtonToggled(LicenseToggleButton button) {
        for (LicenseToggleButton b : openSourceLicenseButtons) {
            if (b != button) {
                b.setSelected(false);
            }
        }
        button.setSelected(true);
        updatePickedLicenseLabel();
    }

    private void onPublicDomainButtonToggled(LicenseToggleButton button) {
        if (button == cc0Button) {
            publicDomainButton.setSelected(false);
        } else {
            cc0Button.setSelected(false);
        }
        button.setSelected(true);
        updatePickedLicenseLabel();
    }

    private void updatePickedLicenseLabel() {
        if (licenseTypeCC.isSelected()) {
            updateCreativeCommonsPickedLicenseLabel();
        } else if (licenseTypeOpenSource.isSelected()) {
            updateOpenSourcePickedLicenseLabel();
        } else if (licenseTypePublicDomain.isSelected()) {
            updatePublicDomainPickedLicenseLabel();
        }
    }

    private void updateCreativeCommonsPickedLicenseLabel() {
        updateLicenseBrokerWithCreativeCommonsLicense();
        updateLicenseLabel();
    }

    private void updateLicenseBroker(License license, CopyrightLicenseBroker.LicenseCategory category) {
        if (license != null) {
            licenseBroker = new CopyrightLicenseBroker(category,
                    license,
                    title.getText(),
                    authorsName.getText(),
                    attributionUrl.getText());
        }
    }

    private void updateLicenseLabel() {
        if (licenseBroker != null) {
            pickedLicenseLabel.setText("<html>" + I18n.tr("You have selected the following License") + ":<br> <a href=\"" + licenseBroker.license.getUrl() + "\">" + licenseBroker.getLicenseName()
                    + "</a>");
            ActionListener[] actionListeners = pickedLicenseLabel.getActionListeners();
            if (actionListeners != null) {
                for (ActionListener listener : actionListeners) {
                    pickedLicenseLabel.removeActionListener(listener);
                }
            }
            pickedLicenseLabel.addActionListener(e -> GUIMediator.openURL(licenseBroker.license.getUrl()));
        } else {
            pickedLicenseLabel.setText("");
        }
    }

    private void updateOpenSourcePickedLicenseLabel() {
        licenseBroker = null;
        License license = null;
        if (apacheButton.isSelected()) {
            license = Licenses.APACHE;
        } else if (bsd2ClauseButton.isSelected()) {
            license = Licenses.BSD_2_CLAUSE;
        } else if (bsd3ClauseButton.isSelected()) {
            license = Licenses.BSD_3_CLAUSE;
        } else if (gpl3Button.isSelected()) {
            license = Licenses.GPL3;
        } else if (lgplButton.isSelected()) {
            license = Licenses.LGPL;
        } else if (mitButton.isSelected()) {
            license = Licenses.MIT;
        } else if (mozillaButton.isSelected()) {
            license = Licenses.MOZILLA;
        } else if (cddlButton.isSelected()) {
            license = Licenses.CDDL;
        } else if (eclipseButton.isSelected()) {
            license = Licenses.ECLIPSE;
        }
        updateLicenseBroker(license, LicenseCategory.OpenSource);
        updateLicenseLabel();
    }

    private void updatePublicDomainPickedLicenseLabel() {
        licenseBroker = null;
        License license = Licenses.PUBLIC_DOMAIN_MARK;
        if (publicDomainButton.isSelected()) {
            license = Licenses.PUBLIC_DOMAIN_MARK;
        } else if (cc0Button.isSelected()) {
            license = Licenses.PUBLIC_DOMAIN_CC0;
        }
        updateLicenseBroker(license, LicenseCategory.PublicDomain);
        updateLicenseLabel();
    }

    private void updateLicenseBrokerWithCreativeCommonsLicense() {
        licenseBroker = null;
        if (hasConfirmedRightfulUseOfLicense() && licenseTypeCC.isSelected()) {
            licenseBroker = new CopyrightLicenseBroker(saButton.isSelected(), ncButton.isSelected(), ndButton.isSelected(), title.getText(), authorsName.getText(), attributionUrl.getText());
        }
    }

    private void initListeners() {
        confirmRightfulUseOfLicense.addActionListener(e -> onConfirmRightfulUseOfLicenseAction());
        initLicenseTypeRadioButtonsListener();
        initCreativeCommonsLicenseToggleListeners();
        initOpenSourceLicensesToggleListeners();
        initPublicDomainToggleListeners();
    }

    private void initOpenSourceLicensesToggleListeners() {
        LicenseToggleButtonOnToggleListener openSourceToggleListener = this::onOpenSourceButtonToggled;
        apacheButton.setOnToggleListener(openSourceToggleListener);
        bsd2ClauseButton.setOnToggleListener(openSourceToggleListener);
        bsd3ClauseButton.setOnToggleListener(openSourceToggleListener);
        cddlButton.setOnToggleListener(openSourceToggleListener);
        eclipseButton.setOnToggleListener(openSourceToggleListener);
        gpl3Button.setOnToggleListener(openSourceToggleListener);
        lgplButton.setOnToggleListener(openSourceToggleListener);
        mitButton.setOnToggleListener(openSourceToggleListener);
        mozillaButton.setOnToggleListener(openSourceToggleListener);
        updatePickedLicenseLabel();
    }

    private void initCreativeCommonsLicenseToggleListeners() {
        LicenseToggleButtonOnToggleListener ccToggleListener = this::onCreativeCommonsButtonToggled;
        ncButton.setOnToggleListener(ccToggleListener);
        ndButton.setOnToggleListener(ccToggleListener);
        saButton.setOnToggleListener(ccToggleListener);
    }

    private void initPublicDomainToggleListeners() {
        LicenseToggleButtonOnToggleListener pdToggleListener = this::onPublicDomainButtonToggled;
        cc0Button.setOnToggleListener(pdToggleListener);
        publicDomainButton.setOnToggleListener(pdToggleListener);
    }

    private void initLicenseTypeRadioButtonsListener() {
        ActionListener licenseTypeChangeListener = e -> onLicenseTypeChanged();
        licenseTypeCC.addActionListener(licenseTypeChangeListener);
        licenseTypeOpenSource.addActionListener(licenseTypeChangeListener);
        licenseTypePublicDomain.addActionListener(licenseTypeChangeListener);
    }

    private void initComponents() {
        initCommonComponents();
        initCreativeCommonsLicensePanel();
        initOpenSourceLicensesPanel();
        initPublicDomainLicensePanel();
        add(licenseTypesCardLayoutContainer, "aligny top, span 2, grow, pushy, gapbottom 5px, wrap");
        pickedLicenseLabel.setHorizontalAlignment(SwingConstants.LEFT);
        pickedLicenseLabel.setBorderPainted(false);
        pickedLicenseLabel.setOpaque(false);
        pickedLicenseLabel.setContentAreaFilled(false);
        pickedLicenseLabel.setFocusPainted(false);
        add(pickedLicenseLabel, "alignx center, growx, span 2, pushx");
    }

    private void initCreativeCommonsLicensePanel() {
        JPanel licenseButtonsPanel = new JPanel(new MigLayout("fill, insets 0 0 0 0"));
        JLabel label = new JLabel("<html>" + I18n.tr("Select what people can and can't do with this work") + "</html>");
        label.setEnabled(false);
        licenseButtonsPanel.add(label, "span 5, alignx center, pushy, aligny bottom, wrap");
        licenseButtonsPanel.add(ccButton, "wmin 130px, aligny top, pushy, grow, gap 2 2 2 2");
        licenseButtonsPanel.add(byButton, "wmin 130px, aligny top, pushy, grow, gap 2 2 2 2");
        licenseButtonsPanel.add(ncButton, "wmin 130px, aligny top, pushy, grow, gap 2 2 2 2");
        licenseButtonsPanel.add(ndButton, "wmin 130px, aligny top, pushy, grow, gap 2 2 2 2");
        licenseButtonsPanel.add(saButton, "wmin 130px, aligny top, pushy, grow, gap 2 2 2 2, wrap");
        licenseTypesCardLayoutContainer.add(licenseButtonsPanel, CREATIVE_COMMONS_CARD_NAME);
    }

    private void initOpenSourceLicensesPanel() {
        JPanel licenseButtonsPanel = new JPanel(new MigLayout("fill, insets 0 0 0 0"));
        licenseButtonsPanel.add(apacheButton, "wmin 130px, aligny top, pushy, grow, gap 2 2 2 2");
        licenseButtonsPanel.add(bsd3ClauseButton, "wmin 130px, aligny top, pushy, grow, gap 2 2 2 2");
        licenseButtonsPanel.add(bsd2ClauseButton, "wmin 130px, aligny top, pushy, grow, gap 2 2 2 2, wrap");
        licenseButtonsPanel.add(gpl3Button, "wmin 130px, aligny top, pushy, grow, gap 2 2 2 2");
        licenseButtonsPanel.add(lgplButton, "wmin 130px, aligny top, pushy, grow, gap 2 2 2 2");
        licenseButtonsPanel.add(mitButton, "wmin 130px, aligny top, pushy, grow, gap 2 2 2 2, wrap");
        licenseButtonsPanel.add(mozillaButton, "wmin 130px, aligny top, pushy, grow, gap 2 2 2 2");
        licenseButtonsPanel.add(cddlButton, "wmin 130px, aligny top, pushy, grow, gap 2 2 2 2");
        licenseButtonsPanel.add(eclipseButton, "wmin 130px, aligny top, pushy, grow, gap 2 2 2 2");
        JScrollPane scrollPane = new JScrollPane(licenseButtonsPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        licenseTypesCardLayoutContainer.add(scrollPane, OPEN_SOURCE_CARD_NAME);
    }

    private void initPublicDomainLicensePanel() {
        JPanel publicDomainLicensePanel = new JPanel(new MigLayout("fill, insets 0 0 0 0, alignx center"));
        JLabel label = new JLabel(I18n.tr("You can copy, modify, distribute and perform the work, even for commercial purposes, all without asking permission."));
        label.setEnabled(false);
        publicDomainLicensePanel.add(label, "gaptop 10px, aligny center, push, alignx center, span 2, wrap");
        publicDomainLicensePanel.add(publicDomainButton, "wmin 400px, aligny top, push, grow, gap 2 2 2 2");
        publicDomainLicensePanel.add(cc0Button, "wmin 400px, aligny top, push, grow, gap 2 2 2 2");
        licenseTypesCardLayoutContainer.add(publicDomainLicensePanel, PUBLIC_DOMAIN_CARD_NAME);
    }

    private void initCommonComponents() {
        confirmRightfulUseOfLicense.setSelected(false);
        add(confirmRightfulUseOfLicense, "growx, north, gapbottom 8, wrap");
        confirmRightfulUseOfLicense.setSelected(false);
        onConfirmRightfulUseOfLicenseAction();
        add(authorsNameLabel, "gapbottom 5px, pushx, wmin 215px");
        add(titleLabel, "gapbottom 5px, wmin 215px, pushx, wrap");
        add(authorsName, "gapbottom 5px, growx 50, aligny top, pushy, wmin 215px, height 30px, span 1");
        add(title, "gapbottom 5px, growx 50, aligny top, pushy, wmin 215px, span 1, height 30px, wrap");
        JPanel attribPanel = new JPanel(new MigLayout("fillx, insets 0 0 0 0"));
        attribPanel.add(attributionUrlLabel, "width 110px!, alignx left");
        attribPanel.add(attributionUrl, "alignx left, growx, pushx");
        add(attribPanel, "aligny top, pushy, growx, gapbottom 10px, span 2, wrap");
        JPanel licenseRadioButtonsContainer = new JPanel(new MigLayout("fillx, insets 0 0 0 0"));
        ButtonGroup group = new ButtonGroup();
        licenseTypeCC.setSelected(true);
        licenseTypeOpenSource.setSelected(false);
        licenseTypePublicDomain.setSelected(false);
        licenseTypeCC.setEnabled(false);
        licenseTypeOpenSource.setEnabled(false);
        licenseTypePublicDomain.setEnabled(false);
        group.add(licenseTypeCC);
        group.add(licenseTypeOpenSource);
        group.add(licenseTypePublicDomain);
        licenseRadioButtonsContainer.add(licenseTypeCC);
        licenseRadioButtonsContainer.add(licenseTypeOpenSource);
        licenseRadioButtonsContainer.add(licenseTypePublicDomain);
        add(new JLabel(I18n.tr("License type:")));
        add(licenseRadioButtonsContainer, "growx, span 2, wrap");
    }

    private void onLicenseTypeChanged() {
        if (confirmRightfulUseOfLicense.isSelected()) {
            System.out.println("onLicenseTypeChanged()");
            CardLayout deck = (CardLayout) licenseTypesCardLayoutContainer.getLayout();
            String currentPanelName = null;
            if (licenseTypeCC.isSelected()) {
                currentPanelName = CREATIVE_COMMONS_CARD_NAME;
            } else if (licenseTypeOpenSource.isSelected()) {
                currentPanelName = OPEN_SOURCE_CARD_NAME;
            } else if (licenseTypePublicDomain.isSelected()) {
                currentPanelName = PUBLIC_DOMAIN_CARD_NAME;
            }
            deck.show(licenseTypesCardLayoutContainer, currentPanelName);
            updatePickedLicenseLabel();
        }
    }

    private void onConfirmRightfulUseOfLicenseAction() {
        boolean rightfulUseConfirmed = hasConfirmedRightfulUseOfLicense();
        authorsNameLabel.setEnabled(rightfulUseConfirmed);
        authorsName.setEnabled(rightfulUseConfirmed);
        titleLabel.setEnabled(rightfulUseConfirmed);
        title.setEnabled(rightfulUseConfirmed);
        attributionUrlLabel.setEnabled(rightfulUseConfirmed);
        attributionUrl.setEnabled(rightfulUseConfirmed);
        ccButton.setSelected(rightfulUseConfirmed);
        byButton.setSelected(rightfulUseConfirmed);
        ncButton.setSelected(rightfulUseConfirmed);
        ncButton.setToggleable(rightfulUseConfirmed);
        ndButton.setToggleable(rightfulUseConfirmed);
        saButton.setToggleable(rightfulUseConfirmed);
        licenseTypeCC.setEnabled(rightfulUseConfirmed);
        licenseTypeOpenSource.setEnabled(rightfulUseConfirmed);
        licenseTypePublicDomain.setEnabled(rightfulUseConfirmed);
        licenseTypesCardLayoutContainer.setEnabled(rightfulUseConfirmed);
        updateOpenSourceLicensesToggleability(rightfulUseConfirmed);
        publicDomainButton.setSelected(rightfulUseConfirmed);
        cc0Button.setSelected(rightfulUseConfirmed);
        publicDomainButton.setToggleable(rightfulUseConfirmed);
        cc0Button.setToggleable(rightfulUseConfirmed);
        pickedLicenseLabel.setVisible(rightfulUseConfirmed);
        if (rightfulUseConfirmed) {
            //reset
            apacheButton.setSelected(true);
            publicDomainButton.setSelected(true);
            cc0Button.setSelected(false);
            updatePickedLicenseLabel();
        } else {
            ndButton.setSelected(false);
            saButton.setSelected(false);
            publicDomainButton.setSelected(false);
            cc0Button.setSelected(false);
            for (LicenseToggleButton licenseToggleButton : openSourceLicenseButtons) {
                licenseToggleButton.setSelected(false);
            }
        }
    }

    private void updateOpenSourceLicensesToggleability(boolean toggleable) {
        for (LicenseToggleButton button : openSourceLicenseButtons) {
            button.setToggleable(toggleable);
        }
    }
}
