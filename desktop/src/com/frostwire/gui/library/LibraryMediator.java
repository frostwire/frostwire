/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

import com.frostwire.alexandria.Library;
import com.frostwire.alexandria.Playlist;
import com.frostwire.alexandria.PlaylistItem;
import com.frostwire.alexandria.db.LibraryDatabase;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.player.MediaSource;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.util.DividerLocationSettingUpdater;
import com.limegroup.gnutella.settings.LibrarySettings;
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
    private static final String PLAYLISTS_TABLE_KEY = "LIBRARY_PLAYLISTS_TABLE";
    private static JPanel MAIN_PANEL;
    /**
     * Singleton instance of this class.
     */
    private static LibraryMediator INSTANCE;
    private static Library LIBRARY;
    private LibraryExplorer libraryExplorer;
    private LibraryPlaylists libraryPlaylists;
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
     * @return the <tt>LibraryMediator</tt> instance
     */
    public static LibraryMediator instance() {
        if (INSTANCE == null) {
            INSTANCE = new LibraryMediator();
        }
        return INSTANCE;
    }

    public static Library getLibrary() {
        if (LIBRARY == null) {
            LIBRARY = new Library(LibrarySettings.LIBRARY_DATABASE);
        }
        return LIBRARY;
    }

    private Object getSelectedKey() {
        if (getSelectedPlaylist() != null) {
            return getSelectedPlaylist();
        } else {
            return getLibraryExplorer().getSelectedDirectoryHolder();
        }
    }

    public LibraryExplorer getLibraryExplorer() {
        if (libraryExplorer == null) {
            libraryExplorer = new LibraryExplorer();
        }
        return libraryExplorer;
    }

    LibraryPlaylists getLibraryPlaylists() {
        if (libraryPlaylists == null) {
            libraryPlaylists = new LibraryPlaylists();
        }
        return libraryPlaylists;
    }

    /**
     *
     */
    Playlist getSelectedPlaylist() {
        return getLibraryPlaylists().getSelectedPlaylist();
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

    private void showView(final String key) {
        GUIMediator.safeInvokeAndWait(() -> {
            rememberScrollbarsOnMediators(key);
            _tablesViewLayout.show(_tablesPanel, key);
        });
        switch (key) {
            case FILES_TABLE_KEY:
                currentMediator = LibraryFilesTableMediator.instance();
                break;
            case PLAYLISTS_TABLE_KEY:
                currentMediator = LibraryPlaylistsTableMediator.instance();
                break;
            default:
                currentMediator = null;
                break;
        }
    }

    private void rememberScrollbarsOnMediators(String key) {
        if (key == null) {
            return;
        }
        AbstractLibraryTableMediator<?, ?, ?> tableMediator = null;
        AbstractLibraryListPanel listPanel = null;
        if (key.equals(FILES_TABLE_KEY)) {
            tableMediator = LibraryFilesTableMediator.instance();
            listPanel = getLibraryExplorer();
        } else if (key.equals(PLAYLISTS_TABLE_KEY)) {
            tableMediator = LibraryPlaylistsTableMediator.instance();
            listPanel = getLibraryPlaylists();
        }
        if (tableMediator == null || listPanel == null) {
            return;
        }
        if (lastSelectedMediator != null && lastSelectedKey != null) {
            scrollbarValues.put(lastSelectedKey, lastSelectedMediator.getScrollbarValue());
        }
        lastSelectedMediator = tableMediator;
        lastSelectedKey = getSelectedKey();
        if (listPanel.getPendingRunnables().size() == 0) {
            int lastScrollValue = scrollbarValues.getOrDefault(lastSelectedKey, 0);
            tableMediator.scrollTo(lastScrollValue);
        }
    }

    void updateTableFiles(DirectoryHolder dirHolder) {
        clearLibraryTable();
        showView(FILES_TABLE_KEY);
        LibraryFilesTableMediator.instance().updateTableFiles(dirHolder);
    }

    public void clearDirectoryHolderCaches() {
        getLibraryExplorer().clearDirectoryHolderCaches();
    }

    void updateTableItems(Playlist playlist) {
        clearLibraryTable();
        showView(PLAYLISTS_TABLE_KEY);
        LibraryPlaylistsTableMediator.instance().updateTableItems(playlist);
    }

    void clearLibraryTable() {
        LibraryFilesTableMediator.instance().clearTable();
        LibraryPlaylistsTableMediator.instance().clearTable();
        getLibrarySearch().clear();
    }

    void addFilesToLibraryTable(List<File> files) {
        for (File file : files) {
            LibraryFilesTableMediator.instance().add(file);
        }
        getLibrarySearch().addResults(files.size());
    }

    void addItemsToLibraryTable(List<PlaylistItem> items) {
        for (PlaylistItem item : items) {
            LibraryPlaylistsTableMediator.instance().add(item);
        }
        LibraryPlaylistsTableMediator.instance().getTable().repaint();
        getLibrarySearch().addResults(items.size());
    }

    private JComponent getLibraryLeftPanel() {
        if (libraryLeftPanel == null) {
            libraryLeftPanel = new LibraryLeftPanel(getLibraryExplorer(), getLibraryPlaylists(), getLibraryCoverArtPanel());
        }
        return libraryLeftPanel;
    }

    private JComponent getLibraryRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        _tablesViewLayout = new CardLayout();
        _tablesPanel = new JPanel(_tablesViewLayout);
        _tablesPanel.add(LibraryFilesTableMediator.instance().getComponent(), FILES_TABLE_KEY);
        _tablesPanel.add(LibraryPlaylistsTableMediator.instance().getComponent(), PLAYLISTS_TABLE_KEY);
        panel.add(getLibrarySearch(), BorderLayout.PAGE_START);
        panel.add(_tablesPanel, BorderLayout.CENTER);
        return panel;
    }

    public void selectCurrentMedia() {
        //Select current playlist.
        Playlist currentPlaylist = MediaPlayer.instance().getCurrentPlaylist();
        final MediaSource currentMedia = MediaPlayer.instance().getCurrentMedia();
        //If the current song is being played from a playlist.
        if (currentPlaylist != null && currentMedia != null && currentMedia.getPlaylistItem() != null) {
            if (currentPlaylist.getId() != LibraryDatabase.STARRED_PLAYLIST_ID) {
                //select the song once it's available on the right hand side
                getLibraryPlaylists().enqueueRunnable(() -> GUIMediator.safeInvokeLater(() -> LibraryPlaylistsTableMediator.instance().setItemSelected(currentMedia.getPlaylistItem())));
                //select the playlist
                getLibraryPlaylists().selectPlaylist(currentPlaylist);
            } else {
                LibraryExplorer libraryFiles = getLibraryExplorer();
                //select the song once it's available on the right hand side
                libraryFiles.enqueueRunnable(() -> GUIMediator.safeInvokeLater(() -> LibraryPlaylistsTableMediator.instance().setItemSelected(currentMedia.getPlaylistItem())));
                libraryFiles.selectStarred();
            }
        } else if (currentMedia != null && currentMedia.getFile() != null) {
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
        } else if (LibraryPlaylistsTableMediator.instance().getSelectedLines() != null && LibraryPlaylistsTableMediator.instance().getSelectedLines().size() == 1) {
            toExplore = LibraryPlaylistsTableMediator.instance().getSelectedLines().get(0).getFile();
        }
        return toExplore;
    }

    public void playCurrentSelection() {
        if (currentMediator != null) {
            currentMediator.playCurrentSelection();
        }
    }
}
