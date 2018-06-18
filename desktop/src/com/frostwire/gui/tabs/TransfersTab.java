/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.gui.tabs;

import com.frostwire.gui.bittorrent.*;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.UISettings;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class TransfersTab extends AbstractTab {
    private static final Logger LOG = Logger.getLogger(TransfersTab.class);
    private final BTDownloadMediator downloadMediator;

    // it will be a reference to the download mediator above who is the one interested.
    private TransfersFilterModeListener transfersFilterModeListener;

    public static final String FILTER_TEXT_HINT = I18n.tr("filter transfers here");
    private JToggleButton filterAllButton;
    private JToggleButton filterDownloadingButton;
    private JToggleButton filterSeedingButton;
    private JToggleButton filterFinishedButton;
    private JPanel mainComponent;
    private JTextArea filterText;

    private final boolean dedicatedTransfersTabAvailable;

    public TransfersTab(BTDownloadMediator downloadMediator) {
        super(I18n.tr("Transfers"),
              I18n.tr("Transfers tab description goes here."),
              "transfers_tab");
        dedicatedTransfersTabAvailable = !UISettings.UI_SEARCH_TRANSFERS_SPLIT_VIEW.getValue();
        this.downloadMediator = downloadMediator;
        initComponents();
    }

    public enum FilterMode {
        ALL,
        DOWNLOADING,
        SEEDING,
        FINISHED
    }

    public void showTransfers(FilterMode mode) {
        if (mode == FilterMode.ALL) {
            filterAllButton.doClick();
        } else if (mode == FilterMode.DOWNLOADING) {
            filterDownloadingButton.doClick();
        } else if (mode == FilterMode.FINISHED) {
            filterFinishedButton.doClick();
        } else if (mode == FilterMode.SEEDING) {
            filterSeedingButton.doClick();
        }
    }

    public interface TransfersFilterModeListener {
        void onFilterUpdate(FilterMode mode, String searchKeywords);
        void onFilterUpdate(String searchKeywords);
    }

    private class TransferTableSelectionListener implements BTDownloadMediator.BTDownloadSelectionListener {
        /**
         * @param selected null if nothing has been selected, a BTDownload otherwise
         */
        @Override
        public void onTransferSelected(BTDownload selected) {
            if (!dedicatedTransfersTabAvailable) {
                LOG.info("Transfer Details not available in this search/transfers split screen mode");
                hideTransferDetailsComponent();
                return;
            }
            if (selected == null ||
                selected instanceof YouTubeDownload ||
                selected instanceof SoundcloudDownload ||
                selected instanceof HttpDownload) {
                hideTransferDetailsComponent();
            } else {
                showTransferDetailsComponent(selected);
            }
        }
    }

    private void hideTransferDetailsComponent() {
        LOG.info("TODO: Hide transfer details component");
    }

    private void showTransferDetailsComponent(BTDownload selected) {
        LOG.info("TODO: Show transfer details component (only if TransfersTab is a component on its own)");
        if (!(selected instanceof BittorrentDownload) &&
            !(selected instanceof TorrentFetcherDownload)) {
            LOG.warn("Check your logic. TransfersTab.showTransferDetailsComponent() invoked on non-torrent transfer");
            return;
        }
    }

    private void initComponents() {
        mainComponent = new JPanel(new MigLayout("fill, insets 6px 0px 0px 0px, gap 0","[][grow]","[][grow]"));
        mainComponent.add(createTextFilterComponent(), "w 200!, h 30!, gapleft 5px, center, shrink");
        mainComponent.add(createFilterToggleButtons(),"w 500!, h 30!, pad 2 0 0 0, right, wrap");
        mainComponent.add(downloadMediator.getComponent(),"cell 0 1 2 1,grow"); // "cell <column> <row> <width> <height>"
        setTransfersFilterModeListener(downloadMediator);
        downloadMediator.setBTDownloadSelectionListener(new TransferTableSelectionListener());
    }

    private void setTransfersFilterModeListener(TransfersFilterModeListener transfersFilterModeListener) {
        this.transfersFilterModeListener = transfersFilterModeListener;
    }

    private JTextArea createTextFilterComponent() {
        filterText = new JTextArea();
        filterText.setEditable(true);
        filterText.setText(FILTER_TEXT_HINT);
        filterText.setFont(new Font("Helvetica",Font.PLAIN, 12));
        filterText.setForeground(Color.GRAY);
        filterText.addMouseListener(new TextFilterMouseAdapter());
        filterText.addKeyListener(new TextFilterKeyAdapter());
        filterText.addFocusListener(new TextFilterFocusAdapter());
        filterText.selectAll();
        final CompoundBorder compoundBorder =
                BorderFactory.createCompoundBorder(filterText.getBorder(),
                        BorderFactory.createEmptyBorder(4, 2, 2, 2));
        filterText.setBorder(compoundBorder);
        return filterText;
    }

    private JPanel createFilterToggleButtons() {
        JPanel filterButtonsContainer = new JPanel(new MigLayout("align right, ins 0 0 0 8"));
        ButtonGroup filterGroup = new ButtonGroup();
        filterAllButton = new JToggleButton(I18n.tr("All"),true);
        filterDownloadingButton = new JToggleButton(I18n.tr("Downloading"),false);
        filterSeedingButton = new JToggleButton(I18n.tr("Seeding"),false);
        filterFinishedButton = new JToggleButton(I18n.tr("Finished"),false);
        filterAllButton.addActionListener(new OnFilterButtonToggledListener(FilterMode.ALL));
        filterDownloadingButton.addActionListener(new OnFilterButtonToggledListener(FilterMode.DOWNLOADING));
        filterSeedingButton.addActionListener(new OnFilterButtonToggledListener(FilterMode.SEEDING));
        filterFinishedButton.addActionListener(new OnFilterButtonToggledListener(FilterMode.FINISHED));
        final Font smallHelvetica = new Font("Helvetica", Font.PLAIN, 12);
        final Dimension buttonDimension = new Dimension(115,28);
        applyFontAndDimensionToFilterToggleButtons(smallHelvetica, buttonDimension,
                filterAllButton, filterDownloadingButton,filterSeedingButton,filterFinishedButton);

        filterAllButton.setPreferredSize(buttonDimension);
        filterAllButton.setMinimumSize(buttonDimension);
        filterAllButton.setMaximumSize(buttonDimension);
        filterGroup.add(filterAllButton);
        filterGroup.add(filterDownloadingButton);
        filterGroup.add(filterSeedingButton);
        filterGroup.add(filterFinishedButton);
        filterButtonsContainer.add(filterAllButton);
        filterButtonsContainer.add(filterDownloadingButton);
        filterButtonsContainer.add(filterSeedingButton);
        filterButtonsContainer.add(filterFinishedButton);
        return filterButtonsContainer;
    }

    private void applyFontAndDimensionToFilterToggleButtons(Font font, Dimension dimension, JToggleButton ... buttons) {
        for (JToggleButton button : buttons) {
            button.setFont(font);
            button.setMinimumSize(dimension);
            button.setMaximumSize(dimension);
            button.setPreferredSize(dimension);
        }
    }

    public JComponent getComponent() {
        return mainComponent;
    }

    private void onTextFilterKeyTyped() {
        final String text = filterText.getText();
        if (text.equals("")) {
            restoreFilterTextHint();
        } else {
            filterText.setForeground(Color.BLACK);
        }
        if (transfersFilterModeListener != null) {
            transfersFilterModeListener.onFilterUpdate(text);
        }
    }

    private void clearFilterTextHint() {
        if (filterText.getText().equals(FILTER_TEXT_HINT)) {
            filterText.setText("");
            filterText.setForeground(Color.BLACK);
        }
    }

    private void restoreFilterTextHint() {
        filterText.setText(FILTER_TEXT_HINT);
        filterText.setForeground(Color.GRAY);
        filterText.selectAll();
    }

    private void onFilterTextFocusLost() {
        if (filterText.getText().equals("")) {
            restoreFilterTextHint();
        }
    }

    private class TextFilterMouseAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            clearFilterTextHint();
        }

        @Override
        public void mousePressed(MouseEvent e) {
            clearFilterTextHint();
        }
    }

    private class TextFilterKeyAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            onTextFilterKeyTyped();
        }

        @Override
        public void keyReleased(KeyEvent e) {
            onTextFilterKeyTyped();
        }
    }

    private class TextFilterFocusAdapter extends FocusAdapter {
        @Override
        public void focusLost(FocusEvent e) {
            onFilterTextFocusLost();
        }
    }

    private class OnFilterButtonToggledListener implements ActionListener {
        final private FilterMode filterMode;
         OnFilterButtonToggledListener(FilterMode filterMode) {
            this.filterMode = filterMode;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (transfersFilterModeListener != null) {
                transfersFilterModeListener.onFilterUpdate(filterMode, filterText.getText());
            }
        }
    }
}
