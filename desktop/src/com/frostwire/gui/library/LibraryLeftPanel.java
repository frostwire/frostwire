/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.library;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JPanel;
import javax.swing.JSplitPane;

import com.limegroup.gnutella.gui.util.DividerLocationSettingUpdater;
import com.limegroup.gnutella.settings.UISettings;

/**
 * @author gubatron
 * @author aldenml
 * 
 */
public class LibraryLeftPanel extends JPanel {

    public static final int MIN_WIDTH = 155;
    public static final int MAX_WIDTH = 300;

    private final LibraryExplorer libraryExplorer;
    private final LibraryPlaylists libraryPlaylists;
    private final LibraryCoverArt libraryCoverArt;

    private final JSplitPane splitPane;

    public LibraryLeftPanel(LibraryExplorer libraryExplorer, LibraryPlaylists libraryPlaylists, LibraryCoverArt libraryCoverArt) {
        this.libraryExplorer = libraryExplorer;
        this.libraryPlaylists = libraryPlaylists;
        this.libraryCoverArt = libraryCoverArt;

        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        setupUI();
    }

    protected void setupUI() {
        setLayout(new BorderLayout());

        //Prepare a split pane with explorers
        splitPane.setTopComponent(libraryExplorer);
        splitPane.setBottomComponent(libraryPlaylists);
        splitPane.setAutoscrolls(true);

        add(splitPane, BorderLayout.CENTER);

        Dimension minSize = new Dimension(MIN_WIDTH, MIN_WIDTH);
        Dimension maxSize = new Dimension(MAX_WIDTH, MAX_WIDTH);
        libraryCoverArt.setPreferredSize(minSize);
        libraryCoverArt.setMinimumSize(minSize);
        libraryCoverArt.setMaximumSize(maxSize);
        add(libraryCoverArt, BorderLayout.PAGE_END);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layoutComponents();
            }
        });

        DividerLocationSettingUpdater.install(splitPane, UISettings.UI_LIBRARY_EXPLORER_DIVIDER_POSITION);
    }

    protected void layoutComponents() {
        Dimension size = libraryCoverArt.getSize();
        size.height = size.width;
        libraryCoverArt.setSize(size);
        libraryCoverArt.setPreferredSize(size);

        revalidate();
    }
}