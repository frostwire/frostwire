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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ToolTipManager;
import javax.swing.UIDefaults;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.plaf.InsetsUIResource;

import org.limewire.util.CommonUtils;
import org.limewire.util.FileUtils;
import org.limewire.util.OSUtils;

import com.frostwire.alexandria.Library;
import com.frostwire.alexandria.Playlist;
import com.frostwire.alexandria.PlaylistItem;
import com.frostwire.gui.library.SortedListModel.SortOrder;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.theme.SkinMenuItem;
import com.frostwire.gui.theme.SkinPopupMenu;
import com.frostwire.gui.theme.ThemeMediator;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;
import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.FileChooserHandler;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.iTunesMediator;
import com.limegroup.gnutella.gui.actions.LimeAction;
import com.limegroup.gnutella.gui.options.ConfigureOptionsAction;
import com.limegroup.gnutella.gui.options.OptionsConstructor;
import com.limegroup.gnutella.gui.tables.DefaultMouseListener;
import com.limegroup.gnutella.gui.tables.MouseObserver;
import com.limegroup.gnutella.gui.tables.TableSettings;
import com.limegroup.gnutella.gui.util.BackgroundExecutorService;
import com.limegroup.gnutella.settings.QuestionsHandler;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class LibraryPlaylists extends AbstractLibraryListPanel {

    private DefaultListModel<Object> _model;
    private int _selectedIndexToRename;

    private LibraryPlaylistsListCell _newPlaylistCell;

    private ActionListener _selectedPlaylistAction;

    private LibraryPlaylistsMouseObserver _listMouseObserver;
    private ListSelectionListener _listSelectionListener;

    private JList<Object> _list;
    private JScrollPane _scrollPane;
    private JTextField _textName;

    private JPopupMenu _popup;
    private Action refreshAction = new RefreshAction();
    private Action refreshID3TagsAction = new RefreshID3TagsAction();
    private Action deleteAction = new DeleteAction();
    private Action cleanupPlaylistAction = new CleanupPlaylistAction();
    private Action renameAction = new StartRenamingPlaylistAction();
    private Action importToPlaylistAction = new ImportToPlaylistAction();
    private Action importToNewPlaylistAction = new ImportToNewPlaylistAction();
    private Action copyPlaylistFilesAction = new CopyPlaylistFilesAction();
    private Action exportPlaylistAction = new ExportPlaylistAction();
    private Action exportToiTunesAction = new ExportToiTunesAction();

    private List<Playlist> importingPlaylists;

    public LibraryPlaylists() {
        setupUI();
        importingPlaylists = new ArrayList<Playlist>();
    }

    public Dimension getRowDimension() {
        Rectangle rect = _list.getUI().getCellBounds(_list, 0, 0);
        return rect.getSize();
    }

    public void addPlaylist(Playlist playlist) {
        LibraryPlaylistsListCell cell = new LibraryPlaylistsListCell(null, null, GUIMediator.getThemeImage("playlist"), playlist, _selectedPlaylistAction);
        _model.addElement(cell);
    }

    public void clearSelection() {
        _list.clearSelection();
    }

    public Playlist getSelectedPlaylist() {
        LibraryPlaylistsListCell cell = (LibraryPlaylistsListCell) _list.getSelectedValue();
        return cell != null ? cell.getPlaylist() : null;
    }

    protected void setupUI() {
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(177,94));

        GUIMediator.addRefreshListener(this);

        setupPopupMenu();
        setupModel();
        setupList();

        _scrollPane = new JScrollPane(_list);

        add(_scrollPane);
    }

    private void setupPopupMenu() {
        _popup = new SkinPopupMenu();
        _popup.add(new SkinMenuItem(refreshAction));
        _popup.add(new SkinMenuItem(refreshID3TagsAction));
        _popup.add(new SkinMenuItem(renameAction));
        _popup.addSeparator();
        _popup.add(new SkinMenuItem(deleteAction));
        _popup.add(new SkinMenuItem(cleanupPlaylistAction));
        _popup.addSeparator();
        _popup.add(new SkinMenuItem(importToPlaylistAction));
        _popup.add(new SkinMenuItem(importToNewPlaylistAction));
        _popup.addSeparator();
        _popup.add(new SkinMenuItem(copyPlaylistFilesAction));
        _popup.add(new SkinMenuItem(exportPlaylistAction));
        _popup.addSeparator();

        if (OSUtils.isWindows() || OSUtils.isMacOSX()) {
            _popup.add(new SkinMenuItem(exportToiTunesAction));
        }

        _popup.addSeparator();
        _popup.add(new SkinMenuItem(new ConfigureOptionsAction(OptionsConstructor.LIBRARY_KEY, I18n.tr("Configure Options"), I18n.tr("You can configure the FrostWire\'s Options."))));
    }

    private void setupModel() {
        _model = new DefaultListModel<Object>();

        _newPlaylistCell = new LibraryPlaylistsListCell(I18n.tr("New Playlist"), I18n.tr("Creates a new Playlist"), GUIMediator.getThemeImage("playlist_plus"), null, null);

        Library library = LibraryMediator.getLibrary();

        _selectedPlaylistAction = new SelectedPlaylistActionListener();

        _model.addElement(_newPlaylistCell);
        for (Playlist playlist : library.getPlaylists()) {
            LibraryPlaylistsListCell cell = new LibraryPlaylistsListCell(null, null, GUIMediator.getThemeImage("playlist"), playlist, _selectedPlaylistAction);
            _model.addElement(cell);
        }
    }

    private void setupList() {
        _listMouseObserver = new LibraryPlaylistsMouseObserver();
        _listSelectionListener = new LibraryPlaylistsSelectionListener();

        SortedListModel sortedModel = new SortedListModel(_model, SortOrder.ASCENDING, new Comparator<LibraryPlaylistsListCell>() {

            @Override
            public int compare(LibraryPlaylistsListCell o1, LibraryPlaylistsListCell o2) {
                if (o1 == _newPlaylistCell) {
                    return -1;
                }
                if (o2 == _newPlaylistCell) {
                    return 1;
                }

                return o1.getText().compareTo(o2.getText());
            }
        });

        _list = new LibraryIconList(sortedModel);
        _list.setFixedCellHeight(TableSettings.DEFAULT_TABLE_ROW_HEIGHT.getValue());
        _list.setCellRenderer(new LibraryPlaylistsCellRenderer());
        _list.addMouseListener(new DefaultMouseListener(_listMouseObserver));
        _list.addListSelectionListener(_listSelectionListener);
        _list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        _list.setLayoutOrientation(JList.VERTICAL);
        _list.setPrototypeCellValue(new LibraryPlaylistsListCell("test", "", GUIMediator.getThemeImage("playlist"), null, null));
        _list.setVisibleRowCount(-1);
        _list.setDragEnabled(true);
        _list.setTransferHandler(new LibraryPlaylistsTransferHandler(_list));
        ToolTipManager.sharedInstance().registerComponent(_list);

        _list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                list_keyPressed(e);
            }
        });

        _list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() > 1) {
                    actionStartRename();
                }
            }
        });

        _textName = new JTextField();
        ThemeMediator.fixKeyStrokes(_textName);
        UIDefaults defaults = new UIDefaults();
        defaults.put("TextField.contentMargins", new InsetsUIResource(0, 4, 0, 4));
        _textName.putClientProperty("Nimbus.Overrides.InheritDefaults", Boolean.TRUE);
        _textName.putClientProperty("Nimbus.Overrides", defaults);
        _textName.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                textName_keyPressed(e);
            }
        });
        _textName.setVisible(false);

        _list.add(_textName);
    }

    protected void list_keyPressed(KeyEvent e) {
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
            deleteAction.actionPerformed(null);
        }

        if (LibraryUtils.isRefreshKeyEvent(e)) {
            refreshSelection();
        }
    }

    protected void textName_keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        if (_selectedIndexToRename != -1 && key == KeyEvent.VK_ENTER) {
            renameSelectedItem(_selectedIndexToRename);
        } else if (_selectedIndexToRename == -1 && key == KeyEvent.VK_ENTER) {
            createNewPlaylist();
        } else if (key == KeyEvent.VK_ESCAPE) {
            _textName.setVisible(false);
        }
    }

    public void refreshSelection() {

        LibraryPlaylistsListCell cell = (LibraryPlaylistsListCell) _list.getSelectedValue();

        if (cell == null) {
            // handle special case
            if (_model.getSize() == 2 && MediaPlayer.instance().getCurrentPlaylist() == null) {
                _list.setSelectedIndex(1);
            }
            return;
        }

        Playlist playlist = cell.getPlaylist();

        if (playlist != null) {
            playlist.refresh();
            LibraryMediator.instance().updateTableItems(playlist);
            String status = LibraryUtils.getPlaylistDurationInDDHHMMSS(playlist) + ", " + playlist.getItems().size() + " " + I18n.tr("tracks");
            LibraryMediator.instance().getLibrarySearch().setStatus(status);
        }

        executePendingRunnables();
    }

    private void actionStartRename() {
        cancelEdit();
        int index = _list.getSelectedIndex();
        if (index != -1) {
            startEdit(index);
        }
    }

    private void startEdit(int index) {
        if (index < 0) {
            return;
        }

        LibraryPlaylistsListCell cell = (LibraryPlaylistsListCell) _model.getElementAt(index);
        _selectedIndexToRename = cell.getPlaylist() != null ? index : -1;

        String text = cell.getText();

        Rectangle rect = _list.getUI().getCellBounds(_list, index, index);
        Dimension lsize = rect.getSize();
        Point llocation = rect.getLocation();
        _textName.setSize(lsize);
        _textName.setLocation(llocation);

        _textName.setText(text);
        _textName.setSelectionStart(0);
        _textName.setSelectionEnd(text.length());

        _textName.setVisible(true);

        _textName.requestFocusInWindow();
        _textName.requestFocus();
    }

    public void selectPlaylist(Playlist playlist) {
        Object selectedValue = _list.getSelectedValue();
        if (selectedValue != null && ((LibraryPlaylistsListCell) selectedValue).getPlaylist() != null && ((LibraryPlaylistsListCell) selectedValue).getPlaylist().equals(playlist)) {
            // already selected
            try {
                _listSelectionListener.valueChanged(null);
            } catch (Exception e) {
                System.out.println();
            }
            return;
        }

        int size = _model.getSize();

        for (int i = 0; i < size; i++) {
            try {
                LibraryPlaylistsListCell cell = (LibraryPlaylistsListCell) _model.get(i);
                if (cell.getPlaylist() != null && cell.getPlaylist().equals(playlist)) {
                    _list.setSelectedValue(cell, true);
                    return;
                }
            } catch (Exception e) {
            }
        }
    }

    private void renameSelectedItem(int index) {
        if (!_textName.isVisible() || _textName.getText().trim().length() == 0) {
            return;
        }

        Playlist selectedPlaylist = getSelectedPlaylist();

        selectedPlaylist.setName(_textName.getText().trim());
        selectedPlaylist.save();

        _list.repaint();
        _textName.setVisible(false);
        UXStats.instance().log(UXAction.LIBRARY_PLAYLIST_RENAMED);
    }

    private void createNewPlaylist() {
        if (!_textName.isVisible()) {
            return;
        }

        String name = _textName.getText();

        Library library = LibraryMediator.getLibrary();

        Playlist playlist = library.newPlaylist(name, name);
        playlist.save();
        LibraryPlaylistsListCell cell = new LibraryPlaylistsListCell(null, null, GUIMediator.getThemeImage("playlist"), playlist, _selectedPlaylistAction);
        _model.addElement(cell);
        _list.setSelectedValue(cell, true);

        _textName.setVisible(false);
        UXStats.instance().log(UXAction.LIBRARY_PLAYLIST_CREATED);
    }

    private void cancelEdit() {
        _selectedIndexToRename = -1;
        _textName.setVisible(false);
    }

    //// handle m3u import/export
    /**
     * Loads a playlist.
     */
    public void importM3U(Playlist playlist) {
        File parentFile = FileChooserHandler.getLastInputDirectory();

        if (parentFile == null)
            parentFile = CommonUtils.getCurrentDirectory();

        final File selFile = FileChooserHandler.getInputFile(GUIMediator.getAppFrame(), I18n.tr("Open Playlist (.m3u)"), parentFile, new PlaylistListFileFilter());

        // nothing selected? exit.
        if (selFile == null || !selFile.isFile())
            return;

        String path = selFile.getPath();
        try {
            path = FileUtils.getCanonicalPath(selFile);
        } catch (IOException ignored) {
            //LOG.warn("unable to get canonical path for file: " + selFile, ignored);
        }

        // create a new thread off of the event queue to process reading the files from
        //  disk
        loadM3U(playlist, selFile, path);
    }

    /**
     * Performs the actual reading of the PlayList and generation of the PlayListItems from
     * the PlayList. Once we have done the heavy weight construction of the PlayListItem
     * list, the list is handed to the swing event queue to process adding the files to
     * the actual table model
     * 
     * 
     * @param selFile - file that we're reading from
     * @param path - path of file to open
     * @param overwrite - true if the table should be cleared of all entries prior to loading
     *          the new playlist
     */
    private void loadM3U(final Playlist playlist, final File selFile, final String path) {
        BackgroundExecutorService.schedule(new Runnable() {
            public void run() {
                try {
                    final List<File> files = M3UPlaylist.load(path);
                    if (playlist != null) {
                        LibraryUtils.asyncAddToPlaylist(playlist, files.toArray(new File[0]));
                    } else {
                        LibraryUtils.createNewPlaylist(files.toArray(new File[0]));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    GUIMediator.safeInvokeLater(new Runnable() {
                        public void run() {
                            GUIMediator.showError("Unable to load playlist");
                        }
                    });
                }
            }
        });
    }

    /**
     * Saves a playlist.
     */
    public void exportM3U(Playlist playlist) {

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

        File selFile = FileChooserHandler.getSaveAsFile(GUIMediator.getAppFrame(), I18n.tr("Save Playlist As"), suggested, new PlaylistListFileFilter());

        // didn't select a file?  nothing we can do.
        if (selFile == null) {
            return;
        }

        // if the file already exists and not the one just opened, ask if it should be
        //  overwritten. 
        //TODO: this should be handled in the jfilechooser
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
     * @param path - file location to save the list to
     */
    private void saveM3U(final Playlist playlist, final String path) {
        BackgroundExecutorService.schedule(new Runnable() {
            public void run() {
                try {
                    List<File> files = new ArrayList<File>();
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
                    GUIMediator.safeInvokeLater(new Runnable() {
                        public void run() {
                            GUIMediator.showError("Unable to save playlist");
                        }
                    });
                }
            }
        });
    }

    public static class LibraryPlaylistsListCell {

        private final String _text;
        private final String _description;
        private final Icon _icon;
        private final Playlist _playlist;
        private final ActionListener _action;

        public LibraryPlaylistsListCell(String text, String description, Icon icon, Playlist playlist, ActionListener action) {
            _text = text;
            _description = description;
            _icon = icon;
            _playlist = playlist;
            _action = action;
        }

        public String getText() {
            if (_text != null) {
                return _text;
            } else if (_playlist != null && _playlist.getName() != null) {
                return _playlist.getName();
            } else {
                return "";
            }
        }

        public String getDescription() {
            if (_description != null) {
                return _description;
            } else if (_playlist != null && _playlist.getDescription() != null) {
                return _playlist.getDescription();
            } else {
                return "";
            }
        }

        public Icon getIcon() {
            return _icon;
        }

        public Playlist getPlaylist() {
            return _playlist;
        }

        public ActionListener getAction() {
            return _action;
        }
    }

    private class LibraryPlaylistsCellRenderer extends DefaultListCellRenderer {

        private final AbstractBorder border = new EmptyBorder(5,5,5,5);

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
                setBorder(border);
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
            int index = _list.locationToIndex(e.getPoint());
            _list.setSelectedIndex(index);
            if (((LibraryPlaylistsListCell) _list.getSelectedValue()).getPlaylist() == null) {
                actionStartRename();
            }
        }

        /**
         * Handles when the mouse is double-clicked.
         */
        public void handleMouseDoubleClick(MouseEvent e) {
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
            _list.setSelectedIndex(_list.locationToIndex(e.getPoint()));
            _popup.show(_list, e.getX(), e.getY());
        }
    }

    private class LibraryPlaylistsSelectionListener implements ListSelectionListener {
        public void valueChanged(ListSelectionEvent e) {
            cancelEdit();

            if (e != null && e.getValueIsAdjusting()) {
                return;
            }

            LibraryPlaylistsListCell cell = (LibraryPlaylistsListCell) _list.getSelectedValue();

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

        /**
         * 
         */
        private static final long serialVersionUID = 3259221218056223423L;

        public RefreshAction() {
            putValue(Action.NAME, I18n.tr("Refresh"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Refresh selected"));
            putValue(LimeAction.ICON_NAME, "LIBRARY_REFRESH");
        }

        public void actionPerformed(ActionEvent e) {
            refreshSelection();
        }
    }

    private class RefreshID3TagsAction extends AbstractAction {

        private static final long serialVersionUID = -472437818841089984L;

        public RefreshID3TagsAction() {
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

        private static final long serialVersionUID = 520856485566457934L;

        public DeleteAction() {
            putValue(Action.NAME, I18n.tr("Delete"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Delete Playlist"));
            putValue(LimeAction.ICON_NAME, "PLAYLIST_DELETE");
        }

        public void actionPerformed(ActionEvent e) {

            Playlist selectedPlaylist = getSelectedPlaylist();

            if (selectedPlaylist != null) {
                DialogOption showConfirmDialog = GUIMediator.showYesNoMessage(I18n.tr("Are you sure you want to delete the playlist?\n(No files will be deleted)"), I18n.tr("Are you sure?"), JOptionPane.QUESTION_MESSAGE);

                if (showConfirmDialog != DialogOption.YES)
                  return;

                selectedPlaylist.delete();
                _model.removeElement(_list.getSelectedValue());
                LibraryMediator.instance().clearLibraryTable();
                UXStats.instance().log(UXAction.LIBRARY_PLAYLIST_REMOVED);
            }
        }
    }

    private final class CleanupPlaylistAction extends AbstractAction {

        private static final long serialVersionUID = 8400749433148927596L;

        public CleanupPlaylistAction() {
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
        private static final long serialVersionUID = 520856485566457934L;

        public StartRenamingPlaylistAction() {
            putValue(Action.NAME, I18n.tr("Rename"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Rename Playlist"));
            putValue(LimeAction.ICON_NAME, "PLAYLIST_RENAME");
        }

        public void actionPerformed(ActionEvent e) {
            startEdit(_list.getSelectedIndex());
        }
    }

    public void reselectPlaylist() {
        _listSelectionListener.valueChanged(null);
    }

    @Override
    public void refresh() {
        _list.repaint();
    }

    /**
     * <tt>FileFilter</tt> class for only displaying m3u file types in
     * the directory chooser.
     */
    private static class PlaylistListFileFilter extends FileFilter {
        public boolean accept(File f) {
            return f.isDirectory() || f.getName().toLowerCase().endsWith("m3u");
        }

        public String getDescription() {
            return I18n.tr("Playlist Files (*.m3u)");
        }
    }

    private final class ImportToPlaylistAction extends AbstractAction {

        private static final long serialVersionUID = -9099898749358019734L;

        public ImportToPlaylistAction() {
            putValue(Action.NAME, I18n.tr("Import .m3u to Playlist"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Import a .m3u file into the selected playlist"));
            putValue(LimeAction.ICON_NAME, "PLAYLIST_IMPORT_TO");
        }

        public void actionPerformed(ActionEvent e) {
            importM3U(getSelectedPlaylist());
        }
    }

    private final class ImportToNewPlaylistAction extends AbstractAction {

        private static final long serialVersionUID = 390846680458085610L;

        public ImportToNewPlaylistAction() {
            putValue(Action.NAME, I18n.tr("Import .m3u to New Playlist"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Import a .m3u file to a new playlist"));
            putValue(LimeAction.ICON_NAME, "PLAYLIST_IMPORT_NEW");
        }

        public void actionPerformed(ActionEvent e) {
            importM3U(null);
        }
    }

    public final static class CopyPlaylistFilesAction extends AbstractAction {

        public CopyPlaylistFilesAction() {
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
            final List<PlaylistItem> playlistItems = new ArrayList<PlaylistItem>(playlist.getItems());

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
                    } catch (InterruptedException e) {
                    }

                }
            });
        }
    }

    private final class ExportPlaylistAction extends AbstractAction {

        private static final long serialVersionUID = 6149822357662730490L;

        public ExportPlaylistAction() {
            putValue(Action.NAME, I18n.tr("Export Playlist to .m3u"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Export this playlist into a .m3u file"));
            putValue(LimeAction.ICON_NAME, "PLAYLIST_IMPORT_NEW");
        }

        public void actionPerformed(ActionEvent e) {
            exportM3U(getSelectedPlaylist());
        }
    }

    private final class ExportToiTunesAction extends AbstractAction {

        private static final long serialVersionUID = 3601146746674363384L;

        public ExportToiTunesAction() {
            putValue(Action.NAME, I18n.tr("Export Playlist to iTunes"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Export this playlist into an iTunes playlist"));
            putValue(LimeAction.ICON_NAME, "PLAYLIST_IMPORT_NEW");
        }

        public void actionPerformed(ActionEvent e) {
            Playlist playlist = getSelectedPlaylist();
            if (playlist != null) {
                List<File> files = new ArrayList<File>();
                for (PlaylistItem item : playlist.getItems()) {
                    File file = new File(item.getFilePath());
                    files.add(file);
                }
                iTunesMediator.instance().addSongsiTunes(playlist.getName(), files.toArray(new File[0]));
            }
        }
    }

    public void markBeginImport(Playlist playlist) {
        try {
            if (!importingPlaylists.contains(playlist)) {
                importingPlaylists.add(playlist);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void markEndImport(Playlist playlist) {
        try {
            importingPlaylists.remove(playlist);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isPlaylistImporting(Playlist playlist) {
        try {
            return importingPlaylists.contains(playlist);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
