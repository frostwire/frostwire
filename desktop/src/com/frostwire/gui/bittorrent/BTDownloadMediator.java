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

package com.frostwire.gui.bittorrent;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.gui.bittorrent.BTDownloadActions.PlaySingleMediaFileAction;
import com.frostwire.gui.components.slides.Slide;
import com.frostwire.gui.components.transfers.TransferDetailFiles;
import com.frostwire.gui.filters.TableLineFilter;
import com.frostwire.gui.library.LibraryFilesTableMediator;
import com.frostwire.gui.library.LibraryMediator;
import com.frostwire.gui.library.LibraryUtils;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.tabs.TransfersTab;
import com.frostwire.gui.theme.SkinMenu;
import com.frostwire.gui.theme.SkinMenuItem;
import com.frostwire.gui.theme.SkinPopupMenu;
import com.frostwire.mp4.Mp4Demuxer;
import com.frostwire.mp4.Mp4Info;
import com.frostwire.search.soundcloud.SoundcloudSearchPerformer;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.torrent.TorrentItemSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.PaddedPanel;
import com.limegroup.gnutella.gui.VPNDropGuard;
import com.limegroup.gnutella.gui.actions.LimeAction;
import com.limegroup.gnutella.gui.search.GenericCellEditor;
import com.limegroup.gnutella.gui.tables.AbstractTableMediator;
import com.limegroup.gnutella.gui.tables.LimeJTable;
import com.limegroup.gnutella.gui.tables.LimeTableColumn;
import com.limegroup.gnutella.gui.tables.TableSettings;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import com.limegroup.gnutella.settings.BittorrentSettings;
import com.limegroup.gnutella.settings.QuestionsHandler;
import com.limegroup.gnutella.settings.TablesHandlerSettings;
import org.apache.commons.io.FilenameUtils;
import org.limewire.util.FileUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class acts as a mediator between all the components of the
 * download window.  It also constructs all the download window
 * components.
 *
 * @author gubatron
 * @author aldenml
 */
public final class BTDownloadMediator extends AbstractTableMediator<BTDownloadRowFilteredModel, BTDownloadDataLine, BTDownload> implements TransfersTab.TransfersFilterModeListener {
    public static final int MIN_HEIGHT = 150;
    private static final Logger LOG = Logger.getLogger(BTDownloadMediator.class);
    /**
     * Instance, for singleton access
     */
    private static BTDownloadMediator INSTANCE;
    private BTDownloadSelectionListener transferTabSelectionListener;
    /**
     * Variables so only one ActionListener needs to be created for both
     * the buttons & popup menu.
     */
    private Action retryAction;
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
    private TransfersFilter transfersFilter;
    private PlaySingleMediaFileAction playSingleMediaFileAction;

    // coalesce selection updates; only last result applies
    private final AtomicInteger selectionSeq = new AtomicInteger();

    /**
     * Constructs all of the elements of the download window, including
     * the table, the buttons, etc.
     */
    private BTDownloadMediator() {
        super("DOWNLOAD_TABLE");
        TABLE.setRowHeight(30);
        GUIMediator.addRefreshListener(this);
        BackgroundQueuedExecutorService.schedule(this::restoreSorting);
    }

    public static BTDownloadMediator instance() {
        if (INSTANCE == null) {
            INSTANCE = new BTDownloadMediator();
        }
        return INSTANCE;
    }

    private static boolean isActive(BTDownload dl) {
        if (dl == null) {
            return false;
        }
        final TransferState state = dl.getState();
        return state == TransferState.CHECKING ||
                state == TransferState.DOWNLOADING ||
                state == TransferState.DOWNLOADING_METADATA ||
                state == TransferState.DOWNLOADING_TORRENT ||
                state == TransferState.SEEDING ||
                state == TransferState.UPLOADING;
    }

    public void selectBTDownload(BTDownload lastSelectedDownload) {
        if (lastSelectedDownload == null) {
            return;
        }
        BTDownloadDataLine btDownloadDataLine = DATA_MODEL.get(lastSelectedDownload);
        if (btDownloadDataLine == null) {
            return;
        }
        int row = DATA_MODEL.getRow(btDownloadDataLine);
        if (row == -1) {
            return;
        }
        TABLE.setSelectedRow(row);
        TABLE.ensureRowVisible(row);
    }

    public void ensureDownloadVisible(BTDownload download) {
        if (download == null) {
            return;
        }
        BTDownloadDataLine btDownloadDataLine = DATA_MODEL.get(download);
        if (btDownloadDataLine == null) {
            return;
        }
        int row = DATA_MODEL.getRow(btDownloadDataLine);
        if (row == -1) {
            return;
        }
        TABLE.validate();
        TABLE.ensureRowVisible(row);
    }

    /**
     * Overridden to have different default values for tooltips.
     */
    protected void buildSettings() {
        SETTINGS = new TableSettings(ID) {
            public boolean getDefaultTooltips() {
                return false;
            }
        };
    }

    /**
     * Sets up drag and drop for the table.
     */
    protected void setupDragAndDrop() {
        TABLE.setDragEnabled(true);
        TABLE.setTransferHandler(new BTDownloadTransferHandler());
    }

    @Override
    protected void buildListeners() {
        super.buildListeners();
        // YUP, if you try to do this by overriding super.addActions() you will get a crash
        // it's easier to just leave this here, let it go. :) -gubatron Mon Jun 18th 2018
        clearInactiveAction = BTDownloadActions.CLEAR_INACTIVE_ACTION;
        retryAction = BTDownloadActions.RETRY_ACTION;
        removeAction = BTDownloadActions.REMOVE_ACTION;
        removeYouTubeAction = BTDownloadActions.REMOVE_YOUTUBE_ACTION;
        resumeAction = BTDownloadActions.RESUME_ACTION;
        pauseAction = BTDownloadActions.PAUSE_ACTION;
        exploreAction = BTDownloadActions.EXPLORE_ACTION;
        showInLibraryAction = BTDownloadActions.SHOW_IN_LIBRARY_ACTION;
        copyMagnetAction = BTDownloadActions.COPY_MAGNET_ACTION;
        copyHashAction = BTDownloadActions.COPY_HASH_ACTION;
        shareTorrentAction = BTDownloadActions.SHARE_TORRENT_ACTION;
        playSingleMediaFileAction = BTDownloadActions.PLAY_SINGLE_AUDIO_FILE_ACTION;
    }

    /**
     * Returns the most prominent actions that operate on the download table.
     */
    public Action[] getActions() {
        return new Action[]{resumeAction, pauseAction, retryAction, showInLibraryAction, exploreAction, removeAction, clearInactiveAction};
    }

    /**
     * Set up the necessary constants.
     */
    protected void setupConstants() {
        MAIN_PANEL = new PaddedPanel();
        transfersFilter = new TransfersFilter();
        DATA_MODEL = new BTDownloadRowFilteredModel(transfersFilter);//new BTDownloadModel();
        TABLE = new LimeJTable(DATA_MODEL);
        TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        // the actual download buttons instance.
        BTDownloadButtons downloadButtons = new BTDownloadButtons(this);
        BUTTON_ROW = downloadButtons.getComponent();
    }

    private BTDownload findBTDownload(File saveLocation) {
        if (saveLocation == null) {
            return null;
        }
        try {
            final List<BTDownload> downloads = getDownloads();
            for (BTDownload dl : downloads) {
                File dlSaveLocation = dl.getSaveLocation();
                if (saveLocation.equals(dlSaveLocation)) {
                    return dl;
                }
                // special consideration if it is an actual torrent transfer
                if (dl instanceof BittorrentDownload) {
                    dlSaveLocation = ((BittorrentDownload) dl).getDl().getContentSavePath();
                    if (saveLocation.equals(dlSaveLocation)) {
                        return dl;
                    }
                }
            }
        } catch (Throwable e) {
            LOG.error("Error looking for transfer by save location", e);
        }
        return null;
    }

    // Linear complexity, take it easy, try no to use on refresh methods, only on actions.
    public boolean isActiveTorrentDownload(File saveLocation) {
        if (saveLocation == null) {
            return false;
        }
        int active = getActiveDownloads() + getActiveUploads();
        if (active == 0) {
            return false;
        }
        final BTDownload btDownload = findBTDownload(saveLocation);
        return isActive(btDownload);
    }

    @Override
    public void onFilterUpdate(TransfersTab.FilterMode mode, String searchKeywords) {
        transfersFilter.update(mode, searchKeywords);
        updateTableFilters();
    }

    @Override
    public void onFilterUpdate(String searchKeywords) {
        transfersFilter.update(searchKeywords);
        updateTableFilters();
    }

    public void setBTDownloadSelectionListener(BTDownloadSelectionListener transferSelectionListener) {
        transferTabSelectionListener = transferSelectionListener;
    }

    void updateTableFilters() {
        if (TABLE == null || DATA_MODEL == null) {
            return;
        }
        BTDownload[] selectedDownloaders = getSelectedDownloaders();
        DATA_MODEL.filtersChanged();
        if (selectedDownloaders.length == 1) {
            // try to select again as filtersChanged triggers a table rebuild and selection will be lost
            List<BTDownload> downloads = getDownloads();
            for (BTDownload d : downloads) {
                if (selectedDownloaders[0] == d) {
                    selectBTDownload(d);
                    ensureDownloadVisible(d);
                    return;
                }
            }
        }
    }

    /**
     * Update the splash screen.
     */
    protected void updateSplashScreen() {
        GUIMediator.setSplashScreenString(I18n.tr("Loading Download Window..."));
    }

    /**
     * Override the default refreshing so that we can
     * set the clear button appropriately.
     */
    public void doRefresh() {
        if (DATA_MODEL == null) {
            return;
        }
        DATA_MODEL.refresh();
        if (TABLE != null) {
            int[] selRows = TABLE.getSelectedRows();
            if (selRows.length > 0) {
                BTDownloadDataLine dataLine = DATA_MODEL.get(selRows[0]);
                if (dataLine != null) {
                    BTDownload dl = dataLine.getInitializeObject();
                    if (dl != null) {
                        boolean completed = dl.isCompleted();
                        retryAction.setEnabled(dl.getState() == TransferState.ERROR_NOT_ENOUGH_PEERS);
                        resumeAction.setEnabled(dl.isResumable());
                        pauseAction.setEnabled(dl.isPausable());
                        exploreAction.setEnabled(completed);
                        showInLibraryAction.setEnabled(completed);
                    }
                }
            }
        }
        int n = DATA_MODEL.getRowCount();
        boolean anyClearable = false;
        for (int i = n - 1; i >= 0; i--) {
            BTDownloadDataLine btDownloadDataLine = DATA_MODEL.get(i);
            if (btDownloadDataLine != null) {
                BTDownload initializeObject = btDownloadDataLine.getInitializeObject();
                if (initializeObject != null && isClearable(initializeObject)) {
                    anyClearable = true;
                    break;
                }
            }
        }
        clearInactiveAction.setEnabled(anyClearable);
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
        double totalBandwidth = download ? engine.downloadRate() : engine.uploadRate();
        if (download) {
            double httpBandwidth = 0;
            for (BTDownload btDownload : this.getDownloads()) {
                if (btDownload instanceof HttpDownload ||
                        btDownload instanceof SoundcloudDownload) {
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
     * <p>
     * Adds a new Downloads to the list of Downloads, obtaining the necessary
     * information from the supplied `Downloader`.
     * <p>
     * If the download is not already in the list, then it is added.
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
     * <p>
     * Takes action upon downloaded theme files, asking if the user wants to
     * apply the theme.
     * <p>
     * Removes a download from the list if the user has configured their system
     * to automatically clear completed download and if the download is
     * complete.
     *
     * @param downloader the `Downloader` to remove from the list if it is
     *                   complete.
     */
    public void remove(BTDownload downloader) {
        super.remove(downloader);
        downloader.remove();
    }

    BTDownload[] getSelectedBTDownloads() {
        int[] sel = TABLE.getSelectedRows();
        ArrayList<BTDownload> btdownloadList = new ArrayList<>(sel.length);
        for (int aSel : sel) {
            BTDownloadDataLine btDownloadDataLine = DATA_MODEL.get(aSel);
            if (btDownloadDataLine.getInitializeObject().isCompleted()) {
                btdownloadList.add(btDownloadDataLine.getInitializeObject());
            }
        }
        return btdownloadList.toArray(new BTDownload[0]);
    }

    /**
     * Handles a double-click event in the table.
     */
    public void handleActionKey() {
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
        menu.add(new SkinMenuItem(retryAction));
        menu.addSeparator();
        menu.add(new SkinMenuItem(showInLibraryAction));
        menu.add(new SkinMenuItem(exploreAction));
        menu.addSeparator();
        menu.add(new SkinMenuItem(shareTorrentAction));
        menu.add(new SkinMenuItem(copyMagnetAction));
        menu.add(new SkinMenuItem(copyHashAction));
        menu.addSeparator();
        menu.add(new SkinMenuItem(removeAction));
        menu.add(new SkinMenuItem(BTDownloadActions.REMOVE_TORRENT_ACTION));
        menu.add(new SkinMenuItem(BTDownloadActions.REMOVE_TORRENT_AND_DATA_ACTION));
        menu.add(new SkinMenuItem(removeYouTubeAction));
        SkinMenu advancedMenu = BTDownloadMediatorAdvancedMenuFactory.createAdvancedSubMenu();
        if (advancedMenu != null) {
            menu.addSeparator();
            menu.add(advancedMenu);
        }
        return menu;
    }

    private boolean selectionHasMP4s(File saveLocation) {
        return saveLocation != null && (LibraryUtils.directoryContainsExtension(saveLocation, "mp4") || (saveLocation.isFile() && FileUtils.hasExtension(saveLocation.getAbsolutePath(), "mp4")));
    }

    private boolean selectionIsSingleFile(File saveLocation) {
        return saveLocation != null && saveLocation.isFile();
    }

    private boolean selectionHasMediaFiles(BTDownload d) {
        if (d instanceof SoundcloudDownload) {
            return true;
        }
        File saveLocation = d.getSaveLocation();
        //in case it's a single picked .torrent/magnet download
        if (saveLocation != null && saveLocation.isDirectory() && LibraryUtils.directoryContainsASinglePlayableFile(saveLocation)) {
            try {
                //noinspection ConstantConditions
                saveLocation = saveLocation.listFiles()[0];
            } catch (Throwable t) {
                saveLocation = null;
            }
        }
        return saveLocation != null && (LibraryUtils.directoryContainsPlayableExtensions(saveLocation) || (saveLocation.isFile() && MediaPlayer.isPlayableFile(saveLocation)));
    }

    private boolean isHttpTransfer(BTDownload d) {
        return d instanceof SoundcloudDownload || d instanceof HttpDownload;
    }

    /**
     * Computes "has media?" flags off the EDT and updates UI if this selection is still current.
     */
    private void updateMediaUIAsync(BTDownload dl, boolean isTransferFinished) {
        final int seq = selectionSeq.incrementAndGet();
        final File saveLocation = dl.getSaveLocation();

        LibraryUtils.getExecutor().submit(() -> {
            boolean isSingleFile = saveLocation != null && saveLocation.isFile();
            boolean hasMediaFiles = false;
            boolean hasMP4s = false;

            try {
                if (dl instanceof SoundcloudDownload) {
                    hasMediaFiles = true;
                } else if (saveLocation != null) {
                    if (saveLocation.isFile()) {
                        hasMediaFiles = MediaPlayer.isPlayableFile(saveLocation);
                        hasMP4s = org.limewire.util.FileUtils.hasExtension(saveLocation.getAbsolutePath(), "mp4");
                    } else if (saveLocation.isDirectory()) {
                        // directory scans are off-EDT here
                        hasMediaFiles = LibraryUtils.directoryContainsPlayableExtensions(saveLocation);
                        hasMP4s = LibraryUtils.directoryContainsExtension(saveLocation, "mp4");
                    }
                }
            } catch (Throwable t) {
                // swallow and leave defaults (disabled)
            }

            final boolean fHasMediaFiles = hasMediaFiles;
            final boolean fHasMP4s = hasMP4s;
            final boolean fIsSingleFile = isSingleFile;

            GUIMediator.safeInvokeLater(() -> {
                // Drop stale results if selection changed again
                if (seq != selectionSeq.get()) return;
                playSingleMediaFileAction.setEnabled(getSelectedDownloaders().length == 1 && fHasMediaFiles && fIsSingleFile);
            });
        });
    }

    /**
     * Handles the deselection of all rows in the download table,
     * disabling all necessary buttons and menu items.
     */
    public void handleNoSelection() {
        removeAction.setEnabled(false);
        resumeAction.setEnabled(false);
        retryAction.setEnabled(false);
        clearInactiveAction.setEnabled(false);
        pauseAction.setEnabled(false);
        exploreAction.setEnabled(false);
        showInLibraryAction.setEnabled(false);
        copyMagnetAction.setEnabled(false);
        copyHashAction.setEnabled(false);
        shareTorrentAction.setEnabled(false);
        playSingleMediaFileAction.setEnabled(false);
        BTDownloadActions.REMOVE_TORRENT_ACTION.setEnabled(false);
        BTDownloadActions.REMOVE_TORRENT_AND_DATA_ACTION.setEnabled(false);
        removeYouTubeAction.setEnabled(false);
        notifyTransferTabSelectionListener(null);
    }

    /**
     * Handles the selection of the specified row in the download window,
     * enabling or disabling buttons and chat menu items depending on
     * the values in the row.
     *
     * @param row the selected row
     */
    public void handleSelection(int row) {
        // Avoid doing heavy work while selection is still adjusting
        if (TABLE.getSelectionModel().getValueIsAdjusting()) {
            return;
        }
        BTDownloadDataLine dataLine = DATA_MODEL.get(row);
        BTDownload dl = dataLine.getInitializeObject();
        boolean pausable = dl.isPausable();
        boolean resumable = dl.isResumable();
        boolean isTransferFinished = dl.isCompleted();

        // Defer expensive filesystem checks off-EDT; pessimistic UI for now
        playSingleMediaFileAction.setEnabled(false);

        removeAction.putValue(Action.NAME, I18n.tr("Cancel Download"));
        removeAction.putValue(LimeAction.SHORT_NAME, I18n.tr("Cancel"));
        removeAction.putValue(Action.SHORT_DESCRIPTION, I18n.tr("Cancel Selected Downloads"));
        exploreAction.setEnabled(dl.isCompleted());
        showInLibraryAction.setEnabled(dl.isCompleted());
        removeAction.setEnabled(true);
        resumeAction.setEnabled(resumable);
        retryAction.setEnabled(dl.getState() == TransferState.ERROR_NOT_ENOUGH_PEERS);
        pauseAction.setEnabled(pausable);
        copyMagnetAction.setEnabled(!isHttpTransfer(dl));
        copyHashAction.setEnabled(!isHttpTransfer(dl));

        shareTorrentAction.setEnabled(getSelectedDownloaders().length == 1 && dl.isPausable());
        // Compute media flags asynchronously and update actions when ready
        updateMediaUIAsync(dl, isTransferFinished);

        removeYouTubeAction.setEnabled(isHttpTransfer(dl));
        BTDownloadActions.REMOVE_TORRENT_ACTION.setEnabled(!isHttpTransfer(dl));
        BTDownloadActions.REMOVE_TORRENT_AND_DATA_ACTION.setEnabled(!isHttpTransfer(dl));
        if (GUIMediator.Tabs.TRANSFERS.equals(GUIMediator.instance().getSelectedTab())) {
            notifyTransferTabSelectionListener(dl);
        }
    }

    private void notifyTransferTabSelectionListener(BTDownload selected) {
        if (transferTabSelectionListener != null) {
            try {
                transferTabSelectionListener.onTransferSelected(selected);
            } catch (Throwable t) {
                LOG.warn("notifyTransferTabSelectionListener() failed: ", t);
            }
        }
    }

    public void openTorrentURI(final String uri, final boolean partialDownload) {
        SwingUtilities.invokeLater(() -> {
            BTDownload downloader = new TorrentFetcherDownload(uri, partialDownload);
            add(downloader);
        });
    }

    public void openTorrentFileForSeed(final File torrentFile, final File saveDir) {
        // If this is called from a UI thread, it needs to be moved to a background thread, ideally
        // reusing an executor
        if (EventQueue.isDispatchThread()) {
            BackgroundQueuedExecutorService.schedule(() -> openTorrentFileForSeed(torrentFile, saveDir));
            return;
        }

        if (VPNDropGuard.canUseBitTorrent()) {
            try {
                File torrentToAdd = torrentFile;
                try {
                    // Ensure the .torrent exists under the configured Torrents directory so it can be discovered/restored
                    File torrentsDir = com.limegroup.gnutella.settings.SharingSettings.TORRENTS_DIR_SETTING.getValue();
                    if (torrentsDir != null) {
                        if (!torrentsDir.exists()) {
                            // noinspection ResultOfMethodCallIgnored
                            torrentsDir.mkdirs();
                        }
                        File parent = torrentFile.getParentFile();
                        // If the .torrent is outside the configured torrentsDir, copy it there
                        if (parent == null || !torrentsDir.equals(parent)) {
                            File target = new File(torrentsDir, torrentFile.getName());
                            try {
                                java.nio.file.Files.copy(torrentFile.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                torrentToAdd = target;
                            } catch (Throwable ignored) {
                                // fall back to original file if copy fails
                                torrentToAdd = torrentFile;
                            }
                        }
                    }
                } catch (Throwable ignored) {
                    // best effort only
                }
                BTEngine.getInstance().download(torrentToAdd, saveDir, null);
            } catch (Throwable e) {
                GUIMediator.safeInvokeLater(() -> {
                    LOG.error("openTorrentFileForSeed(): BTEngine.getInstance().download(torrentFile, saveDir, null) failed: ", e);
                    if (!e.toString().contains("No files selected by user")) {
                        // could not read torrent file or bad torrent file.
                        GUIMediator.showError(
                                I18n.tr("FrostWire was unable to load the torrent file \"{0}\", - it may be malformed or FrostWire does not have permission to access this file.",
                                        torrentFile.getName()), QuestionsHandler.TORRENT_OPEN_FAILURE);
                    }
                });
            }

        }
    }

    @Override
    protected void setDefaultRenderers() {
        super.setDefaultRenderers();
        TABLE.setDefaultRenderer(PaymentOptions.class, new PaymentOptionsRenderer());
        TABLE.setDefaultRenderer(TransferHolder.class, new TransferActionsRenderer());
        TABLE.setDefaultRenderer(SeedingHolder.class, new TransferSeedingRenderer());
        TABLE.setDefaultRenderer(TransferDetailFiles.TransferItemHolder.class, new TransferDetailFilesActionsRenderer());
    }

    @Override
    protected void setDefaultEditors() {
        BTDownloadDataLine.PAYMENT_OPTIONS_COLUMN.setCellEditor(new GenericCellEditor(getPaymentOptionsRenderer()));
        BTDownloadDataLine.ACTIONS_COLUMN.setCellEditor(new GenericCellEditor(getTransferActionsRenderer()));
        BTDownloadDataLine.SEEDING_COLUMN.setCellEditor(new GenericCellEditor(getSeedingRenderer()));
    }

    public void openTorrentFile(final File torrentFile, final boolean partialDownload, final Runnable onOpenRunnableForUIThread) {
        if (VPNDropGuard.canUseBitTorrent()) {
            SwingUtilities.invokeLater(() -> {
                try {
                    boolean[] filesSelection = null;
                    if (partialDownload) {
                        PartialFilesDialog dlg = new PartialFilesDialog(GUIMediator.getAppFrame(), torrentFile);
                        dlg.setVisible(true);
                        filesSelection = dlg.getFilesSelection();
                        if (filesSelection == null) {
                            return;
                        }
                    }
                    if (onOpenRunnableForUIThread != null) {
                        onOpenRunnableForUIThread.run();
                    }
                    File saveDir = null;
                    // Check if there's a file named like the torrent in the same folder
                    // then that means the user wants to seed
                    String seedDataFilename = FilenameUtils.removeExtension(torrentFile.getName());
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
                    LOG.error(e.toString(), e);
                    if (!e.toString().contains("No files selected by user")) {
                        // could not read torrent file or bad torrent file.
                        GUIMediator.showError(
                                I18n.tr("FrostWire was unable to load the torrent file \"{0}\", - it may be malformed or FrostWire does not have permission to access this file.",
                                        torrentFile.getName()), QuestionsHandler.TORRENT_OPEN_FAILURE);
                    }
                }
            });
        }
    }

    public void openTorrentSearchResult(final TorrentSearchResult sr, final boolean partialDownload) {
        GUIMediator.safeInvokeLater(() -> {
            TorrentFetcherDownload d;
            if (!partialDownload && sr instanceof TorrentItemSearchResult) {
                String relativePath = ((TorrentItemSearchResult) sr).getFilePath();
                d = new TorrentFetcherDownload(sr.getTorrentUrl(), sr.getReferrerUrl(), sr.getDisplayName(), false, relativePath);
            } else {
                d = new TorrentFetcherDownload(sr.getTorrentUrl(), sr.getReferrerUrl(), sr.getDisplayName(), partialDownload);
            }
            add(d);
        });
    }

    BTDownload[] getSelectedDownloaders() {
        int[] sel = TABLE.getSelectedRows();
        ArrayList<BTDownload> downloaders = new ArrayList<>(sel.length);
        for (int aSel : sel) {
            BTDownloadDataLine line = DATA_MODEL.get(aSel);
            BTDownload downloader = line.getInitializeObject();
            downloaders.add(downloader);
        }
        return downloaders.toArray(new BTDownload[0]);
    }

    public List<BTDownload> getDownloads() {
        int count = TABLE.getRowCount();
        List<BTDownload> downloads = new ArrayList<>(count);
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
        return engine.totalDownload();
    }

    public long getTotalBytesUploaded() {
        BTEngine engine = BTEngine.getInstance();
        return engine.totalUpload();
    }

    private boolean isClearable(BTDownload initializeObject) {
        TransferState state = initializeObject.getState();
        return state != TransferState.SEEDING && state != TransferState.CHECKING && initializeObject.isCompleted();
    }

    void removeCompleted() {
        int n = DATA_MODEL.getRowCount();
        for (int i = n - 1; i >= 0; i--) {
            BTDownloadDataLine btDownloadDataLine = DATA_MODEL.get(i);
            BTDownload initializeObject = btDownloadDataLine.getInitializeObject();
            if (isClearable(initializeObject)) {
                DATA_MODEL.remove(i);
            }
        }
        updateTableFilters();
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

    public void updateDownload(com.frostwire.bittorrent.BTDownload dl) {
        try {
            int count = TABLE.getRowCount();
            for (int i = 0; i < count; i++) {
                if (i < DATA_MODEL.getRowCount()) {
                    BTDownloadDataLine line = DATA_MODEL.get(i);
                    BTDownload downloader = line.getInitializeObject();
                    if (downloader instanceof BittorrentDownload bt) {
                        if (bt.getHash().equals(dl.getInfoHash())) {
                            bt.updateUI(dl);
                            break;
                        }
                    }
                }
            }
        } catch (Throwable e) {
            LOG.error("Error updating bittorrent download", e);
        }
    }

    /**
     * Load from the last settings saved the previous sorting preferences of this mediator.
     */
    private void restoreSorting() {
        if (EventQueue.isDispatchThread()) {
            throw new RuntimeException("BTDownloadMediator.restoreSorting() cannot be called from the EDT");
        }
        int sortIndex = BittorrentSettings.BTMEDIATOR_COLUMN_SORT_INDEX.getValue();
        boolean sortOrder = BittorrentSettings.BTMEDIATOR_COLUMN_SORT_ORDER.getValue();
        LimeTableColumn column = BTDownloadDataLine.staticGetColumn(sortIndex);
        if (sortIndex != -1 && column != null && TablesHandlerSettings.getVisibility(column.getId(), column.getDefaultVisibility()).getValue()) {
            DATA_MODEL.sort(sortIndex); //ascending
            if (!sortOrder) { //descending
                DATA_MODEL.sort(sortIndex);
            }
        } else {
            DATA_MODEL.sort(BTDownloadDataLine.DATE_CREATED_COLUMN.getModelIndex());
        }
    }

    public void downloadSoundcloudFromTrackUrlOrSearchResult(final String trackUrl, final SoundcloudSearchResult sr, boolean fromPastedUrl) {
        if (sr != null) {
            BackgroundQueuedExecutorService.schedule(() -> {
                System.out.println("BTDownloadMediator.downloadSoundcloudFromTrackUrlOrSearchResult about to get download url");
                final String downloadUrl = sr.getDownloadUrl();
                System.out.println("BTDownloadMediator.downloadSoundcloudFromTrackUrlOrSearchResult: " + downloadUrl);
                GUIMediator.safeInvokeLater(() -> {
                    if (isDownloading(downloadUrl)) {
                        DATA_MODEL.remove(downloadUrl);
                        doRefresh();
                        return;
                    }
                    BTDownload downloader = new SoundcloudDownload(sr);
                    add(downloader);
                });
            });
        } else if (trackUrl != null) {
            BackgroundQueuedExecutorService.schedule(() -> {
                try {
                    String url = trackUrl;
                    if (trackUrl.contains("?in=")) {
                        url = trackUrl.substring(0, trackUrl.indexOf("?in="));
                    }
                    String resolveURL = SoundcloudSearchPerformer.resolveUrl(url);
                    HttpClient client = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD);
                    String json = client.get(resolveURL, 10000);
                    LinkedList<SoundcloudSearchResult> results = SoundcloudSearchPerformer.fromJson(json, fromPastedUrl);
                    if (results.isEmpty()) {
                        GUIMediator.safeInvokeLater(() -> GUIMediator.showError(I18n.tr("Sorry, Couldn't find a valid download location at") + "<br><br><a href=\"" + trackUrl + "\">" + trackUrl + "</a>"));
                        return;
                    }
                    for (SoundcloudSearchResult urlSr : results) {
                        downloadSoundcloudFromTrackUrlOrSearchResult(trackUrl, urlSr, fromPastedUrl);
                    }
                } catch (Throwable e) {
                    LOG.error(e.toString(), e);
                }
            });
        }
    }

    public void openSlide(final Slide slide) {
        GUIMediator.safeInvokeLater(() -> {
            SlideDownload downloader = new SlideDownload(slide);
            add(downloader);
        });
    }

    public void openHttp(final String httpUrl, final String title, final String saveFileAs, final double fileSize, boolean extractAudio) {
        GUIMediator.safeInvokeLater(() -> {
            final HttpDownload downloader = new HttpDownload(httpUrl, title, saveFileAs, fileSize, null, false, true) {
                @Override
                protected void onComplete() {
                    final File savedFile = getSaveLocation();
                    if (savedFile.exists()) {
                        // if extract audio and delete original, we need to do this before the file is scanned
                        if (extractAudio) {
                            File m4a = extractAudio(savedFile);
                            if (m4a != null) {
                                GUIMediator.safeInvokeLater(() -> {
                                    GUIMediator.instance().setWindow(GUIMediator.Tabs.LIBRARY);
                                    LibraryMediator.instance().getLibraryExplorer().selectAudio();
                                    LibraryMediator.instance().getLibraryExplorer().refreshSelection(true);

                                    // Wait for the table to be loaded in order to select the first row
                                    BackgroundQueuedExecutorService.schedule(() -> {
                                        Thread.yield();
                                        GUIMediator.safeInvokeLater(() -> LibraryFilesTableMediator.instance().setSelectedRow(0));
                                    });
                                });
                            }
                        }
                    }
                }
            };
            add(downloader);
        });
    }

    private File extractAudio(File videoMp4) {
        File mp4 = videoMp4.getAbsoluteFile();
        File extractedAudio = new File(mp4.getParentFile(), FilenameUtils.getBaseName(mp4.getName()) + ".m4a").getAbsoluteFile();
        Mp4Info inf = Mp4Info.audio(null, null, null, null);
        try {
            Mp4Demuxer.audio(mp4, extractedAudio, inf, null);
        } catch (IOException e) {
            LOG.error("BTDownloadMediator.extractAudioAndRemoveOriginalVideo() error extracting audio from mp4 file: " + mp4.getAbsolutePath(), e);
            return null;
        }
        return extractedAudio;
    }

    public interface BTDownloadSelectionListener {
        void onTransferSelected(BTDownload selected);
    }

    /**
     * Filter out all the models who are being seeded.
     *
     * @author gubatron
     */
    private static class TransfersFilter implements TableLineFilter<BTDownloadDataLine> {
        private TransfersTab.FilterMode mode = TransfersTab.FilterMode.ALL;
        private String searchKeywords;

        @Override
        public boolean allow(BTDownloadDataLine line) {
            if (line == null) {
                return false;
            }
            boolean result = false;
            if (mode == TransfersTab.FilterMode.SEEDING) {
                result = line.isSeeding();
            } else if (mode == TransfersTab.FilterMode.DOWNLOADING) {
                result = line.isDownloading();
            } else if (mode == TransfersTab.FilterMode.FINISHED) {
                result = line.isFinished();
            } else if (mode == TransfersTab.FilterMode.ALL) {
                result = true;
            }
            return result && matchKeywords(line, searchKeywords);
        }

        private boolean matchKeywords(BTDownloadDataLine line, String searchKeywords) {
            // "Steve Jobs" iTune's like search.
            if (searchKeywords == null ||
                    searchKeywords.isEmpty() ||
                    searchKeywords.trim().equals(TransfersTab.FILTER_TEXT_HINT)) {
                return true;
            }
            String hayStack = line.getDisplayName().toLowerCase();
            final String[] tokens = searchKeywords.split("\\s");
            if (tokens.length == 1) {
                return hayStack.contains(tokens[0].toLowerCase());
            } else {
                for (String needle : tokens) {
                    if (!hayStack.contains(needle.toLowerCase())) {
                        return false;
                    }
                }
            }
            return true;
        }

        void update(TransfersTab.FilterMode mode, String searchKeywords) {
            this.mode = mode;
            this.searchKeywords = searchKeywords;
        }

        void update(String searchKeywords) {
            this.searchKeywords = searchKeywords;
        }
    }
}
