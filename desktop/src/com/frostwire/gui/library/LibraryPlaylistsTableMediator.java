/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

import com.frostwire.alexandria.Playlist;
import com.frostwire.alexandria.PlaylistItem;
import com.frostwire.alexandria.db.LibraryDatabase;
import com.frostwire.gui.bittorrent.CreateTorrentDialog;
import com.frostwire.gui.library.tags.TagsReader;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.player.MediaSource;
import com.frostwire.gui.theme.SkinMenu;
import com.frostwire.gui.theme.SkinMenuItem;
import com.frostwire.gui.theme.SkinPopupMenu;
import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.gui.actions.LimeAction;
import com.limegroup.gnutella.gui.actions.SearchAction;
import com.limegroup.gnutella.gui.search.GenericCellEditor;
import com.limegroup.gnutella.gui.tables.LimeJTable;
import com.limegroup.gnutella.gui.util.GUILauncher;
import com.limegroup.gnutella.gui.util.GUILauncher.LaunchableProvider;
import com.limegroup.gnutella.util.QueryUtils;
import net.miginfocom.swing.MigLayout;
import org.limewire.util.OSUtils;

import javax.swing.*;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class wraps the JTable that displays files in the library,
 * controlling access to the table and the various table properties.
 * It is the Mediator to the Table part of the Library display.
 *
 * @author gubatron
 * @author aldenml
 */
final class LibraryPlaylistsTableMediator extends AbstractLibraryTableMediator<LibraryPlaylistsTableModel, LibraryPlaylistsTableDataLine, PlaylistItem> {
    /**
     * Variables so the PopupMenu & ButtonRow can have the same listeners
     */
    private static Action LAUNCH_ACTION;
    private static Action LAUNCH_OS_ACTION;
    private static Action OPEN_IN_FOLDER_ACTION;
    private static Action CREATE_TORRENT_ACTION;
    private static Action DELETE_ACTION;
    private static Action SEND_TO_ITUNES_ACTION;
    /**
     * instance, for singleton access
     */
    private static LibraryPlaylistsTableMediator INSTANCE;
    private Playlist currentPlaylist;
    private final Action importToPlaylistAction = new ImportToPlaylistAction();
    private final Action exportPlaylistAction = new ExportPlaylistAction();
    private final Action cleanupPlaylistAction = new CleanupPlaylistAction();
    private final Action refreshID3TagsAction = new RefreshID3TagsAction();
    private final Action COPY_PLAYLIST_FILES_TO_FOLDER_ACTION = new LibraryPlaylists.CopyPlaylistFilesAction();
    private JPanel mainContainer;

    /**
     * Note: This is set up for this to work.
     * Polling is not needed though, because updates
     * already generate update events.
     */
    private LibraryPlaylistsTableMediator() {
        super("LIBRARY_PLAYLISTS_TABLE");
        setMediaType(MediaType.getAudioMediaType());
    }

    public static LibraryPlaylistsTableMediator instance() {
        if (INSTANCE == null) {
            INSTANCE = new LibraryPlaylistsTableMediator();
        }
        return INSTANCE;
    }

    Playlist getCurrentPlaylist() {
        return currentPlaylist;
    }

    /**
     * Build some extra listeners
     */
    protected void buildListeners() {
        super.buildListeners();
        LAUNCH_ACTION = new LaunchAction();
        LAUNCH_OS_ACTION = new LaunchOSAction();
        OPEN_IN_FOLDER_ACTION = new OpenInFolderAction();
        CREATE_TORRENT_ACTION = new CreateTorrentAction();
        DELETE_ACTION = new RemoveFromPlaylistAction();
        SEND_TO_ITUNES_ACTION = new SendAudioFilesToiTunes();
    }

    /**
     * Set up the constants
     */
    @SuppressWarnings("serial")
    protected void setupConstants() {
        super.setupConstants();
        MAIN_PANEL = new PaddedPanel();
        DATA_MODEL = new LibraryPlaylistsTableModel();
        DATA_MODEL.sort(LibraryPlaylistsTableDataLine.SORT_INDEX_IDX);
        TABLE = new LimeJTable(DATA_MODEL) {
            private final Image bigAudioIcon = GUIMediator.getThemeImage("audio128x128").getImage();

            protected void paintComponent(java.awt.Graphics g) {
                //System.out.println("LibraryPlaylistTableMediator.paintComponent() - TABLE.getRowCount() " + TABLE.getRowCount());
                if (TABLE.getRowCount() == 0) {
                    drawHelpGraphics(g, bigAudioIcon);
                } else {
                    super.paintComponent(g);
                }
            }
        };
        Action[] aa = new Action[]{LAUNCH_ACTION, OPEN_IN_FOLDER_ACTION, SEND_TO_FRIEND_ACTION, DELETE_ACTION, OPTIONS_ACTION};
        BUTTON_ROW = new ButtonRow(aa, ButtonRow.X_AXIS, ButtonRow.NO_GLUE);
    }

    private void drawHelpGraphics(java.awt.Graphics g, Image icon) {
        Graphics2D g2d = (Graphics2D) g;
        int helpPadding = 20;
        g2d.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f, new float[]{16.0f, 20.0f}, 0.0f));
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, TABLE.getWidth(), TABLE.getHeight());
        g2d.setColor(ThemeMediator.LIGHT_BORDER_COLOR);
        g2d.drawRoundRect(helpPadding, helpPadding, TABLE.getWidth() - helpPadding * 2, TABLE.getHeight() - helpPadding * 2, 6, 6);
        try {
            if ((TABLE.getHeight() - helpPadding * 3) < (icon.getHeight(null))) {
                int newIconDimension = TABLE.getHeight() - helpPadding * 2 - 5;
                if (newIconDimension > 16) {
                    g2d.drawImage(icon, (TABLE.getWidth() - newIconDimension) / 2, (TABLE.getHeight() - newIconDimension) / 2, newIconDimension, newIconDimension, null);
                }
            } else {
                g2d.drawImage(icon, (TABLE.getWidth() - icon.getWidth(null)) / 2, (TABLE.getHeight() - icon.getHeight(null)) / 2, null);
            }
        } catch (Throwable t) {
            //don't stop till you get enough
        }
    }

    // inherit doc comment
    protected JPopupMenu createPopupMenu() {
        if (TABLE.getSelectionModel().isSelectionEmpty())
            return null;
        JPopupMenu menu = new SkinPopupMenu();
        menu.add(new SkinMenuItem(LAUNCH_ACTION));
        menu.add(new SkinMenuItem(LAUNCH_OS_ACTION));
        if (hasExploreAction()) {
            menu.add(new SkinMenuItem(OPEN_IN_FOLDER_ACTION));
        }
        menu.add(new SkinMenuItem(CREATE_TORRENT_ACTION));
        menu.add(createAddToPlaylistSubMenu());
        menu.add(new SkinMenuItem(SEND_TO_FRIEND_ACTION));
        menu.add(new SkinMenuItem(SEND_TO_ITUNES_ACTION));
        menu.addSeparator();
        menu.add(new SkinMenuItem(COPY_PLAYLIST_FILES_TO_FOLDER_ACTION));
        menu.addSeparator();
        menu.add(new SkinMenuItem(DELETE_ACTION));
        int[] rows = TABLE.getSelectedRows();
        boolean dirSelected = false;
        boolean fileSelected = false;
        for (int row : rows) {
            File f = DATA_MODEL.get(row).getFile();
            if (f.isDirectory()) {
                dirSelected = true;
                //				if (IncompleteFileManager.isTorrentFolder(f))
                //					torrentSelected = true;
            } else
                fileSelected = true;
            if (dirSelected && fileSelected)
                break;
        }
        if (dirSelected) {
            DELETE_ACTION.setEnabled(true);
        } else {
            DELETE_ACTION.setEnabled(true);
        }
        menu.addSeparator();
        menu.add(new SkinMenuItem(importToPlaylistAction));
        menu.add(new SkinMenuItem(exportPlaylistAction));
        menu.add(new SkinMenuItem(cleanupPlaylistAction));
        menu.add(new SkinMenuItem(refreshID3TagsAction));
        menu.addSeparator();
        LibraryPlaylistsTableDataLine line = DATA_MODEL.get(rows[0]);
        menu.add(createSearchSubMenu(line));
        return menu;
    }

    @Override
    protected void addListeners() {
        super.addListeners();
        TABLE.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (LibraryUtils.isRefreshKeyEvent(e)) {
                    LibraryMediator.instance().getLibraryPlaylists().refreshSelection();
                }
            }
        });
    }

    private JMenu createSearchSubMenu(LibraryPlaylistsTableDataLine dl) {
        JMenu menu = new SkinMenu(I18n.tr("Search"));
        if (dl != null) {
            File f = dl.getFile();
            String keywords = QueryUtils.createQueryString(f.getName());
            if (keywords.length() > 0)
                menu.add(new SkinMenuItem(new SearchAction(keywords)));
        }
        if (menu.getItemCount() == 0)
            menu.setEnabled(false);
        return menu;
    }

    /**
     * Upgrade getScrolledTablePane to public access.
     */
    public JComponent getScrolledTablePane() {
        return super.getScrolledTablePane();
    }

    /* Don't display anything for this.  The LibraryMediator will do it. */
    protected void updateSplashScreen() {
    }

    /**
     * Sets up drag & drop for the table.
     */
    protected void setupDragAndDrop() {
        TABLE.setDragEnabled(true);
        TABLE.setDropMode(DropMode.INSERT_ROWS);
        TABLE.setTransferHandler(new LibraryPlaylistsTableTransferHandler(this));
    }

    @Override
    protected void setDefaultRenderers() {
        super.setDefaultRenderers();
        TABLE.setDefaultRenderer(PlaylistItemBitRateProperty.class, new PlaylistItemPropertyRenderer());
        TABLE.setDefaultRenderer(PlaylistItemTrackProperty.class, new PlaylistItemPropertyRenderer());
        TABLE.setDefaultRenderer(PlaylistItemStringProperty.class, new PlaylistItemPropertyRenderer());
        TABLE.setDefaultRenderer(PlaylistItemStarProperty.class, new PlaylistItemStarRenderer());
    }

    /**
     * Sets the default editors.
     */
    protected void setDefaultEditors() {
        TableColumnModel model = TABLE.getColumnModel();
        TableColumn tc = model.getColumn(LibraryPlaylistsTableDataLine.STARRED_IDX);
        tc.setCellEditor(new PlaylistItemStarEditor());
        TABLE.addMouseMotionListener(new MouseMotionAdapter() {
            int currentCellColumn = -1;
            int currentCellRow = -1;

            @Override
            public void mouseMoved(MouseEvent e) {
                Point hit = e.getPoint();
                int hitColumn = TABLE.columnAtPoint(hit);
                int hitRow = TABLE.rowAtPoint(hit);
                if (currentCellRow != hitRow || currentCellColumn != hitColumn) {
                    if (TABLE.getCellRenderer(hitRow, hitColumn) instanceof PlaylistItemStarRenderer) {
                        TABLE.editCellAt(hitRow, hitColumn);
                    }
                    currentCellColumn = hitColumn;
                    currentCellRow = hitRow;
                }
            }
        });
        tc = model.getColumn(LibraryPlaylistsTableDataLine.ACTIONS_IDX);
        tc.setCellEditor(new GenericCellEditor(new LibraryActionsRenderer()));
    }

    /**
     * Updates the Table based on the selection of the given table.
     * Perform lookups to remove any store files from the shared folder
     * view and to only display store files in the store view
     */
    void updateTableItems(Playlist playlist) {
        if (playlist == null) {
            return;
        }
        currentPlaylist = playlist;
        List<PlaylistItem> items = currentPlaylist.getItems();
        clearTable();
        for (final PlaylistItem item : items) {
            GUIMediator.safeInvokeLater(() -> addUnsorted(item));
        }
        forceResort();
        GUIMediator.safeInvokeLater(() -> {
            updatePlaylistComponentHeader(null);
            TABLE.requestFocusInWindow();
        });
    }

    @Override
    public JComponent getComponent() {
        mainContainer = new JPanel(new MigLayout("fillx, wrap 1", "[]", "[][fill, grow]"));
        mainContainer.add(new JPanel(), "wrap"); // dummy
        mainContainer.add(super.getComponent(), "grow");
        return mainContainer;
    }

    private void updatePlaylistComponentHeader(String lyrics) {
        JPanel playlistHeaderRow = new JPanel(new MigLayout("insets 10, fillx", "[][grow][]"));
        LibraryCoverArtPanel artWorkLabel = new LibraryCoverArtPanel();
        if (currentPlaylist == null) {
            return;
        }
        if (!currentPlaylist.isStarred()) {
            PlaylistItem playlistItem = currentPlaylist.getItems().get(0);
            if (playlistItem != null) {
                TagsReader tagsReader = new TagsReader(new File(playlistItem.getFilePath()));
                artWorkLabel.setTagsReader(tagsReader).asyncRetrieveImage();
            }
        }
        // left
        Dimension artDimensions = new Dimension(210, 210);
        artWorkLabel.setPreferredSize(artDimensions);
        artWorkLabel.setMinimumSize(artDimensions);
        artWorkLabel.setMaximumSize(artDimensions);
        playlistHeaderRow.add(artWorkLabel);
        // center
        JPanel centerContainer = new JPanel(new MigLayout("fillx, wrap 1"));
        JLabel playlistSectionTitle = new JLabel(I18n.tr("Playlist"));
        // Replace this for a text edit or show a dialog to rename it
        JLabel playlistTitle = new JLabel(currentPlaylist.getName());
        Font font = new Font("Helvetica", Font.BOLD, 30);
        playlistTitle.setFont(font);
        JLabel duration = new JLabel(LibraryUtils.getPlaylistDurationInDDHHMMSS(currentPlaylist) + ", " + currentPlaylist.getItems().size() + " " + I18n.tr("tracks"));
        centerContainer.add(playlistSectionTitle, "wrap");
        centerContainer.add(playlistTitle, "wrap");
        centerContainer.add(duration);
        playlistHeaderRow.add(centerContainer, "alignx left, aligny top, growx, pushx");
        if (lyrics != null && !lyrics.isEmpty()) {
            JPanel lyricsPanel = createLyricsPanel(lyrics);
            playlistHeaderRow.add(lyricsPanel, "aligny top");
        }
        mainContainer.removeAll();
        mainContainer.add(playlistHeaderRow, "wrap, growx");
        mainContainer.add(super.getComponent(), "grow");
        mainContainer.invalidate();
        mainContainer.repaint();
        mainContainer.updateUI();
    }

    private JPanel createLyricsPanel(String lyrics) {
        int[] sel = TABLE.getSelectedRows();
        String songTitle = "";
        if (sel.length == 1) {
            PlaylistItem playlistItem = currentPlaylist.getItems().get(sel[0]);
            songTitle = playlistItem.getTrackTitle() + " ";
        }
        JPanel lyricsPanel = new JPanel(new MigLayout("fillx, wrap 1, insets 0", "[]"));
        lyricsPanel.add(new JLabel(songTitle + I18n.tr("Lyrics")), "wrap");
        JTextArea lyricsTextArea = new JTextArea(lyrics);
        lyricsTextArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(lyricsTextArea);
        Dimension d = new Dimension(500, 210);
        scrollPane.setPreferredSize(d);
        scrollPane.setMaximumSize(d);
        scrollPane.setMaximumSize(d);
        lyricsPanel.add(scrollPane, "growx");
        return lyricsPanel;
    }

    /**
     * Returns the <tt>File</tt> stored at the specified row in the list.
     *
     * @param row the row of the desired <tt>File</tt> instance in the
     *            list
     * @return a <tt>File</tt> instance associated with the specified row
     * in the table
     */
    private File getFile(int row) {
        return DATA_MODEL.getFile(row);
    }

    /**
     * Accessor for the table that this class wraps.
     *
     * @return The <tt>JTable</tt> instance used by the library.
     */
    JTable getTable() {
        return TABLE;
    }

    private LibraryPlaylistsTableDataLine[] getSelectedLibraryLines() {
        int[] selected = TABLE.getSelectedRows();
        LibraryPlaylistsTableDataLine[] lines = new LibraryPlaylistsTableDataLine[selected.length];
        for (int i = 0; i < selected.length; i++)
            lines[i] = DATA_MODEL.get(selected[i]);
        return lines;
    }

    /**
     * Delete selected items from a playlist (not from disk)
     */
    public void removeSelection() {
        LibraryPlaylistsTableDataLine[] lines = getSelectedLibraryLines();
        if (currentPlaylist != null && currentPlaylist.getId() == LibraryDatabase.STARRED_PLAYLIST_ID) {
            for (LibraryPlaylistsTableDataLine line : lines) {
                PlaylistItem playlistItem = line.getInitializeObject();
                playlistItem.setStarred(false);
                playlistItem.save(true);
            }
            LibraryMediator.instance().getLibraryExplorer().refreshSelection();
        } else {
            for (LibraryPlaylistsTableDataLine line : lines) {
                PlaylistItem playlistItem = line.getInitializeObject();
                playlistItem.delete();
            }
            LibraryMediator.instance().getLibraryPlaylists().reselectPlaylist();
            clearSelection();
        }
        super.removeSelection();
    }

    public void handleActionKey() {
        playMedia();
    }

    private void playMedia() {
        LibraryPlaylistsTableDataLine line = DATA_MODEL.get(TABLE.getSelectedRow());
        if (line == null || line.getPlayListItem() == null) {
            return;
        }
        MediaSource mediaSource = new MediaSource(line.getPlayListItem());
        if (MediaPlayer.isPlayableFile(mediaSource)) {
            MediaPlayer.instance().asyncLoadMedia(mediaSource, true, currentPlaylist, getFilesView());
        }
    }

    /**
     * Launches the associated applications for each selected file
     * in the library if it can.
     */
    private void launch(boolean playMedia) {
        int[] rows = TABLE.getSelectedRows();
        if (rows.length == 0) {
            return;
        }
        File selectedFile = DATA_MODEL.getFile(rows[0]);
        if (OSUtils.isWindows()) {
            if (selectedFile.isDirectory()) {
                GUIMediator.launchExplorer(selectedFile);
                return;
            } else if (!MediaPlayer.isPlayableFile(selectedFile)) {
                GUIMediator.launchFile(selectedFile);
                return;
            }
        }
        LaunchableProvider[] providers = new LaunchableProvider[rows.length];
        for (int i = 0; i < rows.length; i++) {
            providers[i] = new FileProvider(DATA_MODEL.getFile(rows[i]));
        }
        if (!playMedia) {
            MediaPlayer.instance().stop();
        }
        if (playMedia) {
            GUILauncher.launch(providers);
        } else {
            GUIMediator.launchFile(selectedFile);
        }
    }

    /**
     * Handles the selection rows in the library window,
     * enabling or disabling buttons and chat menu items depending on
     * the values in the selected rows.
     *
     * @param row the index of the first row that is selected
     */
    public void handleSelection(int row) {
        int[] sel = TABLE.getSelectedRows();
        if (sel.length == 0) {
            handleNoSelection();
            return;
        }
        File selectedFile = getFile(sel[0]);
        //  always turn on Launch, Delete, Magnet Lookup, Bitzi Lookup
        LAUNCH_ACTION.setEnabled(true);
        LAUNCH_OS_ACTION.setEnabled(true);
        DELETE_ACTION.setEnabled(true);
        SEND_TO_ITUNES_ACTION.setEnabled(true);
        if (!selectedFile.getName().endsWith(".torrent")) {
            CREATE_TORRENT_ACTION.setEnabled(sel.length == 1);
        }
        SEND_TO_FRIEND_ACTION.setEnabled(sel.length == 1);
        if (sel.length == 1 && selectedFile.isFile() && selectedFile.getParentFile() != null) {
            OPEN_IN_FOLDER_ACTION.setEnabled(true);
        } else {
            OPEN_IN_FOLDER_ACTION.setEnabled(false);
        }
        if (sel.length == 1) {
            TagsReader tagsReader = new TagsReader(selectedFile);
            LibraryMediator.instance().getLibraryCoverArtPanel().setTagsReader(tagsReader).asyncRetrieveImage();
            LibraryUtils.asyncParseLyrics(tagsReader, new LibraryUtils.OnLyricsParsedUICallback() {
                @Override
                public void run() {
                    updatePlaylistComponentHeader(getLyrics());
                    TABLE.requestFocusInWindow();
                }
            });
        }
        TABLE.requestFocusInWindow();
    }

    /**
     * Handles the deselection of all rows in the library table,
     * disabling all necessary buttons and menu items.
     */
    public void handleNoSelection() {
        LAUNCH_ACTION.setEnabled(false);
        LAUNCH_OS_ACTION.setEnabled(false);
        OPEN_IN_FOLDER_ACTION.setEnabled(false);
        SEND_TO_FRIEND_ACTION.setEnabled(false);
        CREATE_TORRENT_ACTION.setEnabled(false);
        DELETE_ACTION.setEnabled(false);
        SEND_TO_ITUNES_ACTION.setEnabled(false);
        // TODO: Update UI and hide lyrics
    }

    private boolean hasExploreAction() {
        return OSUtils.isWindows() || OSUtils.isMacOSX();
    }
    ///////////////////////////////////////////////////////
    //  ACTIONS
    ///////////////////////////////////////////////////////

    @Override
    public List<MediaSource> getFilesView() {
        int size = DATA_MODEL.getRowCount();
        List<MediaSource> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            try {
                result.add(new MediaSource(DATA_MODEL.get(i).getPlayListItem()));
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }
        return result;
    }

    @Override
    protected void sortAndMaintainSelection(int columnToSort) {
        super.sortAndMaintainSelection(columnToSort);
        resetAudioPlayerFileView();
    }

    private void resetAudioPlayerFileView() {
        Playlist playlist = MediaPlayer.instance().getCurrentPlaylist();
        if (playlist != null && playlist.equals(currentPlaylist)) {
            if (MediaPlayer.instance().getPlaylistFilesView() != null) {
                MediaPlayer.instance().setPlaylistFilesView(getFilesView());
            }
        }
    }

    @Override
    protected MediaSource createMediaSource(LibraryPlaylistsTableDataLine line) {
        return new MediaSource(line.getInitializeObject());
    }

    private static class FileProvider implements LaunchableProvider {
        private final File _file;

        FileProvider(File file) {
            _file = file;
        }

        public File getFile() {
            return _file;
        }
    }

    private final class LaunchAction extends AbstractAction {
        /**
         *
         */
        private static final long serialVersionUID = 949208465372392591L;

        LaunchAction() {
            putValue(Action.NAME, I18n.tr("Launch"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Launch Selected Files"));
            putValue(LimeAction.ICON_NAME, "LIBRARY_LAUNCH");
        }

        public void actionPerformed(ActionEvent ae) {
            launch(true);
        }
    }

    private final class LaunchOSAction extends AbstractAction {
        /**
         *
         */
        private static final long serialVersionUID = 949208465372392592L;

        LaunchOSAction() {
            String os = "OS";
            if (OSUtils.isWindows()) {
                os = "Windows";
            } else if (OSUtils.isMacOSX()) {
                os = "Mac";
            } else if (OSUtils.isLinux()) {
                os = "Linux";
            }
            putValue(Action.NAME, I18n.tr("Launch in") + " " + os);
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Launch Selected Files in") + " " + os);
            putValue(LimeAction.ICON_NAME, "LIBRARY_LAUNCH");
        }

        public void actionPerformed(ActionEvent ae) {
            launch(false);
        }
    }

    private final class OpenInFolderAction extends AbstractAction {
        /**
         *
         */
        private static final long serialVersionUID = 1693310684299300459L;

        OpenInFolderAction() {
            putValue(Action.NAME, I18n.tr("Explore"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Explore"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Open Folder Containing the File"));
            putValue(LimeAction.ICON_NAME, "LIBRARY_EXPLORE");
        }

        public void actionPerformed(ActionEvent ae) {
            int[] sel = TABLE.getSelectedRows();
            if (sel.length == 0) {
                return;
            }
            File selectedFile = getFile(sel[0]);
            if (selectedFile.isFile() && selectedFile.getParentFile() != null) {
                GUIMediator.launchExplorer(selectedFile);
            }
        }
    }

    private final class CreateTorrentAction extends AbstractAction {
        private static final long serialVersionUID = 1898917632888388860L;

        CreateTorrentAction() {
            super(I18n.tr("Create New Torrent"));
            putValue(Action.LONG_DESCRIPTION, I18n.tr("Create a new .torrent file"));
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            File selectedFile = DATA_MODEL.getFile(TABLE.getSelectedRow());
            //can't create torrents out of empty folders.
            //noinspection ConstantConditions
            if (selectedFile.isDirectory() && selectedFile.listFiles() != null && selectedFile.listFiles().length == 0) {
                JOptionPane.showMessageDialog(null, I18n.tr("The folder you selected is empty."), I18n.tr("Invalid Folder"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            //can't create torrents if the folder/file can't be read
            if (!selectedFile.canRead()) {
                JOptionPane.showMessageDialog(null, I18n.tr("Error: You can't read on that file/folder."), I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            CreateTorrentDialog dlg = new CreateTorrentDialog(GUIMediator.getAppFrame());
            dlg.setChosenContent(selectedFile, selectedFile.isFile() ? JFileChooser.FILES_ONLY : JFileChooser.DIRECTORIES_ONLY);
            dlg.setVisible(true);
        }
    }

    private class SendAudioFilesToiTunes extends AbstractAction {
        private static final long serialVersionUID = 4726989286129406765L;

        SendAudioFilesToiTunes() {
            putValue(Action.NAME, I18n.tr("Send to iTunes"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Send audio files to iTunes"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            int[] rows = TABLE.getSelectedRows();
            List<File> files = new ArrayList<>();
            for (int index : rows) {
                File file = DATA_MODEL.getFile(index);
                files.add(file);
                //iTunesMediator.instance().scanForSongs(file);
            }
            if (!files.isEmpty()) {
                iTunesMediator.instance().scanForSongs(files.toArray(new File[0]));
            }
        }
    }

    private final class RemoveFromPlaylistAction extends AbstractAction {
        RemoveFromPlaylistAction() {
            putValue(Action.NAME, I18n.tr("Delete"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Delete Selected Files from this playlist"));
            putValue(LimeAction.ICON_NAME, "LIBRARY_DELETE");
        }

        public void actionPerformed(ActionEvent ae) {
            // REMOVE_LISTENER is this, see LibraryPlaylistsTableMediator.this.removeSelection
            REMOVE_LISTENER.actionPerformed(ae);
        }
    }

    private final class ImportToPlaylistAction extends AbstractAction {
        private static final long serialVersionUID = -9099898749358019734L;

        ImportToPlaylistAction() {
            putValue(Action.NAME, I18n.tr("Import .m3u to Playlist"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Import a .m3u file into the selected playlist"));
            putValue(LimeAction.ICON_NAME, "PLAYLIST_IMPORT_TO");
        }

        public void actionPerformed(ActionEvent e) {
            LibraryMediator.instance().getLibraryPlaylists().importM3U(currentPlaylist);
        }
    }

    private final class ExportPlaylistAction extends AbstractAction {
        private static final long serialVersionUID = 6149822357662730490L;

        ExportPlaylistAction() {
            putValue(Action.NAME, I18n.tr("Export Playlist to .m3u"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Export this playlist into a .m3u file"));
            putValue(LimeAction.ICON_NAME, "PLAYLIST_IMPORT_NEW");
        }

        public void actionPerformed(ActionEvent e) {
            LibraryMediator.instance().getLibraryPlaylists().exportM3U(currentPlaylist);
        }
    }

    private final class CleanupPlaylistAction extends AbstractAction {
        private static final long serialVersionUID = 8400749433148927596L;

        CleanupPlaylistAction() {
            putValue(Action.NAME, I18n.tr("Cleanup playlist"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Remove the deleted items"));
            putValue(LimeAction.ICON_NAME, "PLAYLIST_CLEANUP");
        }

        public void actionPerformed(ActionEvent e) {
            LibraryUtils.cleanup(currentPlaylist);
            LibraryMediator.instance().getLibraryPlaylists().refreshSelection();
        }
    }

    private final class RefreshID3TagsAction extends AbstractAction {
        private static final long serialVersionUID = 758150680592618044L;

        RefreshID3TagsAction() {
            putValue(Action.NAME, I18n.tr("Refresh Audio Properties"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Refresh the audio properties based on ID3 tags"));
            putValue(LimeAction.ICON_NAME, "PLAYLIST_REFRESHID3TAGS");
        }

        public void actionPerformed(ActionEvent e) {
            LibraryPlaylistsTableDataLine[] lines = getSelectedLibraryLines();
            List<PlaylistItem> items = new ArrayList<>(lines.length);
            for (LibraryPlaylistsTableDataLine line : lines) {
                items.add(line.getInitializeObject());
            }
            LibraryUtils.refreshID3Tags(currentPlaylist, items);
        }
    }
}
