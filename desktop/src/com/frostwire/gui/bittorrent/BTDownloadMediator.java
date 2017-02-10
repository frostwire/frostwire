/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.gui.bittorrent;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.gui.bittorrent.BTDownloadActions.PlaySingleMediaFileAction;
import com.frostwire.gui.components.slides.Slide;
import com.frostwire.gui.filters.TableLineFilter;
import com.frostwire.gui.library.LibraryUtils;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.tabs.TransfersTab;
import com.frostwire.gui.theme.SkinMenu;
import com.frostwire.gui.theme.SkinMenuItem;
import com.frostwire.gui.theme.SkinPopupMenu;
import com.frostwire.util.Logger;
import com.frostwire.search.soundcloud.SoundcloudSearchPerformer;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.torrent.TorrentItemSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.search.youtube.YouTubeCrawledSearchResult;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.http.HttpClient;
import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.gui.actions.LimeAction;
import com.limegroup.gnutella.gui.search.GenericCellEditor;
import com.limegroup.gnutella.gui.tables.AbstractTableMediator;
import com.limegroup.gnutella.gui.tables.LimeJTable;
import com.limegroup.gnutella.gui.tables.LimeTableColumn;
import com.limegroup.gnutella.gui.tables.TableSettings;
import com.limegroup.gnutella.settings.*;
import org.apache.commons.io.FilenameUtils;
import org.limewire.util.FileUtils;
import org.limewire.util.OSUtils;

import javax.swing.*;
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
public final class BTDownloadMediator extends AbstractTableMediator<BTDownloadRowFilteredModel, BTDownloadDataLine, BTDownload> implements TransfersTab.TransfersFilterModeListener {
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

    private TransfersFilter transfersFilter;

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
     */
    public Action[] getActions() {
        return new Action[]{resumeAction, pauseAction, showInLibraryAction, exploreAction, removeAction, clearInactiveAction};
    }

    /**
     * Set up the necessary constants.
     */
    protected void setupConstants() {
        MAIN_PANEL = new PaddedPanel(I18n.tr("Transfers"));
        transfersFilter = new TransfersFilter();
        DATA_MODEL = new BTDownloadRowFilteredModel(transfersFilter);//new BTDownloadModel();
        TABLE = new LimeJTable(DATA_MODEL);
        TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        // the actual download buttons instance.
        BTDownloadButtons downloadButtons = new BTDownloadButtons(this);
        BUTTON_ROW = downloadButtons.getComponent();
        updateTableFilters();
    }

    public BTDownload findBTDownload(File saveLocation) {
        if (saveLocation == null || saveLocation.equals("")) {
            return null;
        }
        final List<BTDownload> downloads = getDownloads();
        for (BTDownload dl : downloads) {
            final File dlSaveLocation = dl.getSaveLocation();
            if (saveLocation.equals(dlSaveLocation) && TorrentUtil.isActive(dl)) {
                return dl;
            }
        }
        return null;
    }

    // Linear complexity, take it easy, try no to use on refresh methods, only on actions.
    public boolean isActiveTorrentDownload(File saveLocation) {
        if (saveLocation == null || saveLocation.equals("")) {
            return false;
        }
        int active = getActiveDownloads() + getActiveUploads();
        if (active == 0) {
            return false;
        }
        final BTDownload btDownload = findBTDownload(saveLocation);
        return btDownload != null && TorrentUtil.isActive(btDownload);
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

    /**
     * Filter out all the models who are being seeded.
     *
     * @author gubatron
     */
    private class TransfersFilter implements TableLineFilter<BTDownloadDataLine> {
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
                searchKeywords.equals("") ||
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

        public void update(TransfersTab.FilterMode mode, String searchKeywords) {
            this.mode = mode;
            this.searchKeywords = searchKeywords;
        }

        public void update(String searchKeywords) {
            this.searchKeywords = searchKeywords;
        }
    }

    public void updateTableFilters() {
        if (TABLE == null || DATA_MODEL == null) {
            return;
        }
        DATA_MODEL.filtersChanged();
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
     * @param downloader the <tt>Downloader</tt> to remove from the list if it is
     *                complete.
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
                saveLocation = saveLocation.listFiles()[0];
            } catch (Throwable t) {
                saveLocation = null;
            }
        }

        return saveLocation != null && (LibraryUtils.directoryContainsPlayableExtensions(saveLocation) || (saveLocation.isFile() && MediaPlayer.isPlayableFile(saveLocation)));
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
        if(canUseBitTorrent()) {
            GUIMediator.safeInvokeLater(new Runnable() {
                public void run() {
                    try {
                        BTEngine.getInstance().download(torrentFile, saveDir, null);
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
    }

    @Override
    protected void setDefaultRenderers() {
        super.setDefaultRenderers();
        TABLE.setDefaultRenderer(PaymentOptions.class, new PaymentOptionsRenderer());
        TABLE.setDefaultRenderer(TransferHolder.class, new TransferActionsRenderer());
        TABLE.setDefaultRenderer(SeedingHolder.class, new TransferSeedingRenderer());
    }

    @Override
    protected void setDefaultEditors() {
        BTDownloadDataLine.PAYMENT_OPTIONS_COLUMN.setCellEditor(new GenericCellEditor(getPaymentOptionsRenderer()));
        BTDownloadDataLine.ACTIONS_COLUMN.setCellEditor(new GenericCellEditor(getTransferActionsRenderer()));
        BTDownloadDataLine.SEEDING_COLUMN.setCellEditor(new GenericCellEditor(getSeedingRenderer()));
    }

    private boolean canUseBitTorrent(){
        if (ConnectionSettings.MANDATORY_VPN_FOR_BITTORRENT.getValue() && !VPNs.isVPNActive()) {
            GUIMediator.showWarning(I18n.tr("VPN is inactive. Current settings require active VPN connection before starting BitTorrent engine. Setup a VPN connection or check advanced settings."));
            return false;
        }
        return true;
    }

    public void openTorrentFile(final File torrentFile, final boolean partialDownload, final Runnable onOpenRunnableForUIThread) {
        if(canUseBitTorrent()) {
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
    }

    public void openTorrentSearchResult(final TorrentSearchResult sr, final boolean partialDownload) {
        GUIMediator.safeInvokeLater(new Runnable() {
            public void run() {
                TorrentFetcherDownload d;
                if (!partialDownload && sr instanceof TorrentItemSearchResult) {
                    String relativePath = ((TorrentItemSearchResult) sr).getFilePath();
                    d = new TorrentFetcherDownload(sr.getTorrentUrl(), sr.getReferrerUrl(), sr.getDisplayName(), false, relativePath);
                } else {
                    d = new TorrentFetcherDownload(sr.getTorrentUrl(), sr.getReferrerUrl(), sr.getDisplayName(), partialDownload);
                }
                add(d);
            }
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
        return downloaders.toArray(new BTDownload[downloaders.size()]);
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
                    if (downloader instanceof BittorrentDownload) {
                        BittorrentDownload bt = (BittorrentDownload) downloader;
                        if (bt.getHash().equals(dl.getInfoHash())) {
                            ((BittorrentDownload) downloader).updateUI(dl);
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
            try {
                String url = trackUrl;
                if (trackUrl.contains("?in=")) {
                    url = trackUrl.substring(0, trackUrl.indexOf("?in="));
                }

                String resolveURL = SoundcloudSearchPerformer.resolveUrl(url);

                HttpClient client = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD);
                String json = client.get(resolveURL, 10000);

                LinkedList<SoundcloudSearchResult> results = SoundcloudSearchPerformer.fromJson(json);
                for (SoundcloudSearchResult urlSr : results) {
                    downloadSoundcloudFromTrackUrlOrSearchResult(trackUrl, urlSr);
                }

            } catch (Throwable e) {
                e.printStackTrace();
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
                final HttpDownload downloader = new HttpDownload(httpUrl, title, saveFileAs, fileSize, null, false, true) {
                    @Override
                    protected void onComplete() {
                        final File savedFile = getSaveLocation();
                        if (savedFile.exists()) {
                            GUIMediator.safeInvokeLater(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                BTDownloadMediator.instance().updateTableFilters();
                                                            }
                                                        });

                            if (iTunesSettings.ITUNES_SUPPORT_ENABLED.getValue() && !iTunesMediator.instance().isScanned(savedFile)) {
                                if ((OSUtils.isMacOSX() || OSUtils.isWindows())) {
                                    iTunesMediator.instance().scanForSongs(savedFile);
                                }
                            }
                        }
                    }
                };
                add(downloader);
            }
        });
    }
}
