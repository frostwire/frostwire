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
import java.awt.*;
import java.awt.event.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class TransfersTab extends AbstractTab {
    final BTDownloadMediator downloadMediator;
    private final String FILTER_TEXT_HINT = I18n.tr("filter transfers here");
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

    private void initComponents() {
        mainComponent = new JPanel(new MigLayout("fill, insets 3px 3px 3px 3px, gap 0","[][grow]","[][grow]"));
        mainComponent.add(createTextFilterComponent(), "w 200!, h 25!, gapleft 5px, center, shrink");
        mainComponent.add(createFilterToggleButtons(),"w 332!, h 32!, pad 0 0 0 0, center, wrap");
        mainComponent.add(downloadMediator.getComponent(),"cell 0 1 2 1,grow"); // "cell <column> <row> <width> <height>"
    }

    private JTextArea createTextFilterComponent() {
        filterText = new JTextArea();
        filterText.setEditable(true);
        filterText.setText(FILTER_TEXT_HINT);
        filterText.setFont(new Font("Helvetica",Font.PLAIN, 12));
        filterText.setForeground(Color.GRAY);
        filterText.addMouseListener(new TextFilterMouseAdapter());
        filterText.addKeyListener(new TextFilterKeyAdapter());
        filterText.selectAll();
        return filterText;
    }

    private JPanel createFilterToggleButtons() {
        JPanel filterButtonsContainer = new JPanel(new MigLayout("align center, ins 0 0 0 0"));
        ButtonGroup filterGroup = new ButtonGroup();
        filterAllButton = new JToggleButton(I18n.tr("All"),true);
        filterDownloadingButton = new JToggleButton(I18n.tr("Downloading"),false);
        filterSeedingButton = new JToggleButton(I18n.tr("Seeding"),false);
        filterFinishedButton = new JToggleButton(I18n.tr("Finished"),false);

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

    public JComponent getComponent() {
        return mainComponent;
    }

    private void onTextFilterKeyTyped() {
        if (filterText.getText().equals("")) {
            restoreFilterTextHint();
        } else {
            filterText.setForeground(Color.BLACK);

            // TODO: invoke filtering code here on BTDownloadMediator.
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

    private class TextFilterMouseAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            clearFilterTextHint();
        }
    }

    private class TextFilterKeyAdapter extends KeyAdapter {
        @Override
        public void keyReleased(KeyEvent e) {
            onTextFilterKeyTyped();
        }
    }
}
