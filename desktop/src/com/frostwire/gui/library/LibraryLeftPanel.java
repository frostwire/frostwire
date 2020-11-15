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

package com.frostwire.gui.library;

import com.limegroup.gnutella.gui.util.DividerLocationSettingUpdater;
import com.limegroup.gnutella.settings.UISettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * @author gubatron
 * @author aldenml
 */
class LibraryLeftPanel extends JPanel {
    static final int MIN_WIDTH = 155;
    static final int MAX_WIDTH = 300;
    private final LibraryExplorer libraryExplorer;
    private final LibraryPlaylists libraryPlaylists;
    private final LibraryCoverArtPanel libraryCoverArtPanel;
    private final JSplitPane splitPane;

    LibraryLeftPanel(LibraryExplorer libraryExplorer, LibraryPlaylists libraryPlaylists, LibraryCoverArtPanel libraryCoverArtPanel) {
        this.libraryExplorer = libraryExplorer;
        this.libraryPlaylists = libraryPlaylists;
        this.libraryCoverArtPanel = libraryCoverArtPanel;
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        setupUI();
    }

    private void setupUI() {
        setLayout(new BorderLayout());
        //Prepare a split pane with explorers
        splitPane.setTopComponent(libraryExplorer);
        splitPane.setBottomComponent(libraryPlaylists);
        splitPane.setAutoscrolls(true);
        add(splitPane, BorderLayout.CENTER);
        Dimension minSize = new Dimension(MIN_WIDTH, MIN_WIDTH);
        Dimension maxSize = new Dimension(MAX_WIDTH, MAX_WIDTH);
        libraryCoverArtPanel.setPreferredSize(minSize);
        libraryCoverArtPanel.setMinimumSize(minSize);
        libraryCoverArtPanel.setMaximumSize(maxSize);
        add(libraryCoverArtPanel, BorderLayout.PAGE_END);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layoutComponents();
            }
        });
        DividerLocationSettingUpdater.install(splitPane, UISettings.UI_LIBRARY_EXPLORER_DIVIDER_POSITION);
    }

    private void layoutComponents() {
        Dimension size = libraryCoverArtPanel.getSize();
        size.height = size.width;
        libraryCoverArtPanel.setSize(size);
        libraryCoverArtPanel.setPreferredSize(size);
        revalidate();
    }
}