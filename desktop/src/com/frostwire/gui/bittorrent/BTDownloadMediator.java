/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.bittorrent;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.gui.bittorrent.BTDownloadActions.PlaySingleMediaFileAction;
import com.frostwire.gui.components.slides.Slide;
import com.frostwire.gui.filters.TableLineFilter;
import com.frostwire.gui.library.LibraryUtils;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.theme.SkinMenu;
import com.frostwire.gui.theme.SkinMenuItem;
import com.frostwire.gui.theme.SkinPopupMenu;
import com.frostwire.logging.Logger;
import com.frostwire.search.soundcloud.*;
import com.frostwire.search.torrent.TorrentCrawledSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.search.youtube.YouTubeCrawledSearchResult;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.http.HttpClient;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.PaddedPanel;
import com.limegroup.gnutella.gui.actions.LimeAction;
import com.limegroup.gnutella.gui.iTunesMediator;
import com.limegroup.gnutella.gui.search.GenericCellEditor;
import com.limegroup.gnutella.gui.tables.AbstractTableMediator;
import com.limegroup.gnutella.gui.tables.LimeJTable;
import com.limegroup.gnutella.gui.tables.LimeTableColumn;
import com.limegroup.gnutella.gui.tables.TableSettings;
import com.limegroup.gnutella.settings.*;
import org.limewire.util.FileUtils;
import org.limewire.util.OSUtils;

import javax.swing.*;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * This class acts as a mediator between all of the components of the
 * download window.  It also constructs all of the download window
 * components.
 *
 * @author gubatron
 * @author aldenml
 */
public final class BTDownloadMediator extends AbstractTableMediator<BTDownloadRowFilteredModel, BTDownloadDataLine, BTDownload> {

    private static final Logger LOG = Logger.getLogger(BTDownloadMediator.class);

    public static final int MIN_HEIGHT = 150;

    /**
     * instance, for singleton access
     */
    private static BTDownloadMediator INSTANCE;

    public static BTDownloadMediator instance() {
        if (INSTANCE == null) {
            INSTANCE = new BTDownloadMediator();
        }
        return INSTANCE;
    }

    /**
     * Variables so only one ActionListener needs to be created for both
     * the buttons & popup menu.
     */
    private Action removeAction;
    private Action removeYouTubeAction;
    private Action resumeAction;
    private Action pauseAction;
    private Action exploreAction;
    private Action copyMagnetAction;
    private Action copyHashAction;
    private Action shareTorrentAction;
    private Action showInLibraryAction;
    private Action clearInactiveAction;

    /**
     * The actual download buttons instance.
     */
    private BTDownloadButtons _downloadButtons;
    private SeedingFilter _seedingFilter;

    private Action sendToItunesAction;

    private PlaySingleMediaFileAction playSingleMediaFileAction;

    /**
     * Overriden to have different default values for tooltips.
     */
    protected void buildSettings() {
        SETTINGS = new TableSettings(ID) {
            public boolean getDefaultTooltips() {
                return false;
            }
        };
    }

    /**
     * Sets up drag & drop for the table.
     */
    protected void setupDragAndDrop() {
        TABLE.setDragEnabled(true);
        TABLE.setTransferHandler(new BTDownloadTransferHandler());
    }

    /**
     * Build some extra listeners
     */
    protected void buildListeners() {
        super.buildListeners();

        clearInactiveAction = BTDownloadActions.CLEAR_INACTIVE_ACTION;
        removeAction = BTDownloadActions.REMOVE_ACTION;
        removeYouTubeAction = BTDownloadActions.REMOVE_YOUTUBE_ACTION;
        resumeAction = BTDownloadActions.RESUME_ACTION;
        pauseAction = BTDownloadActions.PAUSE_ACTION;
        exploreAction = BTDownloadActions.EXPLORE_ACTION;
        showInLibraryAction = BTDownloadActions.SHOW_IN_LIBRARY_ACTION;
        copyMagnetAction = BTDownloadActions.COPY_MAGNET_ACTION;
        copyHashAction = BTDownloadActions.COPY_HASH_ACTION;
        shareTorrentAction = BTDownloadActions.SHARE_TORRENT_ACTION;
        sendToItunesAction = BTDownloadActions.SEND_TO_ITUNES_ACTION;
        playSingleMediaFileAction = BTDownloadActions.PLAY_SINGLE_AUDIO_FILE_ACTION;
    }

    /**
     * Returns the most prominent actions that operate on the download table.
     *
     * @return
     */
    public Action[] getActions() {
        return new Action[]{resumeAction, pauseAction, showInLibraryAction, exploreAction, removeAction, clearInactiveAction};
    }

    /**getActionsgetActions
     * Set up the necessary constants.
     */
    protected void setupConstants() {
        MAIN_PANEL = new PaddedPanel(I18n.tr("Transfers"));
        _seedingFilter = new SeedingFilter();
        DATA_MODEL = new BTDownloadRowFilteredModel(_seedingFilter);//new BTDownloadModel();
        TABLE = new LimeJTable(DATA_MODEL);
        TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        _downloadButtons = new BTDownloadButtons(this);
        BUTTON_ROW = _downloadButtons.getComponent();

        updateTableFilters();
    }

    /**
     * Filter out all the models who are being seeded.
     *
     * @author gubatron
     */
    class SeedingFilter implements TableLineFilter<BTDownloadDataLine> {
        @Override
        public boolean allow(BTDownloadDataLine node) {
            if (ApplicationSettings.SHOW_SEEDING_TRANSFERS.getValue()) {
                return true;
            }

            if (node == null) {
                return false;
            }

            return !node.isSeeding();
        }
    }

    public void updateTableFilters() {

        if (TABLE == null || DATA_MODEL == null) {
            return;
        }

        DATA_MODEL.filtersChanged();
    }

    /**
     * Notification that a filter on this panel has changed.
     * <p/>
     * Updates the data model with the new list, maintains the selection,
     * and moves the viewport to the first still visible selected row.
     */
    boolean filterChanged() {
        // store the selection & visible rows
        int[] rows = TABLE.getSelectedRows();
        BTDownloadDataLine[] lines = new BTDownloadDataLine[rows.length];
        List<BTDownloadDataLine> inView = new LinkedList<BTDownloadDataLine>();
        for (int i = 0; i < rows.length; i++) {
            int row = rows[i];
            BTDownloadDataLine line = DATA_MODEL.get(row);
            lines[i] = line;
            if (TABLE.isRowVisible(row))
                inView.add(line);
        }

        // change the table.
        DATA_MODEL.filtersChanged();

        // reselect & move the viewpoint to the first still visible row.
        for (int i = 0; i < rows.length; i++) {
            BTDownloadDataLine line = lines[i];
            int row = DATA_MODEL.getRow(line);
            if (row != -1) {
                TABLE.addRowSelectionInterval(row, row);
                if (inView != null && inView.contains(line)) {
                    TABLE.ensureRowVisible(row);
                    inView = null;
                }
            }
        }

        return true;
    }

    /**
     * Update the splash screen.
     */
    protected void updateSplashScreen() {
        GUIMediator.setSplashScreenString(I18n.tr("Loading Download Window..."));
    }

    /**
     * Constructs all of the elements of the download window, including
     * the table, the buttons, etc.
     */
    private BTDownloadMediator() {
        super("DOWNLOAD_TABLE");
        TABLE.setRowHeight(30);
        GUIMediator.addRefreshListener(this);
        restoreSorting();
    }

    /**
     * Override the default refreshing so that we can
     * set the clear button appropriately.
     */
    public void doRefresh() {
        DATA_MODEL.refresh();

        int[] selRows = TABLE.getSelectedRows();

        if (selRows.length > 0) {
            BTDownloadDataLine dataLine = DATA_MODEL.get(selRows[0]);

            BTDownload dl = dataLine.getInitializeObject();
            boolean completed = dl.isCompleted();

            resumeAction.setEnabled(dl.isResumable());
            pauseAction.setEnabled(dl.isPausable());
            exploreAction.setEnabled(completed);
            showInLibraryAction.setEnabled(completed);
        }

        int n = DATA_MODEL.getRowCount();
        boolean anyClearable = false;
        for (int i = n - 1; i >= 0; i--) {
            BTDownloadDataLine btDownloadDataLine = DATA_MODEL.get(i);
            BTDownload initializeObject = btDownloadDataLine.getInitializeObject();
            if (isClearable(initializeObject)) {
                anyClearable = true;
                break;
            }
        }

        clearInactiveAction.setEnabled(anyClearable);

        try {
            if (OSUtils.isWindows() && UpdateManagerSettings.SHOW_FROSTWIRE_RECOMMENDATIONS.getValue()) {
                //TipsClient.instance().call();
            }
        } catch (Throwable e) {
            LOG.debug("Error using tips framework: " + e.getMessage());
        }
    }

    public int getActiveDownloads() {
        return DATA_MODEL.getActiveDownloads();
    }

    public int getActiveUploads() {
        return DATA_MODEL.getActiveUploads();
    }

    public int getTotalDownloads() {
        return DATA_MODEL.getTotalDownloads();
    }

    /**
     * Returns the aggregate amount of bandwidth being consumed by active downloads.
     *
     * @return the total amount of bandwidth being consumed by active downloads.
     */
    private double getBandwidth(boolean download) {
        BTEngine engine = BTEngine.getInstance();
        double totalBandwidth = download ? engine.getDownloadRate() : engine.getUploadRate();
        if (download) {
            double httpBandwidth = 0;
            for (BTDownload btDownload : this.getDownloads()) {
                if (btDownload instanceof HttpDownload ||
                    btDownload instanceof SoundcloudDownload ||
                    btDownload instanceof YouTubeDownload) {
                    httpBandwidth += btDownload.getDownloadSpeed();
                }
            }
            httpBandwidth = httpBandwidth * 1000;
            totalBandwidth += httpBandwidth;
        }
        return totalBandwidth;
    }

    public double getDownloadsBandwidth() {
        return (getBandwidth(true)) / 1000;
    }

    public double getUploadsBandwidth() {
        return getBandwidth(false) / 1000;
    }

    /**
     * Overrides the default add.
     * <p/>
     * Adds a new Downloads to the list of Downloads, obtaining the necessary
     * information from the supplied <tt>Downloader</tt>.
     * <p/>
     * If the download is not already in the list, then it is added.
     * <p/>
     */
    public void add(BTDownload downloader) {
        if (!DATA_MODEL.contains(downloader)) {
            super.add(downloader, DATA_MODEL.getRowCount());
            if (DATA_MODEL.getRowCount() > 0) {
                int row = DATA_MODEL.getRow(downloader);
                if (row != -1) {
                    TABLE.setSelectedRow(row);
                    TABLE.ensureSelectionVisible();
                }
            }
        }
    }

    /**
     * Overrides the default remove.
     * <p/>
     * Takes action upon downloaded theme files, asking if the user wants to
     * apply the theme.
     * <p/>
     * Removes a download from the list if the user has configured their system
     * to automatically clear completed download and if the download is
     * complete.
     *
     * @param dloader the <tt>Downloader</tt> to remove from the list if it is
     *                complete.
     */
    public void remove(BTDownload dloader) {
        super.remove(dloader);
        dloader.remove();
    }

    /**
     * Launches the selected files in the <tt>Launcher</tt> or in the built-in
     * media player.
     */
    void launchSelectedDownloads() {
        //        int[] sel = TABLE.getSelectedRows();
        //        if (sel.length == 0) {
        //        	return;
        //        }
        //        LaunchableProvider[] providers = new LaunchableProvider[sel.length];
        //        for (int i = 0; i < sel.length; i++) {
        //        	providers[i] = new DownloaderProvider(DATA_MODEL.get(sel[i]).getDownloader());
        //        }
        //        GUILauncher.launch(providers);
    }

    /**
     * Pauses all selected downloads.
     */
    void pauseSelectedDownloads() {
        int[] sel = TABLE.getSelectedRows();
        for (int i = 0; i < sel.length; i++) {
            DATA_MODEL.get(sel[i]).getInitializeObject().pause();
        }
    }

    /**
     * Launches explorer
     */
    void launchExplorer() {
        int[] sel = TABLE.getSelectedRows();
        BTDownload dl = DATA_MODEL.get(sel[sel.length - 1]).getInitializeObject();
        File toExplore = dl.getSaveLocation();

        if (toExplore == null) {
            return;
        }

        GUIMediator.launchExplorer(toExplore);
    }

    public BTDownload[] getSelectedBTDownloads() {
        int[] sel = TABLE.getSelectedRows();
        ArrayList<BTDownload> btdownloadList = new ArrayList<BTDownload>(sel.length);
        for (int i = 0; i < sel.length; i++) {
            BTDownloadDataLine btDownloadDataLine = DATA_MODEL.get(sel[i]);
            if (btDownloadDataLine.getInitializeObject().isCompleted()) {
                btdownloadList.add(btDownloadDataLine.getInitializeObject());
            }
        }
        return btdownloadList.toArray(new BTDownload[btdownloadList.size()]);
    }

    /**
     * Handles a double-click event in the table.
     */
    public void handleActionKey() {

        BTDownload[] selectedDownloaders = getSelectedDownloaders();

        if (selectedDownloaders.length == 1) {
            playSingleMediaFileAction.setEnabled(selectionHasMediaFiles(selectedDownloaders[0]));
        }

        if (playSingleMediaFileAction.isEnabled()) {
            playSingleMediaFileAction.actionPerformed(null);
        }

        if (showInLibraryAction.isEnabled()) {
            showInLibraryAction.actionPerformed(null);
        }
    }

    protected JPopupMenu createPopupMenu() {

        JPopupMenu menu = new SkinPopupMenu();

        if (playSingleMediaFileAction.isEnabled()) {
            menu.add(new SkinMenuItem(playSingleMediaFileAction));
        }

        menu.add(new SkinMenuItem(resumeAction));
        menu.add(new SkinMenuItem(pauseAction));

        menu.addSeparator();
        menu.add(new SkinMenuItem(showInLibraryAction));
        menu.add(new SkinMenuItem(exploreAction));

        menu.addSeparator();
        menu.add(new SkinMenuItem(shareTorrentAction));

        if (OSUtils.isMacOSX() || OSUtils.isWindows()) {
            menu.add(new SkinMenuItem(sendToItunesAction));
        }

        menu.add(new SkinMenuItem(copyMagnetAction));
        menu.add(new SkinMenuItem(copyHashAction));
        SkinMenu addToPlaylistMenu = BTDownloadMediatorAdvancedMenuFactory.createAddToPlaylistSubMenu();
        if (addToPlaylistMenu != null) {
            menu.add(addToPlaylistMenu);
        }
        menu.addSeparator();
        menu.add(new SkinMenuItem(removeAction));
        menu.add(new SkinMenuItem(BTDownloadActions.REMOVE_TORRENT_ACTION));
        menu.add(new SkinMenuItem(BTDownloadActions.REMOVE_TORRENT_AND_DATA_ACTION));
        menu.add(new SkinMenuItem(removeYouTubeAction));

        menu.addSeparator();

        menu.add(new SkinMenuItem(BTDownloadActions.TOGGLE_SEEDS_VISIBILITY_ACTION));

        SkinMenu advancedMenu = BTDownloadMediatorAdvancedMenuFactory.createAdvancedSubMenu();
        if (advancedMenu != null) {
            menu.addSeparator();
            menu.add(advancedMenu);
        }

        return menu;
    }

    /**
     * Handles the selection of the specified row in the download window,
     * enabling or disabling buttons and chat menu items depending on
     * the values in the row.
     *
     * @param row the selected row
     */
    public void handleSelection(int row) {

        BTDownloadDataLine dataLine = DATA_MODEL.get(row);

        boolean pausable = dataLine.getInitializeObject().isPausable();
        boolean resumable = dataLine.getInitializeObject().isResumable();
        boolean isTransferFinished = dataLine.getInitializeObject().isCompleted();

        File saveLocation = dataLine.getInitializeObject().getSaveLocation();

        boolean hasMediaFiles = selectionHasMediaFiles(dataLine.getInitializeObject());
        boolean hasMP4s = selectionHasMP4s(saveLocation);

        boolean isSingleFile = selectionIsSingleFile(saveLocation);

        removeAction.putValue(Action.NAME, I18n.tr("Cancel Download"));
        removeAction.putValue(LimeAction.SHORT_NAME, I18n.tr("Cancel"));
        removeAction.putValue(Action.SHORT_DESCRIPTION, I18n.tr("Cancel Selected Downloads"));

        BTDownload dl = dataLine.getInitializeObject();

        exploreAction.setEnabled(dl.isCompleted());
        showInLibraryAction.setEnabled(dl.isCompleted());

        removeAction.setEnabled(true);
        resumeAction.setEnabled(resumable);
        pauseAction.setEnabled(pausable);
        copyMagnetAction.setEnabled(!isHttpTransfer(dataLine.getInitializeObject()));
        copyHashAction.setEnabled(!isHttpTransfer(dataLine.getInitializeObject()));

        sendToItunesAction.setEnabled(isTransferFinished && (hasMediaFiles || hasMP4s));

        shareTorrentAction.setEnabled(getSelectedDownloaders().length == 1 && dataLine.getInitializeObject().isPausable());

        playSingleMediaFileAction.setEnabled(getSelectedDownloaders().length == 1 && hasMediaFiles && isSingleFile);

        removeYouTubeAction.setEnabled(isHttpTransfer(dataLine.getInitializeObject()));

        BTDownloadActions.REMOVE_TORRENT_ACTION.setEnabled(!isHttpTransfer(dataLine.getInitializeObject()));
        BTDownloadActions.REMOVE_TORRENT_AND_DATA_ACTION.setEnabled(!isHttpTransfer(dataLine.getInitializeObject()));
    }

    private boolean selectionHasMP4s(File saveLocation) {
        boolean hasMP4Files = saveLocation != null
                && (LibraryUtils.directoryContainsExtension(saveLocation, 4, "mp4") || (saveLocation.isFile() && FileUtils.hasExtension(saveLocation.getAbsolutePath(), "mp4")));
        return hasMP4Files;
    }

    private boolean selectionIsSingleFile(File saveLocation) {
        boolean isSingleFile = saveLocation != null && saveLocation.isFile();
        return isSingleFile;
    }

    private boolean selectionHasMediaFiles(BTDownload d) {
        if (d instanceof SoundcloudDownload) {
            return true;
        }
        File saveLocation = d.getSaveLocation();

        //in case it's a single picked .torrent/magnet download
        if (saveLocation != null && saveLocation.isDirectory() && LibraryUtils.directoryContainsASinglePlayableFile(saveLocation, 4)) {
            try {
                saveLocation = saveLocation.listFiles()[0];
            } catch (Throwable t) {
                saveLocation = null;
            }
        }

        boolean hasPlayableFiles = saveLocation != null && (LibraryUtils.directoryContainsPlayableExtensions(saveLocation, 4) || (saveLocation.isFile() && MediaPlayer.isPlayableFile(saveLocation)));
        return hasPlayableFiles;
    }

    private boolean isHttpTransfer(BTDownload d) {
        return isYouTubeTransfer(d) || d instanceof SoundcloudDownload || d instanceof HttpDownload;
    }

    private boolean isYouTubeTransfer(BTDownload d) {
        return d instanceof YouTubeDownload;
    }

    /**
     * Handles the deselection of all rows in the download table,
     * disabling all necessary buttons and menu items.
     */
    public void handleNoSelection() {
        removeAction.setEnabled(false);
        resumeAction.setEnabled(false);
        clearInactiveAction.setEnabled(false);
        pauseAction.setEnabled(false);
        exploreAction.setEnabled(false);
        showInLibraryAction.setEnabled(false);
        copyMagnetAction.setEnabled(false);
        copyHashAction.setEnabled(false);
        shareTorrentAction.setEnabled(false);
        sendToItunesAction.setEnabled(false);
        playSingleMediaFileAction.setEnabled(false);

        BTDownloadActions.REMOVE_TORRENT_ACTION.setEnabled(false);
        BTDownloadActions.REMOVE_TORRENT_AND_DATA_ACTION.setEnabled(false);
        removeYouTubeAction.setEnabled(false);
    }

    public void openTorrentURI(final String uri, final boolean partialDownload) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                BTDownload downloader = new TorrentFetcherDownload(uri, partialDownload);
                add(downloader);
            }
        });
    }

    public void openTorrentFileForSeed(final File torrentFile, final File saveDir) {
        GUIMediator.safeInvokeLater(new Runnable() {
            public void run() {
                try {
                    BTEngine.getInstance().download(torrentFile, saveDir);
                } catch (Throwable e) {
                    e.printStackTrace();
                    if (!e.toString().contains("No files selected by user")) {
                        // could not read torrent file or bad torrent file.
                        GUIMediator.showError(
                                I18n.tr("FrostWire was unable to load the torrent file \"{0}\", - it may be malformed or FrostWire does not have permission to access this file.",
                                        torrentFile.getName()), QuestionsHandler.TORRENT_OPEN_FAILURE);
                    }
                }

            }
        });
    }

    @Override
    protected void setDefaultRenderers() {
        super.setDefaultRenderers();
        TABLE.setDefaultRenderer(PaymentOptions.class, new PaymentOptionsRenderer());
        TABLE.setDefaultRenderer(TransferHolder.class, new TransferActionsRenderer());
    }

    @Override
    protected void setDefaultEditors() {
        TableColumnModel model = TABLE.getColumnModel();
        TableColumn tc;

        tc = model.getColumn(BTDownloadDataLine.PAYMENT_OPTIONS_INDEX);
        tc.setCellEditor(new GenericCellEditor(new PaymentOptionsRenderer()));

        tc = model.getColumn(BTDownloadDataLine.ACTIONS_INDEX);
        tc.setCellEditor(new GenericCellEditor(getTransferActionsRenderer()));
    }

    public void openTorrentFile(final File torrentFile, final boolean partialDownload, final Runnable onOpenRunnableForUIThread) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {

                    boolean[] filesSelection = null;

                    if (partialDownload) {
                        PartialFilesDialog dlg = new PartialFilesDialog(GUIMediator.getAppFrame(), torrentFile);
                        dlg.setVisible(true);
                        filesSelection = dlg.getFilesSelection();
                        if (filesSelection == null) {
                            return;
                        }

                        if (onOpenRunnableForUIThread != null) {
                            onOpenRunnableForUIThread.run();
                        }
                    }

                    File saveDir = null;

                    // Check if there's a file named like the torrent in the same folder
                    // then that means the user wants to seed
                    String seedDataFilename = torrentFile.getName().replace(".torrent", "");
                    File seedDataFile = new File(torrentFile.getParentFile(), seedDataFilename);
                    if (seedDataFile.exists()) {
                        saveDir = torrentFile.getParentFile();
                    }

                    if (saveDir == null) {
                        BTEngine.getInstance().download(torrentFile, null, filesSelection);
                    } else {
                        GUIMediator.instance().openTorrentForSeed(torrentFile, saveDir);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    if (!e.toString().contains("No files selected by user")) {
                        // could not read torrent file or bad torrent file.
                        GUIMediator.showError(
                                I18n.tr("FrostWire was unable to load the torrent file \"{0}\", - it may be malformed or FrostWire does not have permission to access this file.",
                                        torrentFile.getName()), QuestionsHandler.TORRENT_OPEN_FAILURE);
                        //System.out.println("***Error happened from Download Mediator: " +  ioe);
                        //GUIMediator.showMessage("Error was: " + ioe); //FTA: debug
                    }
                }
            }
        });
    }

    public void openSearchResult(TorrentCrawledSearchResult sr) {
        try {
            BTEngine.getInstance().download(sr, SharingSettings.TORRENT_DATA_DIR_SETTING.getValue());
        } catch (Throwable e) {
            LOG.error("Unable to start download from search result", e);
        }
    }

    public void openTorrentSearchResult(final TorrentSearchResult sr, final boolean partialDownload) {
        GUIMediator.safeInvokeLater(new Runnable() {
            public void run() {
                add(new TorrentFetcherDownload(sr.getTorrentUrl(), sr.getReferrerUrl(), sr.getDisplayName(), partialDownload));
            }
        });
    }

    public BTDownload[] getSelectedDownloaders() {
        int[] sel = TABLE.getSelectedRows();
        ArrayList<BTDownload> downloaders = new ArrayList<BTDownload>(sel.length);
        for (int i = 0; i < sel.length; i++) {
            BTDownloadDataLine line = DATA_MODEL.get(sel[i]);
            BTDownload downloader = line.getInitializeObject();
            downloaders.add(downloader);
        }
        return downloaders.toArray(new BTDownload[0]);
    }

    public List<BTDownload> getDownloads() {
        int count = TABLE.getRowCount();
        List<BTDownload> downloads = new ArrayList<BTDownload>(count);
        for (int i = 0; i < count; i++) {
            try {
                if (i < DATA_MODEL.getRowCount()) {
                    BTDownloadDataLine line = DATA_MODEL.get(i);
                    BTDownload downloader = line.getInitializeObject();
                    downloads.add(downloader);
                }
            } catch (Throwable t) {
                //saw user with 771 downloads
                //perhaps deleted one, and by the time this finished
                //iterating this threw an IndexOutOfBounds exception
            }
        }
        return downloads;
    }

    public long getTotalBytesDownloaded() {
        BTEngine engine = BTEngine.getInstance();
        return engine.getTotalDownload();
    }

    public long getTotalBytesUploaded() {
        BTEngine engine = BTEngine.getInstance();
        return engine.getTotalUpload();
    }

    public boolean isClearable(BTDownload initializeObject) {
        TransferState state = initializeObject.getState();
        return state != TransferState.SEEDING && state != TransferState.CHECKING && initializeObject.isCompleted();
    }

    public void removeCompleted() {
        int n = DATA_MODEL.getRowCount();
        for (int i = n - 1; i >= 0; i--) {
            BTDownloadDataLine btDownloadDataLine = DATA_MODEL.get(i);
            BTDownload initializeObject = btDownloadDataLine.getInitializeObject();

            if (isClearable(initializeObject)) {
                DATA_MODEL.remove(i);
            }
        }
    }

    public void stopCompleted() {
        int n = DATA_MODEL.getRowCount();
        for (int i = n - 1; i >= 0; i--) {
            BTDownloadDataLine btDownloadDataLine = DATA_MODEL.get(i);
            BTDownload initializeObject = btDownloadDataLine.getInitializeObject();
            if (initializeObject.isCompleted()) {
                initializeObject.pause();
            }
        }
    }

    public boolean isDownloading(String hash) {
        return DATA_MODEL.isDownloading(hash);
    }


    public void addDownload(com.frostwire.bittorrent.BTDownload dl) {
        try {
            add(new BittorrentDownload(dl));
        } catch (Throwable e) {
            LOG.error("Error adding bittorrent download", e);
        }
    }

    /**
     * Load from the last settings saved the previous sorting preferences of this mediator.
     */
    public void restoreSorting() {
        int sortIndex = BittorrentSettings.BTMEDIATOR_COLUMN_SORT_INDEX.getValue();
        boolean sortOrder = BittorrentSettings.BTMEDIATOR_COLUMN_SORT_ORDER.getValue();

        LimeTableColumn column = BTDownloadDataLine.staticGetColumn(sortIndex);

        if (sortIndex != -1 && column != null && TablesHandlerSettings.getVisibility(column.getId(), column.getDefaultVisibility()).getValue()) {
            DATA_MODEL.sort(sortIndex); //ascending

            if (!sortOrder) { //descending
                DATA_MODEL.sort(sortIndex);
            }
        } else {
            DATA_MODEL.sort(BTDownloadDataLine.DATE_CREATED_INDEX);
        }
    }

    public void downloadSoundcloudFromTrackUrlOrSearchResult(final String trackUrl, final SoundcloudSearchResult sr) {
        if (sr != null) {
            GUIMediator.safeInvokeLater(new Runnable() {
                public void run() {
                    if (isDownloading(sr.getDownloadUrl())) {
                        DATA_MODEL.remove(sr.getDownloadUrl());
                        doRefresh();
                        return;
                    }
                    BTDownload downloader = new SoundcloudDownload(sr);
                    add(downloader);
                }
            });
        } else if (trackUrl != null) {
            //resolve track information using http://api.soundcloud.com/resolve?url=<url>&client_id=b45b1aa10f1ac2941910a7f0d10f8e28
            final String clientId = SoundcloudSearchPerformer.SOUNDCLOUD_CLIENTID;
            final String appVersion = SoundcloudSearchPerformer.SOUNDCLOUD_APP_VERSION;
            try {
                String url = trackUrl;
                if (trackUrl.contains("?in=")) {
                    url = trackUrl.substring(0, trackUrl.indexOf("?in="));
                }

                final String resolveURL = "http://api.soundcloud.com/resolve.json?url=" + url + "&client_id=" + clientId + "&app_version=" + appVersion;
                System.out.println("resolve: " + resolveURL);

                HttpClient client = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD);
                final String json = client.get(resolveURL, 10000);
                //System.out.println(json);

                if (json.contains("\"status\":\"30")) {
                    try {
                        System.out.println("Soundcloud Redirection! >> " + json);
                        final SoundCloudRedirectResponse redirectResponse = JsonUtils.toObject(json, SoundCloudRedirectResponse.class);
                        final String redirectedJson = client.get(redirectResponse.location, 10000);
                        //System.out.println(redirectedJson);
                        downloadSoundcloudSetOrTrack(clientId, appVersion, url, redirectedJson);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                } else {
                    downloadSoundcloudSetOrTrack(clientId, appVersion, url, json);
                }

            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    private void downloadSoundcloudSetOrTrack(final String clientId, final String appVersion, String url, final String json) {
        if (url.contains("/sets/")) {
            downloadSoundcloudSet(clientId, appVersion, url, json);
        } else {
            downloadSoundcloudTrack(clientId, appVersion, url, json);
        }
    }

    private void downloadSoundcloudTrack(final String clientId, final String appVersion, String url, final String json) {
        //download single track
        final SoundcloudItem scItem = JsonUtils.toObject(json, SoundcloudItem.class);
        if (scItem != null) {
            SoundcloudSearchResult srNew = new SoundcloudSearchResult(scItem, clientId, appVersion);
            downloadSoundcloudFromTrackUrlOrSearchResult(url, srNew);
        }
    }

    private void downloadSoundcloudSet(final String clientId, final String appVersion, String url, final String json) {
        //download a whole playlist
        final SoundcloudPlaylist playlist = JsonUtils.toObject(json, SoundcloudPlaylist.class);

        if (playlist != null && playlist.tracks != null) {
            for (SoundcloudItem scItem : playlist.tracks) {
                if (scItem.downloadable) {
                    SoundcloudSearchResult srNew = new SoundcloudSearchResult(scItem, clientId, appVersion);
                    downloadSoundcloudFromTrackUrlOrSearchResult(url, srNew);
                }
            }
        }
    }

    public void openYouTubeItem(final YouTubeCrawledSearchResult sr) {
        GUIMediator.safeInvokeLater(new Runnable() {
            public void run() {
                if (!isDownloading(sr.getDownloadUrl())) {
                    BTDownload downloader = new YouTubeDownload(sr);
                    add(downloader);
                }
            }
        });
    }

    public void openSlide(final Slide slide) {
        GUIMediator.safeInvokeLater(new Runnable() {
            @Override
            public void run() {
                SlideDownload downloader = new SlideDownload(slide);
                add(downloader);
            }
        });
    }

    public void openHttp(final String httpUrl, final String title, final String saveFileAs, final long fileSize) {
        GUIMediator.safeInvokeLater(new Runnable() {
            @Override
            public void run() {
                HttpDownload downloader = new HttpDownload(httpUrl, title, saveFileAs, fileSize, null, false, true) {
                    @Override
                    protected void onComplete() {
                        final File savedFile = getSaveLocation();
                        if (savedFile.exists() && iTunesSettings.ITUNES_SUPPORT_ENABLED.getValue() && !iTunesMediator.instance().isScanned(savedFile)) {
                            if ((OSUtils.isMacOSX() || OSUtils.isWindows())) {
                                iTunesMediator.instance().scanForSongs(savedFile);
                            }
                        }
                    }
                };
                add(downloader);
            }
        });
    }
}
