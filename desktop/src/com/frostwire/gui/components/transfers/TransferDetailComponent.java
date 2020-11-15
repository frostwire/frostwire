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
import java.awt.event.MouseAdapter;
import java.util.HashMap;

/**
 * The component that holds the different panels to present
 * all the different torrent transfer details.
 */
public final class TransferDetailComponent extends JPanel implements RefreshListener {
    // inner use strings, no need to translate, just to switch cards layout
    private final String GENERAL_CARD = "G";
    private final String FILES_CARD = "F";
    private final String PIECES_CARD = "P";
    private final String TRACKERS_CARD = "T";
    private final String PEERS_CARD = "p";
    //private static final Logger LOG = Logger.getLogger(TransferDetailComponent.class);
    private JToggleButton filesButton;
    private JToggleButton piecesButton;
    private JToggleButton generalButton;
    private JToggleButton trackersButton;
    private JToggleButton peersButton;
    private JPanel detailComponentHolder;
    private HashMap<String, TransferDetailPanel> cardPanelMap;
    private TransferDetailPanel currentComponent;
    private BittorrentDownload selectedBittorrentDownload;

    public TransferDetailComponent(MouseAdapter hideDetailsActionListener) {
        super(new MigLayout("fill, insets 0 0 0 0",
                "",
                "[top][grow]"));
        JPanel labelAndLink = new JPanel(new FlowLayout());
        JLabel hideLink = new JLabel("<html><a href='#'>" + I18n.tr("hide") + "</a></html>");
        hideLink.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        hideLink.setFont(new Font("Helvetica", Font.PLAIN, 13));
        hideLink.addMouseListener(hideDetailsActionListener);
        labelAndLink.add(new JLabel(I18n.tr("Transfer Detail")));
        labelAndLink.add(hideLink, "left, gapleft 10px, growx");
        add(labelAndLink, "left, gapleft 10px, growx");
        add(createDetailSwitcherButtons(), "push, right, wrap");
        add(createDetailComponentHolder(), "hmin 0px, span 2, grow");
        GUIMediator.addRefreshListener(this);
    }

    private JPanel createDetailComponentHolder() {
        detailComponentHolder = new JPanel(new CardLayout());
        TransferDetailGeneral generalComponent;
        detailComponentHolder.add(generalComponent = new TransferDetailGeneral(), GENERAL_CARD);
        TransferDetailFiles filesComponent;
        detailComponentHolder.add(filesComponent = new TransferDetailFiles(), FILES_CARD);
        TransferDetailPieces piecesComponent;
        detailComponentHolder.add(piecesComponent = new TransferDetailPieces(), PIECES_CARD);
        TransferDetailTrackers trackersComponent;
        detailComponentHolder.add(trackersComponent = new TransferDetailTrackers(), TRACKERS_CARD);
        TransferDetailPeers peersComponent;
        detailComponentHolder.add(peersComponent = new TransferDetailPeers(), PEERS_CARD);
        cardPanelMap = new HashMap<>();
        cardPanelMap.put(GENERAL_CARD, generalComponent);
        cardPanelMap.put(FILES_CARD, filesComponent);
        cardPanelMap.put(PIECES_CARD, piecesComponent);
        cardPanelMap.put(TRACKERS_CARD, trackersComponent);
        cardPanelMap.put(PEERS_CARD, peersComponent);
        HashMap<String, JToggleButton> cardButtonMap = new HashMap<>();
        cardButtonMap.put(GENERAL_CARD, generalButton);
        cardButtonMap.put(FILES_CARD, filesButton);
        cardButtonMap.put(PIECES_CARD, piecesButton);
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
        generalButton = new JToggleButton(I18n.tr("General"), false);
        filesButton = new JToggleButton(I18n.tr("Files"), true);
        piecesButton = new JToggleButton(I18n.tr("Pieces"), false);
        trackersButton = new JToggleButton(I18n.tr("Trackers"), false);
        peersButton = new JToggleButton(I18n.tr("Peers"), false);
        generalButton.addActionListener(e -> showDetailComponent(GENERAL_CARD));
        filesButton.addActionListener(e -> showDetailComponent(FILES_CARD));
        piecesButton.addActionListener(e -> showDetailComponent(PIECES_CARD));
        trackersButton.addActionListener(e -> showDetailComponent(TRACKERS_CARD));
        peersButton.addActionListener(e -> showDetailComponent(PEERS_CARD));
        final Font smallHelvetica = new Font("Helvetica", Font.PLAIN, 11);
        final Dimension buttonDimension = new Dimension(80, 24);
        applyFontAndDimensionToFilterToggleButtons(
                smallHelvetica,
                buttonDimension,
                generalButton,
                filesButton,
                piecesButton,
                trackersButton,
                peersButton);
        switcherButtonsGroup.add(generalButton);
        switcherButtonsGroup.add(filesButton);
        switcherButtonsGroup.add(piecesButton);
        switcherButtonsGroup.add(trackersButton);
        switcherButtonsGroup.add(peersButton);
        detailSwitcherButtonsPanel.add(generalButton);
        detailSwitcherButtonsPanel.add(filesButton);
        detailSwitcherButtonsPanel.add(piecesButton);
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

    interface TransferDetailPanel {
        void updateData(BittorrentDownload btDownload);
    }
}
