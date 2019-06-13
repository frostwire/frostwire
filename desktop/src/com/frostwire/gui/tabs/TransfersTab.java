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
import com.frostwire.gui.components.transfers.TransferDetailComponent;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.util.BackgroundExecutorService;
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
    public static final String FILTER_TEXT_HINT = I18n.tr("filter transfers here");
    private final BTDownloadMediator downloadMediator; // holds the JTable
    private final boolean dedicatedTransfersTabAvailable;
    /**
     * Visible only on non search/transfer split mode and
     * upon a Torrent Download selection.
     */
    private JSplitPane transferDetailSplitter;
    private TransferDetailComponent transferDetailComponent;
    // it will be a reference to the download mediator above who is the one interested.
    private TransfersFilterModeListener transfersFilterModeListener;
    private JToggleButton filterAllButton;
    private JToggleButton filterDownloadingButton;
    private JToggleButton filterSeedingButton;
    private JToggleButton filterFinishedButton;
    private JPanel mainComponent;
    private JTextArea filterText;
    private int lastSplitterLocationWithDetailsVisible = -1;

    public TransfersTab(BTDownloadMediator downloadMediator) {
        super(I18n.tr("Transfers"),
                I18n.tr("Transfers tab description goes here."),
                "transfers_tab");
        dedicatedTransfersTabAvailable = !UISettings.UI_SEARCH_TRANSFERS_SPLIT_VIEW.getValue();
        this.downloadMediator = downloadMediator;
        initComponents();
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

    private void initComponents() {
        mainComponent = new JPanel(new MigLayout("fill, insets 6px 0px 0px 0px, gap 0", "[][][grow]"));
        // removed last parameter: rowConstraints="[][grow]"
        // it was causing the entire transfer tab not to grow vertically on bigger screens
        // Transfers [ text filter]           [filter buttons] row
        mainComponent.add(new JLabel(I18n.tr("Transfers")), "h 30!, gapleft 10px, left");
        mainComponent.add(createTextFilterComponent(), "w 200!, h 30!, gapleft 5px, center, shrink");
        mainComponent.add(createFilterToggleButtons(), "w 500!, h 30!, pad 2 0 0 0, right, wrap");
        if (dedicatedTransfersTabAvailable) {
            transferDetailSplitter = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
            transferDetailSplitter.setDividerLocation(270);
            transferDetailSplitter.setResizeWeight(1); // Top component gets all the weight
            JComponent transfersComponent = downloadMediator.getComponent();
            transfersComponent.setMinimumSize(new Dimension(100, 200));
            transferDetailSplitter.add(transfersComponent);
            transferDetailComponent = new TransferDetailComponent(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    downloadMediator.clearSelection();
                }
            });
            transferDetailSplitter.add(transferDetailComponent);
            mainComponent.add(transferDetailSplitter, "cell 0 1 3 1, grow, pushy, hmax 10000px, wrap"); // "cell <column> <row> <width> <height>"
        } else {
            mainComponent.add(downloadMediator.getComponent(), "cell 0 1 3 1, grow, pushy, wrap"); // "cell <column> <row> <width> <height>"
        }
        setTransfersFilterModeListener(downloadMediator);
        downloadMediator.setBTDownloadSelectionListener(new TransferTableSelectionListener());
    }

    private void hideTransferDetailsComponent() {
        if (!dedicatedTransfersTabAvailable) {
            return;
        }
        lastSplitterLocationWithDetailsVisible = transferDetailSplitter.getDividerLocation();
        transferDetailComponent.setVisible(false);
    }

    private void showTransferDetailsComponent(BittorrentDownload selected) {
        if (!dedicatedTransfersTabAvailable) {
            return;
        }
        boolean transferDetailComponentWasAlreadyVisible = transferDetailComponent.isVisible();
        transferDetailComponent.setVisible(true);
        transferDetailComponent.updateData(selected);
        if (!transferDetailComponentWasAlreadyVisible) {
            Container parent = transferDetailSplitter.getParent();
            int h = parent.getSize().height;
            lastSplitterLocationWithDetailsVisible = 2 * h / 3;
            // special case of too much space
            if (h > 800) {
                lastSplitterLocationWithDetailsVisible = h - 500;
            }
            transferDetailSplitter.setDividerLocation(lastSplitterLocationWithDetailsVisible);
        }
    }

    private void setTransfersFilterModeListener(TransfersFilterModeListener transfersFilterModeListener) {
        this.transfersFilterModeListener = transfersFilterModeListener;
    }

    private JTextArea createTextFilterComponent() {
        filterText = new JTextArea();
        filterText.setEditable(true);
        filterText.setText(FILTER_TEXT_HINT);
        filterText.setFont(new Font("Helvetica", Font.PLAIN, 12));
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
        filterAllButton = new JToggleButton(I18n.tr("All"), true);
        filterDownloadingButton = new JToggleButton(I18n.tr("Downloading"), false);
        filterSeedingButton = new JToggleButton(I18n.tr("Seeding"), false);
        filterFinishedButton = new JToggleButton(I18n.tr("Finished"), false);
        filterAllButton.addActionListener(new OnFilterButtonToggledListener(FilterMode.ALL));
        filterDownloadingButton.addActionListener(new OnFilterButtonToggledListener(FilterMode.DOWNLOADING));
        filterSeedingButton.addActionListener(new OnFilterButtonToggledListener(FilterMode.SEEDING));
        filterFinishedButton.addActionListener(new OnFilterButtonToggledListener(FilterMode.FINISHED));
        final Font smallHelvetica = new Font("Helvetica", Font.PLAIN, 12);
        final Dimension buttonDimension = new Dimension(115, 28);
        applyFontAndDimensionToButtons(smallHelvetica, buttonDimension,
                filterAllButton, filterDownloadingButton, filterSeedingButton, filterFinishedButton);
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

    private void applyFontAndDimensionToButtons(Font font, Dimension dimension, JComponent... buttons) {
        for (JComponent button : buttons) {
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

    public enum FilterMode {
        ALL,
        DOWNLOADING,
        SEEDING,
        FINISHED
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
                hideTransferDetailsComponent();
                return;
            }
            if (selected == null ||
                    selected instanceof SoundcloudDownload ||
                    selected instanceof HttpDownload ||
                    selected instanceof TorrentFetcherDownload) {
                hideTransferDetailsComponent();
            } else if (selected instanceof BittorrentDownload) {
                BittorrentDownload bittorrentDownload = (BittorrentDownload) selected;
                showTransferDetailsComponent(bittorrentDownload);
                // TODO: remove this hack and the validate call inside ensureDownloadVisible
                // Hack. Need to let the UI thread re-calculate the dimensions
                // of the transfers table in order for downloadMediator.ensureDownloadVisible(btd)
                // to calculate the new location of the row that's to be scrolled to.
                BackgroundExecutorService.schedule(() -> {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                    }
                    GUIMediator.safeInvokeLater(() -> downloadMediator.ensureDownloadVisible(bittorrentDownload));
                });
            }
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
                filterText.setText("");
                transfersFilterModeListener.onFilterUpdate(filterMode, filterText.getText());
            }
        }
    }
}
