/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.tabs;

import com.frostwire.gui.bittorrent.BTDownloadMediator;
import com.limegroup.gnutella.gui.I18n;
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

    public TransfersTab(BTDownloadMediator downloadMediator) {
        super(I18n.tr("Transfers"),
              I18n.tr("Transfers tab description goes here."),
              "transfers_tab");
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

    private void initComponents() {
        mainComponent = new JPanel(new MigLayout("fill, insets 6px 0px 0px 0px, gap 0","[][grow]","[][grow]"));
        mainComponent.add(createTextFilterComponent(), "w 200!, h 30!, gapleft 5px, center, shrink");
        mainComponent.add(createFilterToggleButtons(),"w 500!, h 30!, pad 2 0 0 0, right, wrap");
        mainComponent.add(downloadMediator.getComponent(),"cell 0 1 2 1,grow"); // "cell <column> <row> <width> <height>"
        setTransfersFilterModeListener(downloadMediator);
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
