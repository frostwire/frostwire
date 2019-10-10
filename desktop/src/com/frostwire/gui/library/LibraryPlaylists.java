/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Jose Molina (votaguz)
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

package com.frostwire.gui.library;

import com.frostwire.alexandria.Library;
import com.frostwire.alexandria.Playlist;
import com.frostwire.alexandria.PlaylistItem;
import com.frostwire.alexandria.db.LibraryDatabase;
import com.frostwire.gui.library.SortedListModel.SortOrder;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.theme.SkinMenuItem;
import com.frostwire.gui.theme.SkinPopupMenu;
import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.gui.actions.LimeAction;
import com.limegroup.gnutella.gui.options.ConfigureOptionsAction;
import com.limegroup.gnutella.gui.options.OptionsConstructor;
import com.limegroup.gnutella.gui.tables.DefaultMouseListener;
import com.limegroup.gnutella.gui.tables.MouseObserver;
import com.limegroup.gnutella.gui.tables.TableSettings;
import com.limegroup.gnutella.gui.util.BackgroundExecutorService;
import com.limegroup.gnutella.settings.QuestionsHandler;
import org.limewire.util.CommonUtils;
import org.limewire.util.FileUtils;
import org.limewire.util.OSUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.plaf.InsetsUIResource;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 * @author votaguz
 */
public class LibraryPlaylists extends AbstractLibraryListPanel {
    private final byte REFRESH_ACTION = 0;
    private final byte REFRESH_ID3_TAGS_ACTION = 1;
    private final byte DELETE_ACTION = 2;
    private final byte CLEANUP_PLAYLIST_ACTION = 3;
    @SuppressWarnings("FieldCanBeLocal")
    private final byte RENAME_ACTION = 4;
    private final byte IMPORT_TO_PLAYLIST_ACTION = 5;
    private final byte IMPORT_TO_NEW_PLAYLIST_ACTION = 6;
    private final byte CONFIGURE_OPTIONS_ACTION = 0xa;
    private DefaultListModel<Object> model;
    private int selectedIndexToRename;
    private LibraryPlaylistsListCell newPlaylistCell;
    private LibraryPlaylistsListCell starredPlaylistCell;
    private ActionListener selectedPlaylistAction;
    private ListSelectionListener listSelectionListener;
    private JList<Object> list;
    private JTextField textName;
    private Action[] actions;
    private final List<Playlist> importingPlaylists;

    LibraryPlaylists() {
        setupUI();
        importingPlaylists = new ArrayList<>();
    }

    void addPlaylist(Playlist playlist) {
        LibraryPlaylistsListCell cell = new LibraryPlaylistsListCell(null, null, GUIMediator.getThemeImage("playlist"), playlist, selectedPlaylistAction);
        model.addElement(cell);
    }

    public void clearSelection() {
        list.clearSelection();
    }

    Playlist getSelectedPlaylist() {
        LibraryPlaylistsListCell cell = (LibraryPlaylistsListCell) list.getSelectedValue();
        return cell != null ? cell.getPlaylist() : null;
    }

    private void setupUI() {
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(177, 94));
        GUIMediator.addRefreshListener(this);
        initPopupMenuActions();
        LibraryUtils.getExecutor().execute(() -> {
            setupModel();
            GUIMediator.safeInvokeLater(() -> {
                setupList();
                JScrollPane _scrollPane = new JScrollPane(list);
                add(_scrollPane);
            });
        });
    }

    private void initPopupMenuActions() {
        actions = new Action[]{
                new RefreshAction(), // 0
                new RefreshID3TagsAction(), // 1
                new DeleteAction(), // 2
                new CleanupPlaylistAction(), // 3
                new StartRenamingPlaylistAction(), // 4
                new ImportToPlaylistAction(), // 5
                new ImportToNewPlaylistAction(), // 6
                new CopyPlaylistFilesAction(), // 7
                new ExportPlaylistAction(), // 8
                new ExportToiTunesAction(), // 9
                new ConfigureOptionsAction(OptionsConstructor.LIBRARY_KEY, I18n.tr("Configure Options"), I18n.tr("You can configure the FrostWire\'s Options.")), // 0xa
        };
    }

    private SkinPopupMenu getStarredPlaylistPopupMenu(Playlist playlist) {
        SkinPopupMenu starredPlaylistPopupMenu = new SkinPopupMenu();
        final List<PlaylistItem> items = playlist.getItems();
        boolean playlistEmpty = items == null || items.size() == 0;
        if (!playlistEmpty) {
            starredPlaylistPopupMenu.add(new SkinMenuItem(actions[CLEANUP_PLAYLIST_ACTION]));
        }
        starredPlaylistPopupMenu.add(new SkinMenuItem(actions[REFRESH_ACTION]));
        if (!playlistEmpty) {
            starredPlaylistPopupMenu.add(new SkinMenuItem(actions[REFRESH_ID3_TAGS_ACTION]));
        }
        starredPlaylistPopupMenu.addSeparator();
        starredPlaylistPopupMenu.add(new SkinMenuItem(actions[IMPORT_TO_PLAYLIST_ACTION]));
        addExportActionsToPopupMenu(starredPlaylistPopupMenu, playlistEmpty);
        starredPlaylistPopupMenu.addSeparator();
        starredPlaylistPopupMenu.add(new SkinMenuItem(actions[CONFIGURE_OPTIONS_ACTION]));
        return starredPlaylistPopupMenu;
    }

    private SkinPopupMenu getPlaylistPopupMenu(Playlist playlist) {
        SkinPopupMenu playlistPopup = new SkinPopupMenu();
        final List<PlaylistItem> items = playlist.getItems();
        boolean playlistEmpty = items == null || items.size() == 0;
        playlistPopup.add(new SkinMenuItem(actions[REFRESH_ACTION]));
        if (!playlistEmpty) {
            playlistPopup.add(new SkinMenuItem(actions[REFRESH_ID3_TAGS_ACTION]));
        }
        playlistPopup.addSeparator();
        playlistPopup.add(new SkinMenuItem(actions[RENAME_ACTION]));
        playlistPopup.add(new SkinMenuItem(actions[DELETE_ACTION]));
        if (!playlistEmpty) {
            playlistPopup.add(new SkinMenuItem(actions[CLEANUP_PLAYLIST_ACTION]));
        }
        playlistPopup.addSeparator();
        playlistPopup.add(new SkinMenuItem(actions[IMPORT_TO_PLAYLIST_ACTION]));
        playlistPopup.add(new SkinMenuItem(actions[IMPORT_TO_NEW_PLAYLIST_ACTION]));
        addExportActionsToPopupMenu(playlistPopup, playlistEmpty);
        playlistPopup.addSeparator();
        playlistPopup.add(new SkinMenuItem(actions[CONFIGURE_OPTIONS_ACTION]));
        return playlistPopup;
    }

    private SkinPopupMenu getNewPlaylistPopupMenu() {
        SkinPopupMenu popupMenu = new SkinPopupMenu();
        popupMenu.add(new SkinMenuItem(actions[IMPORT_TO_NEW_PLAYLIST_ACTION]));
        popupMenu.add(new SkinMenuItem(actions[CONFIGURE_OPTIONS_ACTION]));
        return popupMenu;
    }

    private void addExportActionsToPopupMenu(SkinPopupMenu playlistPopup, boolean playlistEmpty) {
        if (!playlistEmpty) {
            playlistPopup.addSeparator();
            byte COPY_PLAYLIST_FILES_ACTION = 7;
            playlistPopup.add(new SkinMenuItem(actions[COPY_PLAYLIST_FILES_ACTION]));
            byte EXPORT_PLAYLIST_ACTION = 8;
            playlistPopup.add(new SkinMenuItem(actions[EXPORT_PLAYLIST_ACTION]));
            playlistPopup.addSeparator();
            if (OSUtils.isWindows() || OSUtils.isMacOSX()) {
                byte EXPORT_TO_ITUNES_ACTION = 9;
                playlistPopup.add(new SkinMenuItem(actions[EXPORT_TO_ITUNES_ACTION]));
            }
        }
    }

    private void setupModel() {
        model = new DefaultListModel<>();
        newPlaylistCell = new LibraryPlaylistsListCell(I18n.tr("New Playlist"), I18n.tr("Creates a new Playlist"), GUIMediator.getThemeImage("playlist_plus"), null, null);
        Library library = LibraryMediator.getLibrary();
        selectedPlaylistAction = new SelectedPlaylistActionListener();
        Playlist starredPlaylist = LibraryMediator.getLibrary().getStarredPlaylist();
        starredPlaylistCell = new LibraryPlaylistsListCell(I18n.tr("Starred"), I18n.tr("Show all starred items"),
                GUIMediator.getThemeImage("star_on"), starredPlaylist, selectedPlaylistAction);
        model.addElement(newPlaylistCell);
        model.addElement(starredPlaylistCell);
        for (Playlist playlist : library.getPlaylists()) {
            LibraryPlaylistsListCell cell = new LibraryPlaylistsListCell(null, null, GUIMediator.getThemeImage("playlist"), playlist, selectedPlaylistAction);
            model.addElement(cell);
        }
    }

    private void setupList() {
        LibraryPlaylistsMouseObserver _listMouseObserver = new LibraryPlaylistsMouseObserver();
        listSelectionListener = new LibraryPlaylistsSelectionListener();
        SortedListModel sortedModel = new SortedListModel(model, SortOrder.ASCENDING, (Comparator<LibraryPlaylistsListCell>) (o1, o2) -> {
            if (o1 == newPlaylistCell || o1 == starredPlaylistCell) {
                return -1;
            }
            if (o2 == newPlaylistCell || o2 == starredPlaylistCell) {
                return 1;
            }
            return o1.getText().compareTo(o2.getText());
        });
        list = new LibraryIconList(sortedModel);
        list.setFixedCellHeight(TableSettings.DEFAULT_TABLE_ROW_HEIGHT.getValue());
        list.setCellRenderer(new LibraryPlaylistsCellRenderer());
        list.addMouseListener(new DefaultMouseListener(_listMouseObserver));
        list.addListSelectionListener(listSelectionListener);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setLayoutOrientation(JList.VERTICAL);
        list.setPrototypeCellValue(new LibraryPlaylistsListCell("test", "", GUIMediator.getThemeImage("playlist"), null, null));
        list.setVisibleRowCount(-1);
        list.setDragEnabled(true);
        list.setTransferHandler(new LibraryPlaylistsTransferHandler(list));
        ToolTipManager.sharedInstance().registerComponent(list);
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                list_keyPressed(e);
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() > 1) {
                    actionStartRename();
                }
            }
        });
        textName = new JTextField();
        ThemeMediator.fixKeyStrokes(textName);
        UIDefaults defaults = new UIDefaults();
        defaults.put("TextField.contentMargins", new InsetsUIResource(0, 4, 0, 4));
        textName.putClientProperty("Nimbus.Overrides.InheritDefaults", Boolean.TRUE);
        textName.putClientProperty("Nimbus.Overrides", defaults);
        textName.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                textName_keyPressed(e);
            }
        });
        textName.setVisible(false);
        list.add(textName);
    }

    private void list_keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_ESCAPE) {
            cancelEdit();
        } else if (key == KeyEvent.VK_ENTER) {
            if (OSUtils.isMacOSX()) {
                actionStartRename();
            }
        } else if (key == KeyEvent.VK_F2) {
            if (!OSUtils.isMacOSX()) {
                actionStartRename();
            }
        } else if (key == KeyEvent.VK_DELETE || (OSUtils.isMacOSX() && key == KeyEvent.VK_BACK_SPACE)) {
            actions[DELETE_ACTION].actionPerformed(null);
        }
        if (LibraryUtils.isRefreshKeyEvent(e)) {
            refreshSelection();
        }
    }

    private void textName_keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        if (selectedIndexToRename != -1 && key == KeyEvent.VK_ENTER) {
            renameSelectedItem();
        } else if (selectedIndexToRename == -1 && key == KeyEvent.VK_ENTER) {
            createNewPlaylist();
        } else if (key == KeyEvent.VK_ESCAPE) {
            textName.setVisible(false);
        }
    }

    void refreshSelection() {
        LibraryPlaylistsListCell cell = (LibraryPlaylistsListCell) list.getSelectedValue();
        if (cell == null) {
            // handle special case
            if (model.getSize() == 2 && MediaPlayer.instance().getCurrentPlaylist() == null) {
                list.setSelectedIndex(1);
            }
            return;
        }
        Playlist playlist = cell.getPlaylist();
        if (playlist != null) {
            if (playlist.getId() == LibraryDatabase.STARRED_PLAYLIST_ID) {
                playlist = LibraryMediator.getLibrary().getStarredPlaylist();
            } else {
                playlist.refresh();
            }
            LibraryMediator.instance().updateTableItems(playlist);
            LibraryMediator.instance().getLibrarySearch().setStatus("");
        }
        executePendingRunnables();
    }

    private void actionStartRename() {
        cancelEdit();
        int index = list.getSelectedIndex();
        Playlist playlist = ((LibraryPlaylistsListCell) list.getSelectedValue()).getPlaylist();
        if (playlist != null && playlist.getId() == LibraryDatabase.STARRED_PLAYLIST_ID) {
            return;
        }
        if (index != -1) {
            startEdit(index);
        }
    }

    private void startEdit(int index) {
        if (index < 0) {
            return;
        }
        LibraryPlaylistsListCell cell = (LibraryPlaylistsListCell) model.getElementAt(index);
        selectedIndexToRename = cell.getPlaylist() != null ? index : -1;
        String text = cell.getText();
        Rectangle rect = list.getUI().getCellBounds(list, index, index);
        Dimension size = rect.getSize();
        Point location = rect.getLocation();
        textName.setSize(size);
        textName.setLocation(location);
        textName.setText(text);
        textName.setSelectionStart(0);
        textName.setSelectionEnd(text.length());
        textName.setVisible(true);
        textName.requestFocusInWindow();
        textName.requestFocus();
    }

    void selectPlaylist(Playlist playlist) {
        Object selectedValue = list.getSelectedValue();
        if (selectedValue != null && ((LibraryPlaylistsListCell) selectedValue).getPlaylist() != null && ((LibraryPlaylistsListCell) selectedValue).getPlaylist().equals(playlist)) {
            // already selected
            try {
                listSelectionListener.valueChanged(null);
            } catch (Exception e) {
                System.out.println();
            }
            return;
        }
        int size = model.getSize();
        for (int i = 0; i < size; i++) {
            try {
                LibraryPlaylistsListCell cell = (LibraryPlaylistsListCell) model.get(i);
                if (cell.getPlaylist() != null && cell.getPlaylist().equals(playlist)) {
                    list.setSelectedValue(cell, true);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void renameSelectedItem() {
        if (!textName.isVisible() || textName.getText().trim().length() == 0) {
            return;
        }
        Playlist selectedPlaylist = getSelectedPlaylist();
        selectedPlaylist.setName(textName.getText().trim());
        selectedPlaylist.save();
        list.repaint();
        textName.setVisible(false);
        LibraryPlaylistsTableMediator.instance().updateTableItems(selectedPlaylist);
    }

    private void createNewPlaylist() {
        if (!textName.isVisible()) {
            return;
        }
        String name = textName.getText();
        Library library = LibraryMediator.getLibrary();
        Playlist playlist = library.newPlaylist(name, name);
        playlist.save();
        LibraryPlaylistsListCell cell = new LibraryPlaylistsListCell(null, null, GUIMediator.getThemeImage("playlist"), playlist, selectedPlaylistAction);
        model.addElement(cell);
        list.setSelectedValue(cell, true);
        textName.setVisible(false);
    }

    private void cancelEdit() {
        selectedIndexToRename = -1;
        textName.setVisible(false);
    }
    //// handle m3u import/export

    /**
     * Loads a playlist.
     */
    void importM3U(Playlist playlist) {
        File parentFile = FileChooserHandler.getLastInputDirectory();
        if (parentFile == null) {
            parentFile = CommonUtils.getCurrentDirectory();
        }
        final File selFile = FileChooserHandler.getInputFile(GUIMediator.getAppFrame(), I18n.tr("Open Playlist (.m3u)"), parentFile, new PlaylistListFileFilter());
        // nothing selected? exit.
        if (selFile == null || !selFile.isFile()) {
            return;
        }
        String path = selFile.getPath();
        try {
            path = FileUtils.getCanonicalPath(selFile);
        } catch (IOException ignored) {
            //LOG.warn("unable to get canonical path for file: " + selFile, ignored);
        }
        // create a new thread off of the event queue to process reading the files from
        //  disk
        loadM3U(playlist, path);
    }

    /**
     * Performs the actual reading of the PlayList and generation of the PlayListItems from
     * the PlayList. Once we have done the heavy weight construction of the PlayListItem
     * list, the list is handed to the swing event queue to process adding the files to
     * the actual table model
     *
     * @param path - path of file to open
     */
    private void loadM3U(final Playlist playlist, final String path) {
        BackgroundExecutorService.schedule(() -> {
            try {
                final List<File> files = M3UPlaylist.load(path);
                if (playlist != null) {
                    LibraryUtils.asyncAddToPlaylist(playlist, files.toArray(new File[0]));
                } else {
                    LibraryUtils.createNewPlaylist(files.toArray(new File[0]));
                }
            } catch (Exception e) {
                e.printStackTrace();
                GUIMediator.safeInvokeLater(() -> GUIMediator.showError("Unable to load playlist"));
            }
        });
    }

    /**
     * Saves a playlist.
     */
    void exportM3U(Playlist playlist) {
        if (playlist == null) {
            return;
        }
        String suggestedName = CommonUtils.convertFileName(playlist.getName());
        // get the user to select a new one.... avoid FrostWire installation folder.
        File suggested;
        File suggestedDirectory = FileChooserHandler.getLastInputDirectory();
        if (suggestedDirectory.equals(CommonUtils.getCurrentDirectory())) {
            suggestedDirectory = new File(CommonUtils.getUserHomeDir(), "Desktop");
        }
        suggested = new File(suggestedDirectory, suggestedName + ".m3u");
        File selFile = FileChooserHandler.getSaveAsFile(I18n.tr("Save Playlist As"), suggested, new PlaylistListFileFilter());
        // didn't select a file?  nothing we can do.
        if (selFile == null) {
            return;
        }
        // if the file already exists and not the one just opened, ask if it should be overwritten.
        if (selFile.exists()) {
            DialogOption choice = GUIMediator.showYesNoMessage(I18n.tr("Warning: a file with the name {0} already exists in the folder. Overwrite this file?", selFile.getName()), QuestionsHandler.PLAYLIST_OVERWRITE_OK, DialogOption.NO);
            if (choice != DialogOption.YES)
                return;
        }
        String path = selFile.getPath();
        try {
            path = FileUtils.getCanonicalPath(selFile);
        } catch (IOException ignored) {
            //LOG.warn("unable to get canonical path for file: " + selFile, ignored);
        }
        // force m3u on the end.
        if (!path.toLowerCase().endsWith(".m3u"))
            path += ".m3u";
        // create a new thread to handle saving the playlist to disk
        saveM3U(playlist, path);
    }

    /**
     * Handles actually copying and writing the playlist to disk.
     *
     * @param path - file location to save the list to
     */
    private void saveM3U(final Playlist playlist, final String path) {
        BackgroundExecutorService.schedule(() -> {
            try {
                List<File> files = new ArrayList<>();
                List<PlaylistItem> items = playlist.getItems();
                for (PlaylistItem item : items) {
                    File file = new File(item.getFilePath());
                    if (file.exists()) {
                        files.add(file);
                    }
                }
                M3UPlaylist.save(path, files);
            } catch (Exception e) {
                e.printStackTrace();
                GUIMediator.safeInvokeLater(() -> GUIMediator.showError("Unable to save playlist"));
            }
        });
    }

    void reselectPlaylist() {
        listSelectionListener.valueChanged(null);
    }

    @Override
    public void refresh() {
        if (list != null) {
            list.repaint();
        }
    }

    void markBeginImport(Playlist playlist) {
        try {
            if (!importingPlaylists.contains(playlist)) {
                importingPlaylists.add(playlist);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void markEndImport(Playlist playlist) {
        try {
            importingPlaylists.remove(playlist);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    boolean isPlaylistImporting(Playlist playlist) {
        try {
            return importingPlaylists.contains(playlist);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static class LibraryPlaylistsListCell {
        private final String _text;
        private final String _description;
        private final Icon _icon;
        private final Playlist _playlist;
        private final ActionListener _action;

        LibraryPlaylistsListCell(String text, String description, Icon icon, Playlist playlist, ActionListener action) {
            _text = text;
            _description = description;
            _icon = icon;
            _playlist = playlist;
            _action = action;
        }

        String getText() {
            if (_text != null) {
                return _text;
            } else if (_playlist != null && _playlist.getName() != null) {
                return _playlist.getName();
            } else {
                return "";
            }
        }

        String getDescription() {
            if (_description != null) {
                return _description;
            } else if (_playlist != null && _playlist.getDescription() != null) {
                return _playlist.getDescription();
            } else {
                return "";
            }
        }

        Icon getIcon() {
            return _icon;
        }

        public Playlist getPlaylist() {
            return _playlist;
        }

        ActionListener getAction() {
            return _action;
        }
    }

    private static class PlaylistListFileFilter extends FileFilter {
        public boolean accept(File f) {
            return f.isDirectory() || f.getName().toLowerCase().endsWith("m3u");
        }

        public String getDescription() {
            return I18n.tr("Playlist Files (*.m3u)");
        }
    }

    public final static class CopyPlaylistFilesAction extends AbstractAction {
        CopyPlaylistFilesAction() {
            putValue(Action.NAME, I18n.tr("Export playlist files to folder"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Copy all playlist files to a folder of your choosing"));
            putValue(LimeAction.ICON_NAME, "PLAYLIST_IMPORT_NEW");
        }

        public void actionPerformed(ActionEvent e) {
            copyPlaylistFilesToFolder(LibraryMediator.instance().getSelectedPlaylist());
        }

        private void copyPlaylistFilesToFolder(Playlist playlist) {
            if (playlist == null || playlist.getItems().isEmpty()) {
                return;
            }
            File suggestedDirectory = FileChooserHandler.getLastInputDirectory();
            if (suggestedDirectory.equals(CommonUtils.getCurrentDirectory())) {
                suggestedDirectory = new File(CommonUtils.getUserHomeDir(), "Desktop");
            }
            final File selFolder = FileChooserHandler.getSaveAsDir(GUIMediator.getAppFrame(), I18n.tr("Where do you want the playlist files copied to?"), suggestedDirectory);
            if (selFolder == null) {
                return;
            }
            //let's make a copy of the list in case the playlist will be modified during the copying.
            final List<PlaylistItem> playlistItems = new ArrayList<>(playlist.getItems());
            BackgroundExecutorService.schedule(new Thread("Library-copy-playlist-files") {
                @Override
                public void run() {
                    int n = 0;
                    int total = playlistItems.size();
                    String targetName = selFolder.getName();
                    for (PlaylistItem item : playlistItems) {
                        File f = new File(item.getFilePath());
                        if (f.isFile() && f.exists() && f.canRead()) {
                            try {
                                Path source = f.toPath();
                                Path target = FileSystems.getDefault().getPath(selFolder.getAbsolutePath(), f.getName());
                                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                                n++;
                                //invoked on UI thread later
                                String status = String.format("Copied %d of %d to %s", n, total, targetName);
                                LibraryMediator.instance().getLibrarySearch().pushStatus(status);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    GUIMediator.launchExplorer(selFolder);
                    //and clear the output
                    try {
                        Thread.sleep(2000);
                        LibraryMediator.instance().getLibrarySearch().pushStatus("");
                    } catch (InterruptedException ignored) {
                    }
                }
            });
        }
    }

    private class LibraryPlaylistsCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            LibraryPlaylistsListCell cell = (LibraryPlaylistsListCell) value;
            setText(cell.getText());
            setToolTipText(cell.getDescription());
            setPreferredSize(new Dimension(getSize().width, TableSettings.DEFAULT_TABLE_ROW_HEIGHT.getValue()));
            Icon icon = cell.getIcon();
            if (icon != null) {
                setIcon(icon);
                setBorder(new EmptyBorder(5, 5, 5, 5));
            }
            this.setFont(list.getFont());
            ThemeMediator.fixLabelFont(this);
            return this;
        }
    }

    private class SelectedPlaylistActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            refreshSelection();
        }
    }

    private class LibraryPlaylistsMouseObserver implements MouseObserver {
        public void handleMouseClick(MouseEvent e) {
            int index = list.locationToIndex(e.getPoint());
            list.setSelectedIndex(index);
            if (((LibraryPlaylistsListCell) list.getSelectedValue()).getPlaylist() == null) {
                actionStartRename();
            }
        }

        /**
         * Handles when the mouse is double-clicked.
         */
        public void handleMouseDoubleClick() {
        }

        /**
         * Handles a right-mouse click.
         */
        public void handleRightMouseClick(MouseEvent e) {
        }

        /**
         * Handles a trigger to the popup menu.
         */
        public void handlePopupMenu(MouseEvent e) {
            list.setSelectedIndex(list.locationToIndex(e.getPoint()));
            LibraryPlaylistsListCell cell = (LibraryPlaylistsListCell) list.getSelectedValue();
            final Playlist playlist = cell.getPlaylist();
            JPopupMenu popup;
            if (playlist == null) {
                popup = getNewPlaylistPopupMenu();
            } else if (playlist.getId() == LibraryDatabase.STARRED_PLAYLIST_ID) {
                popup = getStarredPlaylistPopupMenu(playlist);
            } else {
                popup = getPlaylistPopupMenu(playlist);
            }
            popup.show(list, e.getX(), e.getY());
        }
    }

    private class LibraryPlaylistsSelectionListener implements ListSelectionListener {
        public void valueChanged(ListSelectionEvent e) {
            cancelEdit();
            if (e != null && e.getValueIsAdjusting()) {
                return;
            }
            LibraryPlaylistsListCell cell = (LibraryPlaylistsListCell) list.getSelectedValue();
            if (cell == null) {
                return;
            }
            LibraryMediator.instance().getLibraryExplorer().clearSelection();
            if (cell.getAction() != null) {
                cell.getAction().actionPerformed(null);
            }
        }
    }

    private class RefreshAction extends AbstractAction {
        RefreshAction() {
            putValue(Action.NAME, I18n.tr("Refresh"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Refresh selected"));
            putValue(LimeAction.ICON_NAME, "LIBRARY_REFRESH");
        }

        public void actionPerformed(ActionEvent e) {
            refreshSelection();
        }
    }

    private class RefreshID3TagsAction extends AbstractAction {
        RefreshID3TagsAction() {
            putValue(Action.NAME, I18n.tr("Refresh Audio Properties"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Refresh the audio properties based on ID3 tags of selected items"));
            putValue(LimeAction.ICON_NAME, "LIBRARY_REFRESH");
        }

        public void actionPerformed(ActionEvent e) {
            Playlist playlist = getSelectedPlaylist();
            if (playlist != null) {
                LibraryUtils.refreshID3Tags(playlist);
            }
        }
    }

    private class DeleteAction extends AbstractAction {
        DeleteAction() {
            putValue(Action.NAME, I18n.tr("Delete"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Delete Playlist"));
            putValue(LimeAction.ICON_NAME, "PLAYLIST_DELETE");
        }

        public void actionPerformed(ActionEvent e) {
            Playlist selectedPlaylist = getSelectedPlaylist();
            if (selectedPlaylist != null && selectedPlaylist.getId() != LibraryDatabase.STARRED_PLAYLIST_ID) {
                DialogOption showConfirmDialog = GUIMediator.showYesNoMessage(I18n.tr("Are you sure you want to delete the playlist?\n(No files will be deleted)"), I18n.tr("Are you sure?"), JOptionPane.QUESTION_MESSAGE);
                if (showConfirmDialog != DialogOption.YES) {
                    return;
                }
                selectedPlaylist.delete();
                model.removeElement(list.getSelectedValue());
                LibraryMediator.instance().clearLibraryTable();
            }
        }
    }

    private final class CleanupPlaylistAction extends AbstractAction {
        CleanupPlaylistAction() {
            putValue(Action.NAME, I18n.tr("Cleanup playlist"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Remove the deleted items"));
            putValue(LimeAction.ICON_NAME, "PLAYLIST_CLEANUP");
        }

        public void actionPerformed(ActionEvent e) {
            Playlist selectedPlaylist = getSelectedPlaylist();
            if (selectedPlaylist != null) {
                LibraryUtils.cleanup(selectedPlaylist);
                LibraryMediator.instance().getLibraryPlaylists().refreshSelection();
            }
        }
    }

    private class StartRenamingPlaylistAction extends AbstractAction {
        StartRenamingPlaylistAction() {
            putValue(Action.NAME, I18n.tr("Rename"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Rename Playlist"));
            putValue(LimeAction.ICON_NAME, "PLAYLIST_RENAME");
        }

        public void actionPerformed(ActionEvent e) {
            if (((LibraryPlaylistsListCell) list.getSelectedValue()).getPlaylist().getId() != LibraryDatabase.STARRED_PLAYLIST_ID) {
                startEdit(list.getSelectedIndex());
            }
        }
    }

    private final class ImportToPlaylistAction extends AbstractAction {
        ImportToPlaylistAction() {
            putValue(Action.NAME, I18n.tr("Import .m3u to Playlist"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Import a .m3u file into the selected playlist"));
            putValue(LimeAction.ICON_NAME, "PLAYLIST_IMPORT_TO");
        }

        public void actionPerformed(ActionEvent e) {
            importM3U(getSelectedPlaylist());
        }
    }

    private final class ImportToNewPlaylistAction extends AbstractAction {
        ImportToNewPlaylistAction() {
            putValue(Action.NAME, I18n.tr("Import .m3u to New Playlist"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Import a .m3u file to a new playlist"));
            putValue(LimeAction.ICON_NAME, "PLAYLIST_IMPORT_NEW");
        }

        public void actionPerformed(ActionEvent e) {
            importM3U(null);
        }
    }

    private final class ExportPlaylistAction extends AbstractAction {
        ExportPlaylistAction() {
            putValue(Action.NAME, I18n.tr("Export Playlist to .m3u"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Export this playlist into a .m3u file"));
            putValue(LimeAction.ICON_NAME, "PLAYLIST_IMPORT_NEW");
        }

        public void actionPerformed(ActionEvent e) {
            exportM3U(getSelectedPlaylist());
        }
    }

    private final class ExportToiTunesAction extends AbstractAction {
        ExportToiTunesAction() {
            String actionName = I18n.tr("Export Playlist to iTunes");
            String shortDescription = I18n.tr("Export this playlist into an iTunes playlist");
            if (OSUtils.isMacOSCatalina105OrNewer()) {
                actionName = I18n.tr("Export Playlist to Apple Music");
                shortDescription = I18n.tr("Export this playlist into an Apple Music playlist");
            }
            putValue(Action.NAME, actionName);
            putValue(Action.SHORT_DESCRIPTION, shortDescription);
            putValue(LimeAction.ICON_NAME, "PLAYLIST_IMPORT_NEW");
        }

        public void actionPerformed(ActionEvent e) {
            Playlist playlist = getSelectedPlaylist();
            if (playlist != null) {
                List<File> files = new ArrayList<>();
                for (PlaylistItem item : playlist.getItems()) {
                    File file = new File(item.getFilePath());
                    files.add(file);
                }
                iTunesMediator.instance().addSongsiTunes(playlist.getName(), files.toArray(new File[0]));
            }
        }
    }
}
