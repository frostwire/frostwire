/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.components.transfers;

import com.frostwire.gui.bittorrent.BittorrentDownload;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.RefreshListener;
import com.limegroup.gnutella.settings.UISettings;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;

/**
 * The component that holds the different panels to present
 * all the different torrent transfer details.
 */
public final class TransferDetailComponent extends JPanel implements RefreshListener {
    //private static final Logger LOG = Logger.getLogger(TransferDetailComponent.class);

    private JToggleButton filesButton;
    private JToggleButton piecesButton;
    private JToggleButton statusButton;
    private JToggleButton detailsButton;
    private JToggleButton trackersButton;
    private JToggleButton peersButton;

    private JPanel detailComponentHolder;

    // inner use strings, no need to translate, just to switch cards layout
    private final String FILES_CARD = "F";
    private final String PIECES_CARD = "P";
    private final String STATUS_CARD = "S";
    private final String DETAILS_CARD = "D";
    private final String TRACKERS_CARD = "T";
    private final String PEERS_CARD = "p";

    private HashMap<String, TransferDetailPanel> cardPanelMap;
    private TransferDetailPanel currentComponent;
    private BittorrentDownload selectedBittorrentDownload;

    interface TransferDetailPanel {
        void updateData(BittorrentDownload btDownload);
    }

    public TransferDetailComponent() {
        super(new MigLayout("fill, insets 0 0 0 0"));
        add(new JLabel(I18n.tr("Transfer Detail")), "left, gapleft 5px, growx");
        add(createDetailSwitcherButtons(), "right, wrap");
        add(createDetailComponentHolder(), "span 2, grow");
        GUIMediator.addRefreshListener(this);
    }

    private JPanel createDetailComponentHolder() {
        detailComponentHolder = new JPanel(new CardLayout());
        TransferDetailFiles filesComponent;
        detailComponentHolder.add(filesComponent = new TransferDetailFiles(), FILES_CARD);
        TransferDetailPieces piecesComponent;
        detailComponentHolder.add(piecesComponent = new TransferDetailPieces(), PIECES_CARD);
        TransferDetailStatus statusComponent;
        detailComponentHolder.add(statusComponent = new TransferDetailStatus(), STATUS_CARD);
        TransferDetailDetails detailsComponent;
        detailComponentHolder.add(detailsComponent = new TransferDetailDetails(), DETAILS_CARD);
        TransferDetailTrackers trackersComponent;
        detailComponentHolder.add(trackersComponent = new TransferDetailTrackers(), TRACKERS_CARD);
        TransferDetailPeers peersComponent;
        detailComponentHolder.add(peersComponent = new TransferDetailPeers(), PEERS_CARD);

        cardPanelMap = new HashMap<>();
        cardPanelMap.put(FILES_CARD, filesComponent);
        cardPanelMap.put(PIECES_CARD, piecesComponent);
        cardPanelMap.put(STATUS_CARD, statusComponent);
        cardPanelMap.put(DETAILS_CARD, detailsComponent);
        cardPanelMap.put(TRACKERS_CARD, trackersComponent);
        cardPanelMap.put(PEERS_CARD, peersComponent);

        HashMap<String, JToggleButton> cardButtonMap = new HashMap<>();
        cardButtonMap.put(FILES_CARD, filesButton);
        cardButtonMap.put(PIECES_CARD, piecesButton);
        cardButtonMap.put(STATUS_CARD, statusButton);
        cardButtonMap.put(DETAILS_CARD, detailsButton);
        cardButtonMap.put(TRACKERS_CARD, trackersButton);
        cardButtonMap.put(PEERS_CARD, peersButton);

        // Auto click on the last shown JPanel button selector
        String lastTransferSelected = UISettings.LAST_SELECTED_TRANSFER_DETAIL_JPANEL.getValue();
        if (cardButtonMap.containsKey(lastTransferSelected)) {
            cardButtonMap.get(lastTransferSelected).doClick();
        }
        return detailComponentHolder;
    }

    private JPanel createDetailSwitcherButtons() {
        JPanel detailSwitcherButtonsPanel = new JPanel(new MigLayout("align right, ins 0 0 0 8"));
        ButtonGroup switcherButtonsGroup = new ButtonGroup();
        filesButton = new JToggleButton(I18n.tr("Files"), true);
        piecesButton = new JToggleButton(I18n.tr("Pieces"), false);
        statusButton = new JToggleButton(I18n.tr("Status"), false);
        detailsButton = new JToggleButton(I18n.tr("Details"), false);
        trackersButton = new JToggleButton(I18n.tr("Trackers"), false);
        peersButton = new JToggleButton(I18n.tr("Peers"), false);

        filesButton.addActionListener(e -> showDetailComponent(FILES_CARD));
        piecesButton.addActionListener(e -> showDetailComponent(PIECES_CARD));
        statusButton.addActionListener(e -> showDetailComponent(STATUS_CARD));
        detailsButton.addActionListener(e -> showDetailComponent(DETAILS_CARD));
        trackersButton.addActionListener(e -> showDetailComponent(TRACKERS_CARD));
        peersButton.addActionListener(e -> showDetailComponent(PEERS_CARD));

        final Font smallHelvetica = new Font("Helvetica", Font.PLAIN, 9);
        final Dimension buttonDimension = new Dimension(70, 22);
        applyFontAndDimensionToFilterToggleButtons(
                smallHelvetica,
                buttonDimension,
                filesButton,
                piecesButton,
                statusButton,
                detailsButton,
                trackersButton,
                peersButton);

        switcherButtonsGroup.add(filesButton);
        switcherButtonsGroup.add(piecesButton);
        switcherButtonsGroup.add(statusButton);
        switcherButtonsGroup.add(detailsButton);
        switcherButtonsGroup.add(trackersButton);
        switcherButtonsGroup.add(peersButton);

        detailSwitcherButtonsPanel.add(filesButton);
        detailSwitcherButtonsPanel.add(piecesButton);
        detailSwitcherButtonsPanel.add(statusButton);
        detailSwitcherButtonsPanel.add(detailsButton);
        detailSwitcherButtonsPanel.add(trackersButton);
        detailSwitcherButtonsPanel.add(peersButton);

        return detailSwitcherButtonsPanel;
    }

    private void showDetailComponent(final String cardName) {
        CardLayout cardLayout = (CardLayout) detailComponentHolder.getLayout();
        if (cardLayout != null) {
            cardLayout.show(detailComponentHolder, cardName);
            if (!cardPanelMap.containsKey(cardName)) {
                throw new RuntimeException("showDetailComponent() - check your logic, cardName '" + cardName + "' not found");
            }
            UISettings.LAST_SELECTED_TRANSFER_DETAIL_JPANEL.setValue(cardName);
            currentComponent = cardPanelMap.get(cardName);
        }
    }

    private void applyFontAndDimensionToFilterToggleButtons(Font font, Dimension dimension, JToggleButton... buttons) {
        for (JToggleButton button : buttons) {
            button.setFont(font);
            button.setMinimumSize(dimension);
            button.setMaximumSize(dimension);
            button.setPreferredSize(dimension);
        }
    }

    @Override
    public void refresh() {
        // we're a Refresh listener of the GUIMediator, this gets invoked every 1 second
        if (GUIMediator.instance().getSelectedTab() == GUIMediator.Tabs.TRANSFERS && isVisible() && currentComponent != null && selectedBittorrentDownload != null) {
            currentComponent.updateData(selectedBittorrentDownload);
        }
    }

    // gets invoked when a transfer is selected and called back by our RefreshListener implementation
    public void updateData(BittorrentDownload btDownload) {
        selectedBittorrentDownload = btDownload;
        if (currentComponent != null && isVisible()) {
            currentComponent.updateData(btDownload);
        }
    }
}