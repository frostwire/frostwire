/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.library;

import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.player.MediaSource;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.util.DividerLocationSettingUpdater;
import com.limegroup.gnutella.settings.UISettings;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.*;

/**
 * @author gubatron
 * @author aldenml
 */
public class LibraryMediator {
    private static final String FILES_TABLE_KEY = "LIBRARY_FILES_TABLE";
    private static JPanel MAIN_PANEL;
    /**
     * Singleton instance of this class.
     */
    private static LibraryMediator INSTANCE;

    private LibraryExplorer libraryExplorer;
    private LibraryCoverArtPanel libraryCoverArtPanel;
    private LibraryLeftPanel libraryLeftPanel;
    private LibrarySearch librarySearch;
    private CardLayout _tablesViewLayout = new CardLayout();
    private JPanel _tablesPanel;
    private final Map<Object, Integer> scrollbarValues;
    private Object lastSelectedKey;
    private AbstractLibraryTableMediator<?, ?, ?> lastSelectedMediator;
    private final Set<Integer> idScanned;
    private AbstractLibraryTableMediator<?, ?, ?> currentMediator;

    private LibraryMediator() {
        GUIMediator.setSplashScreenString(I18n.tr("Loading Library Window..."));
        idScanned = new HashSet<>();
        getComponent(); // creates MAIN_PANEL
        scrollbarValues = new HashMap<>();
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, getLibraryLeftPanel(), getLibraryRightPanel());
        splitPane.setContinuousLayout(true);
        splitPane.setResizeWeight(0.5);
        splitPane.addPropertyChangeListener(JSplitPane.LAST_DIVIDER_LOCATION_PROPERTY, evt -> {
            JSplitPane splitPane1 = (JSplitPane) evt.getSource();
            int current = splitPane1.getDividerLocation();
            if (current > LibraryLeftPanel.MAX_WIDTH) {
                splitPane1.setDividerLocation(LibraryLeftPanel.MAX_WIDTH);
            } else if (current < LibraryLeftPanel.MIN_WIDTH) {
                splitPane1.setDividerLocation(LibraryLeftPanel.MIN_WIDTH);
            }
        });
        DividerLocationSettingUpdater.install(splitPane, UISettings.UI_LIBRARY_MAIN_DIVIDER_LOCATION);
        MAIN_PANEL.add(splitPane);
    }

    /**
     * @return the `LibraryMediator` instance
     */
    public static LibraryMediator instance() {
        if (INSTANCE == null) {
            INSTANCE = new LibraryMediator();
        }
        return INSTANCE;
    }

    private Object getSelectedKey() {
        return getLibraryExplorer().getSelectedDirectoryHolder();
    }

    public LibraryExplorer getLibraryExplorer() {
        if (libraryExplorer == null) {
            libraryExplorer = new LibraryExplorer();
        }
        return libraryExplorer;
    }

    public LibrarySearch getLibrarySearch() {
        if (librarySearch == null) {
            librarySearch = new LibrarySearch();
        }
        return librarySearch;
    }

    public LibraryCoverArtPanel getLibraryCoverArtPanel() {
        if (libraryCoverArtPanel == null) {
            libraryCoverArtPanel = new LibraryCoverArtPanel();
        }
        return libraryCoverArtPanel;
    }

    public JComponent getComponent() {
        if (MAIN_PANEL == null) {
            MAIN_PANEL = new JPanel(new BorderLayout());
        }
        return MAIN_PANEL;
    }

    private void showView() {
        GUIMediator.safeInvokeAndWait(() -> {
            rememberScrollbarsOnMediators();
            _tablesViewLayout.show(_tablesPanel, LibraryMediator.FILES_TABLE_KEY);
        });
        currentMediator = LibraryFilesTableMediator.instance();
    }

    private void rememberScrollbarsOnMediators() {
        AbstractLibraryTableMediator<?, ?, ?> tableMediator;
        tableMediator = LibraryFilesTableMediator.instance();
        if (tableMediator == null) {
            return;
        }
        if (lastSelectedMediator != null && lastSelectedKey != null) {
            scrollbarValues.put(lastSelectedKey, lastSelectedMediator.getScrollbarValue());
        }
        lastSelectedMediator = tableMediator;
        lastSelectedKey = getSelectedKey();
        int lastScrollValue = scrollbarValues.getOrDefault(lastSelectedKey, 0);
        tableMediator.scrollTo(lastScrollValue);
    }

    void updateTableFiles(DirectoryHolder dirHolder) {
        clearLibraryTable();
        showView();
        LibraryFilesTableMediator.instance().updateTableFiles(dirHolder);
    }

    public void clearDirectoryHolderCaches() {
        getLibraryExplorer().clearDirectoryHolderCaches();
    }

    void clearLibraryTable() {
        LibraryFilesTableMediator.instance().clearTable();
        getLibrarySearch().clear();
    }

    void addFilesToLibraryTable(List<File> files) {
        for (File file : files) {
            LibraryFilesTableMediator.instance().add(file);
        }
        getLibrarySearch().addResults(files.size());
    }

    private JComponent getLibraryLeftPanel() {
        if (libraryLeftPanel == null) {
            libraryLeftPanel = new LibraryLeftPanel(getLibraryExplorer(), getLibraryCoverArtPanel());
        }
        return libraryLeftPanel;
    }

    private JComponent getLibraryRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        _tablesViewLayout = new CardLayout();
        _tablesPanel = new JPanel(_tablesViewLayout);
        _tablesPanel.add(LibraryFilesTableMediator.instance().getComponent(), FILES_TABLE_KEY);
        panel.add(getLibrarySearch(), BorderLayout.PAGE_START);
        panel.add(_tablesPanel, BorderLayout.CENTER);
        return panel;
    }

    public void selectCurrentMedia() {
        final MediaSource currentMedia = MediaPlayer.instance().getCurrentMedia();
        if (currentMedia != null && currentMedia.getFile() != null) {
            //selects the audio node at the top
            LibraryExplorer libraryFiles = getLibraryExplorer();
            //select the song once it's available on the right hand side
            libraryFiles.enqueueRunnable(() -> GUIMediator.safeInvokeLater(() -> LibraryFilesTableMediator.instance().setFileSelected(currentMedia.getFile())));
            libraryFiles.selectAudio();
        }
        //Scroll to current song.
    }

    public boolean isScanned(int id) {
        return idScanned.contains(id);
    }

    public void scan(int hashCode, File location) {
        if (location == null) {
            return;
        }
        idScanned.add(hashCode);
        if (location.isDirectory()) {
            final File[] files = location.listFiles();
            if (files != null) {
                for (File file : files) {
                    scan(hashCode, file);
                }
            }
        } else {
            List<MediaTypeSavedFilesDirectoryHolder> holders = getLibraryExplorer().getMediaTypeSavedFilesDirectoryHolders();
            for (MediaTypeSavedFilesDirectoryHolder holder : holders) {
                Set<File> cache = holder.getCache();
                if (holder.accept(location) && !cache.isEmpty()) {
                    cache.add(location);
                }
            }
        }
    }

    /**
     * If a file has been selected on the right hand side, this method will select such file.
     * <p>
     * If there's a radio station, or if there's more than one file selected, or none, it will return null.
     */
    File getSelectedFile() {
        File toExplore = null;
        DirectoryHolder selectedDirectoryHolder = LibraryMediator.instance().getLibraryExplorer().getSelectedDirectoryHolder();
        boolean fileBasedDirectoryHolderSelected = selectedDirectoryHolder instanceof SavedFilesDirectoryHolder || selectedDirectoryHolder instanceof MediaTypeSavedFilesDirectoryHolder || selectedDirectoryHolder instanceof TorrentDirectoryHolder;
        if (fileBasedDirectoryHolderSelected && LibraryFilesTableMediator.instance().getSelectedLines().size() == 1) {
            toExplore = LibraryFilesTableMediator.instance().getSelectedLines().get(0).getFile();
        }
        return toExplore;
    }

    public void playCurrentSelection() {
        if (currentMediator != null) {
            currentMediator.playCurrentSelection();
        }
    }
}
