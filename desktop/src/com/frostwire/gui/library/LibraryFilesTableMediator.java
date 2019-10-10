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
import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.gui.bittorrent.*;
import com.frostwire.gui.library.tags.TagsReader;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.player.MediaSource;
import com.frostwire.gui.theme.SkinMenu;
import com.frostwire.gui.theme.SkinMenuItem;
import com.frostwire.gui.theme.SkinPopupMenu;
import com.frostwire.mp4.Mp4Demuxer;
import com.frostwire.mp4.Mp4Info;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.gui.actions.AbstractAction;
import com.limegroup.gnutella.gui.actions.LimeAction;
import com.limegroup.gnutella.gui.actions.SearchAction;
import com.limegroup.gnutella.gui.search.GenericCellEditor;
import com.limegroup.gnutella.gui.tables.LimeJTable;
import com.limegroup.gnutella.gui.util.BackgroundExecutorService;
import com.limegroup.gnutella.gui.util.GUILauncher;
import com.limegroup.gnutella.gui.util.GUILauncher.LaunchableProvider;
import com.limegroup.gnutella.util.QueryUtils;
import org.apache.commons.io.FilenameUtils;
import org.limewire.util.FileUtils;
import org.limewire.util.OSUtils;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;
import java.util.*;

/**
 * This class wraps the JTable that displays files in the library,
 * controlling access to the table and the various table properties.
 * It is the Mediator to the Table part of the Library display.
 *
 * @author gubatron
 * @author aldenml
 */
final class LibraryFilesTableMediator extends AbstractLibraryTableMediator<LibraryFilesTableModel, LibraryFilesTableDataLine, File> {
    /**
     * instance, for singleton access
     */
    private static LibraryFilesTableMediator INSTANCE;
    /**
     * Variables so the PopupMenu & ButtonRow can have the same listeners
     */
    private Action LAUNCH_ACTION;
    private Action LAUNCH_OS_ACTION;
    private Action OPEN_IN_FOLDER_ACTION;
    private Action DEMUX_MP4_AUDIO_ACTION;
    private Action CREATE_TORRENT_ACTION;
    private Action DELETE_ACTION;
    private Action SEND_TO_ITUNES_ACTION;

    /**
     * Note: This is set up for this to work.
     * Polling is not needed though, because updates
     * already generate update events.
     */
    private LibraryFilesTableMediator() {
        super("LIBRARY_FILES_TABLE");
    }

    public static LibraryFilesTableMediator instance() {
        if (INSTANCE == null) {
            INSTANCE = new LibraryFilesTableMediator();
        }
        return INSTANCE;
    }

    /**
     * Returns the options offered to the user when removing files.
     * <p>
     * Depending on the platform these can be a subset of
     * MOVE_TO_TRASH, DELETE, CANCEL.
     */
    private static Object[] createRemoveOptions() {
        if (OSUtils.supportsTrash()) {
            String trashLabel = OSUtils.isWindows() ? I18n.tr("Move to Recycle Bin") : I18n.tr("Move to Trash");
            return new Object[]{trashLabel, I18n.tr("Delete"), I18n.tr("Cancel")};
        } else {
            return new Object[]{I18n.tr("Delete"), I18n.tr("Cancel")};
        }
    }

    /**
     * Creates a JList of files and sets and makes it non-selectable.
     */
    private static JList<String> createFileList(List<String> fileNames) {
        JList<String> fileList;
        fileList = new JList<>(fileNames.toArray(new String[0]));
        fileList.setVisibleRowCount(5);
        fileList.setCellRenderer(new FileNameListCellRenderer());
        fileList.setFocusable(false);
        return fileList;
    }

    /**
     * Split a collection in Lists of up to partitionSize elements.
     */
    private static <T> List<List<T>> split(int partitionSize, List<T> collection) {
        List<List<T>> lists = new LinkedList<>();
        for (int i = 0; i < collection.size(); i += partitionSize) {
            //the compiler might not know if the collection has changed size
            //so it might not optimize this by itself.
            int jLimit = Math.min(collection.size(), i + partitionSize);
            List<T> newList = new LinkedList<>();
            for (int j = i; j < jLimit; j++) {
                newList.add(collection.get(j));
            }
            lists.add(newList);
        }
        return lists;
    }

    private static boolean hasExtension(String filename, String... extensionsWithoutDot) {
        String extension = FilenameUtils.getExtension(filename).toLowerCase();
        for (String ext : extensionsWithoutDot) {
            if (ext.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Build some extra listeners
     */
    protected void buildListeners() {
        super.buildListeners();
        LAUNCH_ACTION = new LaunchAction();
        LAUNCH_OS_ACTION = new LaunchOSAction();
        OPEN_IN_FOLDER_ACTION = new OpenInFolderAction();
        DEMUX_MP4_AUDIO_ACTION = new DemuxMP4AudioAction();
        CREATE_TORRENT_ACTION = new CreateTorrentAction();
        DELETE_ACTION = new RemoveAction();
        SEND_TO_ITUNES_ACTION = new SendAudioFilesToiTunes();
    }

    @Override
    protected void addListeners() {
        super.addListeners();
        TABLE.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (LibraryUtils.isRefreshKeyEvent(e)) {
                    LibraryMediator.instance().getLibraryExplorer().refreshSelection(true);
                }
            }
        });
    }

    /**
     * Set up the constants
     */
    protected void setupConstants() {
        super.setupConstants();
        MAIN_PANEL = new PaddedPanel();
        DATA_MODEL = new LibraryFilesTableModel();
        //sort by modification time in descending order by default
        //so user can quickly find newest files.
        DATA_MODEL.sort(LibraryFilesTableDataLine.MODIFICATION_TIME_IDX);
        DATA_MODEL.sort(LibraryFilesTableDataLine.MODIFICATION_TIME_IDX);
        TABLE = new LimeJTable(DATA_MODEL);
        Action[] aa = new Action[]{LAUNCH_ACTION, OPEN_IN_FOLDER_ACTION, SEND_TO_FRIEND_ACTION, DELETE_ACTION, OPTIONS_ACTION};
        BUTTON_ROW = new ButtonRow(aa, ButtonRow.X_AXIS, ButtonRow.NO_GLUE);
    }

    // inherit doc comment
    protected JPopupMenu createPopupMenu() {
        if (TABLE.getSelectionModel().isSelectionEmpty()) {
            return null;
        }
        JPopupMenu menu = new SkinPopupMenu();
        menu.add(new SkinMenuItem(LAUNCH_ACTION));
        if (getMediaType().equals(MediaType.getAudioMediaType())) {
            menu.add(new SkinMenuItem(LAUNCH_OS_ACTION));
        }
        if (hasExploreAction()) {
            menu.add(new SkinMenuItem(OPEN_IN_FOLDER_ACTION));
        }
        if (areAllSelectedFilesMP4s()) {
            menu.add(DEMUX_MP4_AUDIO_ACTION);
            DEMUX_MP4_AUDIO_ACTION.setEnabled(!((DemuxMP4AudioAction) DEMUX_MP4_AUDIO_ACTION).isDemuxing());
        }
        menu.add(new SkinMenuItem(CREATE_TORRENT_ACTION));
        if (areAllSelectedFilesPlayable()) {
            menu.add(createAddToPlaylistSubMenu());
        }
        menu.add(new SkinMenuItem(SEND_TO_FRIEND_ACTION));
        menu.add(new SkinMenuItem(SEND_TO_ITUNES_ACTION));
        menu.addSeparator();
        menu.add(new SkinMenuItem(DELETE_ACTION));
        menu.addSeparator();
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
        DELETE_ACTION.setEnabled(true);
        LibraryFilesTableDataLine line = DATA_MODEL.get(rows[0]);
        menu.add(createSearchSubMenu(line));
        return menu;
    }

    private boolean areAllSelectedFilesMP4s() {
        boolean selectionIsAllMP4 = true;
        int[] selectedRows = TABLE.getSelectedRows();
        for (int i : selectedRows) {
            if (!DATA_MODEL.getFile(i).getAbsolutePath().toLowerCase().endsWith("mp4")) {
                selectionIsAllMP4 = false;
                break;
            }
        }
        return selectionIsAllMP4;
    }

    private boolean areAllSelectedFilesPlayable() {
        boolean selectionIsAllAudio = true;
        int[] selectedRows = TABLE.getSelectedRows();
        for (int i : selectedRows) {
            if (!MediaPlayer.isPlayableFile(DATA_MODEL.get(i).getInitializeObject())) {
                selectionIsAllAudio = false;
                break;
            }
        }
        return selectionIsAllAudio;
    }

    private JMenu createSearchSubMenu(LibraryFilesTableDataLine dl) {
        SkinMenu menu = new SkinMenu(I18n.tr("Search"));
        if (dl != null) {
            File f = dl.getInitializeObject();
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
        TABLE.setTransferHandler(new LibraryFilesTableTransferHandler(this));
    }

    @Override
    protected void setDefaultRenderers() {
        super.setDefaultRenderers();
        TABLE.setDefaultRenderer(PlayableIconCell.class, new PlayableIconCellRenderer());
        TABLE.setDefaultRenderer(PlayableCell.class, new PlayableCellRenderer());
        TABLE.setDefaultRenderer(PaymentOptions.class, new PaymentOptionsRenderer());
    }

    /**
     * Sets the default editors.
     */
    protected void setDefaultEditors() {
        TableColumnModel model = TABLE.getColumnModel();
        TableColumn tc = model.getColumn(LibraryFilesTableDataLine.ACTIONS_IDX);
        tc.setCellEditor(new GenericCellEditor(getAbstractActionsRenderer()));
        tc = model.getColumn(LibraryFilesTableDataLine.PAYMENT_OPTIONS_IDX);
        tc.setCellEditor(new GenericCellEditor(new PaymentOptionsRenderer()));
    }

    /**
     * Updates the Table based on the selection of the given table.
     * Perform lookups to remove any store files from the shared folder
     * view and to only display store files in the store view
     */
    void updateTableFiles(DirectoryHolder dirHolder) {
        if (dirHolder == null) {
            return;
        }
        if (dirHolder instanceof MediaTypeSavedFilesDirectoryHolder) {
            MediaType mediaType = ((MediaTypeSavedFilesDirectoryHolder) dirHolder).getMediaType();
            setMediaType(mediaType);
        } else {
            setMediaType(MediaType.getAnyTypeMediaType());
        }
        clearTable();
        List<List<File>> partitionedFiles = split(100, Arrays.asList(dirHolder.getFiles()));
        for (List<File> partition : partitionedFiles) {
            final List<File> fPartition = partition;
            BackgroundExecutorService.schedule(() -> {
                for (final File file : fPartition) {
                    GUIMediator.safeInvokeLater(() -> addUnsorted(file));
                }
                GUIMediator.safeInvokeLater(() -> LibraryMediator.instance().getLibrarySearch().addResults(fPartition.size()));
            });
        }
        forceResort();
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

    public List<MediaSource> getFilesView() {
        int size = DATA_MODEL.getRowCount();
        List<MediaSource> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            try {
                File file = DATA_MODEL.get(i).getFile();
                if (MediaPlayer.isPlayableFile(file)) {
                    result.add(new MediaSource(DATA_MODEL.get(i).getFile()));
                }
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }
        return result;
    }

    /**
     * Override the default removal so we can actually stop sharing
     * and delete the file.
     * Deletes the selected rows in the table.
     * CAUTION: THIS WILL DELETE THE FILE FROM THE DISK.
     */
    public void removeSelection() {
        int[] rows = TABLE.getSelectedRows();
        if (rows.length == 0)
            return;
        if (TABLE.isEditing()) {
            TableCellEditor editor = TABLE.getCellEditor();
            editor.cancelCellEditing();
        }
        List<File> files = new ArrayList<>(rows.length);
        // sort row indices and go backwards so list indices don't change when
        // removing the files from the model list
        Arrays.sort(rows);
        for (int i = rows.length - 1; i >= 0; i--) {
            File file = DATA_MODEL.getFile(rows[i]);
            files.add(file);
        }
        CheckBoxListPanel<File> listPanel = new CheckBoxListPanel<>(files, new FileTextProvider(), true);
        listPanel.getList().setVisibleRowCount(4);
        // display list of files that should be deleted
        Object[] message = new Object[]{new MultiLineLabel(I18n.tr("Are you sure you want to delete the selected file(s), thus removing it from your computer?"), 400), Box.createVerticalStrut(ButtonRow.BUTTON_SEP), listPanel, Box.createVerticalStrut(ButtonRow.BUTTON_SEP)};
        // get platform dependent options which are displayed as buttons in the dialog
        Object[] removeOptions = createRemoveOptions();
        int option = JOptionPane.showOptionDialog(MessageService.getParentComponent(), message, I18n.tr("Message"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, removeOptions, removeOptions[0] /* default option */);
        if (option == removeOptions.length - 1 /* "cancel" option index */
                || option == JOptionPane.CLOSED_OPTION) {
            return;
        }
        // remove still selected files
        List<File> selected = listPanel.getSelectedElements();
        List<String> undeletedFileNames;
        undeletedFileNames = new ArrayList<>();
        boolean somethingWasRemoved = false;
        for (File file : selected) {
            // stop seeding if seeding
            BittorrentDownload dm;
            if ((dm = TorrentUtil.getDownloadManager(file)) != null) {
                dm.setDeleteDataWhenRemove(false);
                dm.setDeleteTorrentWhenRemove(false);
                BTDownloadMediator.instance().remove(dm);
            }
            // close media player if still playing
            if (MediaPlayer.instance().isThisBeingPlayed(file)) {
                MediaPlayer.instance().stop();
                MPlayerMediator.instance().showPlayerWindow(false);
            }
            // removeOptions > 2 => OS offers trash options
            boolean removed = FileUtils.delete(file, removeOptions.length > 2 && option == 0 /* "move to trash" option index */);
            if (removed) {
                somethingWasRemoved = true;
                DATA_MODEL.remove(DATA_MODEL.getRow(file));
            } else {
                undeletedFileNames.add(getCompleteFileName(file));
            }
        }
        clearSelection();
        if (somethingWasRemoved) {
            LibraryMediator.instance().getLibraryExplorer().refreshSelection(true);
        }
        if (undeletedFileNames.isEmpty()) {
            return;
        }
        // display list of files that could not be deleted
        message = new Object[]{new MultiLineLabel(I18n.tr("The following files could not be deleted. They may be in use by another application or are currently being downloaded to."), 400), Box.createVerticalStrut(ButtonRow.BUTTON_SEP), new JScrollPane(createFileList(undeletedFileNames))};
        JOptionPane.showMessageDialog(MessageService.getParentComponent(), message, I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
        super.removeSelection();
    }

    /**
     * Returns the human readable file name for incomplete files or
     * just the regular file name otherwise.
     */
    private String getCompleteFileName(File file) {
        return file.getName();
    }

    public void handleActionKey() {
        LibraryFilesTableDataLine line = DATA_MODEL.get(TABLE.getSelectedRow());
        if (line == null || line.getFile() == null) {
            return;
        }
        if (getMediaType().equals(MediaType.getAudioMediaType()) && MediaPlayer.isPlayableFile(line.getFile())) {
            MediaPlayer.instance().asyncLoadMedia(new MediaSource(line.getFile()), true, null, getFilesView());
            return;
        }
        launch(true);
    }

    /**
     * Launches the associated applications for each selected file
     * in the library if it can.
     */
    private void launch(boolean playAudio) {
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
                String extension = FilenameUtils.getExtension(selectedFile.getName());
                if (extension != null && extension.toLowerCase().equals("torrent")) {
                    GUIMediator.instance().openTorrentFile(selectedFile, true);
                } else {
                    GUIMediator.launchFile(selectedFile);
                }
                return;
            }
        }
        LaunchableProvider[] providers = new LaunchableProvider[rows.length];
        boolean stopAudio = false;
        for (int i = 0; i < rows.length; i++) {
            try {
                MediaType mt = MediaType.getMediaTypeForExtension(FilenameUtils.getExtension(DATA_MODEL.getFile(rows[i]).getName()));
                if (mt != null && mt.equals(MediaType.getVideoMediaType())) {
                    stopAudio = true;
                }
            } catch (Throwable e) {
                // ignore
            }
            providers[i] = new FileProvider(DATA_MODEL.getFile(rows[i]));
        }
        if (stopAudio || !playAudio) {
            MediaPlayer.instance().stop();
        }
        if (playAudio) {
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
        if (selectedFile != null && !selectedFile.getName().endsWith(".torrent")) {
            CREATE_TORRENT_ACTION.setEnabled(sel.length == 1);
        }
        if (selectedFile != null) {
            SEND_TO_FRIEND_ACTION.setEnabled(sel.length == 1);
            if (getMediaType().equals(MediaType.getAnyTypeMediaType())) {
                boolean atLeastOneIsPlayable = false;
                for (int i : sel) {
                    File f = getFile(i);
                    if (MediaPlayer.isPlayableFile(f) || hasExtension(f.getAbsolutePath(), "mp4")) {
                        atLeastOneIsPlayable = true;
                        break;
                    }
                }
                SEND_TO_ITUNES_ACTION.setEnabled(atLeastOneIsPlayable);
            } else {
                SEND_TO_ITUNES_ACTION.setEnabled(getMediaType().equals(MediaType.getAudioMediaType()) || hasExtension(selectedFile.getAbsolutePath(), "mp4"));
            }
        }
        if (sel.length == 1 && selectedFile != null && selectedFile.isFile() && selectedFile.getParentFile() != null) {
            OPEN_IN_FOLDER_ACTION.setEnabled(true);
        } else {
            OPEN_IN_FOLDER_ACTION.setEnabled(false);
        }
        if (sel.length == 1) {
            LibraryMediator.instance().getLibraryCoverArtPanel().setTagsReader(new TagsReader(selectedFile)).asyncRetrieveImage();
        }
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
    }

    @SuppressWarnings("UnusedReturnValue")
    boolean setFileSelected(File file) {
        int i = DATA_MODEL.getRow(file);
        if (i != -1) {
            TABLE.setSelectedRow(i);
            TABLE.ensureSelectionVisible();
            return true;
        }
        return false;
    }
    ///////////////////////////////////////////////////////
    //  ACTIONS
    ///////////////////////////////////////////////////////

    private boolean hasExploreAction() {
        return OSUtils.isWindows() || OSUtils.isMacOSX();
    }

    @Override
    protected void sortAndMaintainSelection(int columnToSort) {
        super.sortAndMaintainSelection(columnToSort);
        resetAudioPlayerFileView();
    }

    void resetAudioPlayerFileView() {
        Playlist playlist = MediaPlayer.instance().getCurrentPlaylist();
        if (playlist == null) {
            MediaPlayer.instance().setPlaylistFilesView(getFilesView());
        }
    }

    @Override
    protected MediaSource createMediaSource(LibraryFilesTableDataLine line) {
        if (MediaPlayer.isPlayableFile(line.getInitializeObject())) {
            return new MediaSource(line.getInitializeObject());
        } else {
            return null;
        }
    }

    /**
     * Sets an icon based on the filename extension.
     */
    private static class FileNameListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String extension = FilenameUtils.getExtension(value.toString());
            if (extension != null) {
                setIcon(IconManager.instance().getIconForExtension(extension));
            }
            return this;
        }
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
            if (selectedFile.isDirectory() && selectedFile.listFiles().length == 0) {
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

    private final class RemoveAction extends AbstractAction {
        /**
         *
         */
        private static final long serialVersionUID = -8704093935791256631L;

        RemoveAction() {
            putValue(Action.NAME, I18n.tr("Delete"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Delete Selected Files"));
            putValue(LimeAction.ICON_NAME, "LIBRARY_DELETE");
        }

        public void actionPerformed(ActionEvent ae) {
            REMOVE_LISTENER.actionPerformed(ae);
        }
    }

    private class SendAudioFilesToiTunes extends AbstractAction {
        private static final long serialVersionUID = 4726989286129406765L;

        SendAudioFilesToiTunes() {
            if (!OSUtils.isLinux()) {
                String actionName = I18n.tr("Send to iTunes");
                String shortDescription = I18n.tr("Send audio files to iTunes");
                if (OSUtils.isMacOSCatalina105OrNewer()) {
                    actionName = I18n.tr("Send to Apple Music");
                    shortDescription = I18n.tr("Send audio files to Apple Music");
                }

                putValue(Action.NAME, actionName);
                putValue(Action.SHORT_DESCRIPTION, shortDescription);
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            int[] rows = TABLE.getSelectedRows();
            List<File> files = new ArrayList<>();
            for (int index : rows) {
                File file = DATA_MODEL.getFile(index);
                files.add(file);
            }
            iTunesMediator.instance().scanForSongs(files.toArray(new File[0]));
        }
    }

    private class DemuxMP4AudioAction extends AbstractAction {
        private static final long serialVersionUID = 2994040746359495494L;
        private final ArrayList<File> demuxedFiles;
        private boolean isDemuxing = false;

        DemuxMP4AudioAction() {
            putValue(Action.NAME, I18n.tr("Extract Audio"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Extract .m4a Audio from this .mp4 video"));
            demuxedFiles = new ArrayList<>();
        }

        boolean isDemuxing() {
            return isDemuxing;
        }

        private List<File> getSelectedFiles() {
            int[] rows = TABLE.getSelectedRows();
            List<File> files = new ArrayList<>(rows.length);
            for (int index : rows) {
                File file = DATA_MODEL.getFile(index);
                files.add(file);
            }
            return files;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            final short videoCount = (short) TABLE.getSelectedRows().length;
            //can't happen, but just in case.
            if (videoCount < 1) {
                return;
            }
            //get selected files before we switch to audio and loose the selection
            final List<File> selectedFiles = getSelectedFiles();
            selectAudio();
            String status = I18n.tr("Extracting audio from " + videoCount + " selected videos...");
            if (videoCount == 1) {
                status = I18n.tr("Extracting audio from selected video...");
            }
            LibraryMediator.instance().getLibrarySearch().pushStatus(status);
            SwingWorker<Void, Void> demuxWorker = new SwingWorker<>() {
                @SuppressWarnings("RedundantThrows")
                @Override
                protected Void doInBackground() throws Exception {
                    isDemuxing = true;
                    demuxFiles(selectedFiles);
                    isDemuxing = false;
                    return null;
                }

                @Override
                protected void done() {
                    int failed = videoCount - demuxedFiles.size();
                    String failedStr = (failed > 0) ? " (" + failed + " " + I18n.tr("failed") + ")" : "";
                    LibraryMediator.instance().getLibrarySearch().pushStatus(I18n.tr("Done extracting audio.") + failedStr);
                }
            };
            demuxWorker.execute();
        }

        private void selectAudio() {
            final LibraryExplorer explorer = LibraryMediator.instance().getLibraryExplorer();
            explorer.enqueueRunnable(explorer::selectAudio);
            explorer.executePendingRunnables();
        }

        private void demuxFiles(final List<File> files) {
            demuxedFiles.clear();
            for (final File file : files) {
                try {
                    System.out.println("Demuxing file " + file.getAbsolutePath());
                    File mp4 = file.getAbsoluteFile();
                    File m4a = new File(file.getParentFile(), FilenameUtils.getBaseName(mp4.getName()) + ".m4a").getAbsoluteFile();
                    try {
                        Mp4Info inf = Mp4Info.audio(null, null, null, null);
                        Mp4Demuxer.audio(mp4, m4a, inf, null);
                        demuxedFiles.add(m4a);
                        updateDemuxingStatus(m4a, files.size(), true);
                    } catch (Throwable e) {
                        updateDemuxingStatus(file, files.size(), false);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private void updateDemuxingStatus(final File demuxed, final int totalDemuxed, final boolean demuxSuccess) {
            GUIMediator.safeInvokeAndWait(() -> {
                LibraryExplorer explorer = LibraryMediator.instance().getLibraryExplorer();
                explorer.enqueueRunnable(() -> {
                    if (demuxSuccess) {
                        add(demuxed, 0);
                        update(demuxed);
                        LibraryMediator.instance().getLibrarySearch().pushStatus(I18n.tr("Finished") + " " + demuxedFiles.size() + " " + I18n.tr("out of") + " " + totalDemuxed + ". Extracting audio...");
                        System.out.println("Finished" + demuxedFiles.size() + " out of " + totalDemuxed + ". Extracting audio...");
                    } else {
                        LibraryMediator.instance().getLibrarySearch().pushStatus(I18n.tr("Could not extract audio from") + " " + demuxed.getName());
                    }
                });
                explorer.executePendingRunnables();
            });
        }
    }

    /**
     * Renders the file part of the Tuple<File, FileDesc> in CheckBoxList<Tuple<File, FileDesc>>.
     */
    private class FileTextProvider implements CheckBoxList.TextProvider<File> {
        public Icon getIcon(File obj) {
            String extension = FilenameUtils.getExtension(obj.getName());
            if (extension != null) {
                return IconManager.instance().getIconForExtension(extension);
            }
            return null;
        }

        public String getText(File obj) {
            return getCompleteFileName(obj);
        }

        public String getToolTipText(File obj) {
            return obj.getAbsolutePath();
        }
    }
}
