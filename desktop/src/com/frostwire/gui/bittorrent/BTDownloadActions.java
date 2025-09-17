/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.gui.bittorrent;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.gui.library.LibraryMediator;
import com.frostwire.gui.library.LibraryUtils;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.player.MediaSource;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.actions.LimeAction;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Objects;

/**
 * @author gubatron
 * @author aldenml
 */
final class BTDownloadActions {

    private final static Logger LOG = Logger.getLogger(BTDownloadActions.class);
    static final ExploreAction EXPLORE_ACTION = new ExploreAction();
    static final ShowInLibraryAction SHOW_IN_LIBRARY_ACTION = new ShowInLibraryAction();
    static final ResumeAction RESUME_ACTION = new ResumeAction();
    static final PauseAction PAUSE_ACTION = new PauseAction();
    static final ClearInactiveAction CLEAR_INACTIVE_ACTION = new ClearInactiveAction();
    static final RetryAction RETRY_ACTION = new RetryAction();
    static final RemoveAction REMOVE_ACTION = new RemoveAction(false, false);
    static final RemoveAction REMOVE_YOUTUBE_ACTION = new RemoveYouTubeAction();
    static final RemoveAction REMOVE_TORRENT_ACTION = new RemoveAction(true, false);
    static final RemoveAction REMOVE_TORRENT_AND_DATA_ACTION = new RemoveAction(true, true);
    static final CopyMagnetAction COPY_MAGNET_ACTION = new CopyMagnetAction();
    static final CopyInfoHashAction COPY_HASH_ACTION = new CopyInfoHashAction();
    static final ShareTorrentAction SHARE_TORRENT_ACTION = new ShareTorrentAction();
    static final PlaySingleMediaFileAction PLAY_SINGLE_AUDIO_FILE_ACTION = new PlaySingleMediaFileAction();

    private static abstract class RefreshingAction extends AbstractAction {
        public final void actionPerformed(ActionEvent e) {
            performAction();
            BTDownloadMediator.instance().doRefresh();
        }

        protected abstract void performAction();
    }

    private static class ExploreAction extends RefreshingAction {
        ExploreAction() {
            putValue(Action.NAME, I18n.tr("Explore"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Explore"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Open Folder Containing the File"));
            putValue(LimeAction.ICON_NAME, "LIBRARY_EXPLORE");
        }

        public void performAction() {
            BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();
            if (downloaders.length > 0) {
                // when the downloader is a single file, this is appending a folder to the actual file path
                // treating it like a bittorrent download.
                File toExplore = new File(downloaders[0].getSaveLocation(), downloaders[0].getDisplayName());
                // but perhaps it's a single file, make sure it is then... (Re: Issue #366)
                if (!toExplore.exists() &&
                        downloaders[0].getSaveLocation() != null &&
                        downloaders[0].getSaveLocation().isFile()) {
                    // (made this if very explicit and dumb on purpose to make logic clear, reverse logic is shorter)
                    toExplore = downloaders[0].getSaveLocation();
                }
                if (toExplore.exists()) {
                    GUIMediator.launchExplorer(toExplore);
                }
            }
        }
    }

    private static class ShowInLibraryAction extends RefreshingAction {
        ShowInLibraryAction() {
            putValue(Action.NAME, I18n.tr("Show"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Show"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Shows the contents of this transfer in the Library Tab"));
            putValue(LimeAction.ICON_NAME, "DOWNLOAD_SHOW_IN_LIBRARY");
        }

        public void performAction() {
            BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();
            if (downloaders.length > 0) {
                final String toExplore = downloaders[0].getDisplayName();
                if (toExplore == null) {
                    return;
                }
                LibraryMediator.instance().getLibrarySearch().searchFor(toExplore.replace("_", " ").replace("-", " ").replace(".", " "), false);
            }
        }
    }

    private static class ResumeAction extends RefreshingAction {
        ResumeAction() {
            putValue(Action.NAME, I18n.tr("Resume Download"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Resume"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Reattempt Selected Downloads"));
            putValue(LimeAction.ICON_NAME, "DOWNLOAD_FILE_MORE_SOURCES");
        }

        public void performAction() {
            BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();
            BTDownload lastSelectedDownload = null;
            if (downloaders.length == 1) {
                lastSelectedDownload = downloaders[0];
            }
            TorrentUtil.askForPermissionToSeedAndSeedDownloads(downloaders);
            BTDownloadMediator.instance().updateTableFilters();
            if (lastSelectedDownload != null) {
                BTDownloadMediator.instance().selectBTDownload(lastSelectedDownload);
            }
        }
    }

    private static class PauseAction extends RefreshingAction {
        PauseAction() {
            putValue(Action.NAME, I18n.tr("Pause Download"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Pause"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Pause Selected Downloads"));
            putValue(LimeAction.ICON_NAME, "DOWNLOAD_PAUSE");
        }

        public void performAction() {
            BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();
            BTDownload lastSelectedDownload = null;
            if (downloaders.length == 1) {
                lastSelectedDownload = downloaders[0];
            }
            for (BTDownload downloader : downloaders) {
                downloader.pause();
            }
            BTDownloadMediator.instance().updateTableFilters();
            if (lastSelectedDownload != null) {
                BTDownloadMediator.instance().selectBTDownload(lastSelectedDownload);
            }
        }
    }

    public static class RetryAction extends com.limegroup.gnutella.gui.actions.AbstractAction {
        private static final Logger LOG = Logger.getLogger(RetryAction.class);

        RetryAction() {
            putValue(Action.NAME, I18n.tr("Retry Transfer"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Retry Transfer"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Retry Transfer"));
            putValue(LimeAction.ICON_NAME, "MAGNET");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();
            if (downloaders == null) {
                LOG.info("actionPerformed() aborted. No selected transfers to retry");
                return;
            }
            new RemoveAction(true, true, false).performAction();
            BTDownloadMediator.instance().refresh();

            for (BTDownload download : downloaders) {
                LOG.info("actionPerformed() retry " + download.getClass().getCanonicalName());
                if (download instanceof TorrentFetcherDownload) {
                    GUIMediator.instance().openTorrentURI(((TorrentFetcherDownload) download).getUri(),false);
                }
            }


        }
    }

    public static class RemoveAction extends RefreshingAction {
        private final boolean _deleteTorrent;
        private final boolean _deleteData;
        private final boolean _showDialogIfDeleteData;

        RemoveAction(boolean deleteTorrent, boolean deleteData) {
            this(deleteTorrent, deleteData, true);
        }

        RemoveAction(boolean deleteTorrent, boolean deleteData, boolean showDialogIfDeleteData) {
            if (deleteTorrent && deleteData) {
                putValue(Action.NAME, I18n.tr("Remove Torrent and Data"));
                putValue(LimeAction.SHORT_NAME, I18n.tr("Remove Torrent and Data"));
                putValue(Action.SHORT_DESCRIPTION, I18n.tr("Remove Torrent and Data from selected downloads"));
            } else if (deleteTorrent) {
                putValue(Action.NAME, I18n.tr("Remove Torrent"));
                putValue(LimeAction.SHORT_NAME, I18n.tr("Remove Torrent"));
                putValue(Action.SHORT_DESCRIPTION, I18n.tr("Remove Torrent from selected downloads"));
            } else {
                putValue(Action.NAME, I18n.tr("Remove Download"));
                putValue(LimeAction.SHORT_NAME, I18n.tr("Remove"));
                putValue(Action.SHORT_DESCRIPTION, I18n.tr("Remove Selected Downloads"));
            }
            putValue(LimeAction.ICON_NAME, "DOWNLOAD_KILL");
            _deleteTorrent = deleteTorrent;
            _deleteData = deleteData;
            _showDialogIfDeleteData = showDialogIfDeleteData;
        }

        public void performAction() {
            if (_deleteData && _showDialogIfDeleteData) {
                DialogOption result = GUIMediator.showYesNoMessage(I18n.tr("Are you sure you want to remove the data files from your computer?\n\nYou won't be able to recover the files."), I18n.tr("Are you sure?"), JOptionPane.QUESTION_MESSAGE);
                if (result != DialogOption.YES)
                    return;
            }
            BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();
            for (BTDownload downloader : downloaders) {
                downloader.setDeleteTorrentWhenRemove(_deleteTorrent);
                downloader.setDeleteDataWhenRemove(_deleteData);
            }
            BTDownloadMediator.instance().removeSelection();
            BTDownloadMediator.instance().updateTableFilters();
        }
    }

    public static class RemoveYouTubeAction extends RemoveAction {
        RemoveYouTubeAction() {
            super(true, true);
            putValue(Action.NAME, I18n.tr("Remove Download and Data"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Remove Download and Data"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Remove Download and Data from selected downloads"));
        }
    }

    private static class ClearInactiveAction extends RefreshingAction {
        ClearInactiveAction() {
            putValue(Action.NAME, I18n.tr("Clear Inactive"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Clear Inactive"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Clear Inactive (completed) transfers from the Transfers list."));
            putValue(LimeAction.ICON_NAME, "DOWNLOAD_CLEAR_INACTIVE");
        }

        @Override
        protected void performAction() {
            BTDownloadMediator.instance().removeCompleted();
        }
    }

    private static class CopyMagnetAction extends RefreshingAction {
        CopyMagnetAction() {
            putValue(Action.NAME, I18n.tr("Copy Magnet"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Copy Magnet"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Copy Magnet"));
            putValue(LimeAction.ICON_NAME, "COPY_MAGNET");
        }

        public void performAction() {
            BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();
            StringBuilder str = new StringBuilder();
            for (int i = 0; i < downloaders.length; i++) {
                BTDownload d = downloaders[i];
                if (d instanceof BittorrentDownload btDownload) {
                    String magnetUri = btDownload.makeMagnetUri();
                    str.append(magnetUri);
                    str.append(BTEngine.getInstance().magnetPeers());
                    if (i < downloaders.length - 1) {
                        str.append(System.lineSeparator());
                    }
                } else if (d instanceof TorrentFetcherDownload tfd) {
                    if (tfd.getUri().startsWith("magnet")) {
                        str = new StringBuilder(tfd.getUri());
                    }
                }
            }
            GUIMediator.setClipboardContent(str.toString());
        }
    }

    private static class CopyInfoHashAction extends RefreshingAction {
        CopyInfoHashAction() {
            putValue(Action.NAME, I18n.tr("Copy Infohash"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Copy Infohash"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Copy Infohash"));
            putValue(LimeAction.ICON_NAME, "COPY_HASH");
        }

        public void performAction() {
            BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();
            StringBuilder str = new StringBuilder();
            for (int i = 0; i < downloaders.length; i++) {
                str.append(downloaders[i].getHash());
                if (i < downloaders.length - 1) {
                    str.append("\n");
                }
            }
            GUIMediator.setClipboardContent(str.toString());
        }
    }

    private static class ShareTorrentAction extends RefreshingAction {
        ShareTorrentAction() {
            putValue(Action.NAME, I18n.tr("Send to friend"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Send to friend"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Send to friend"));
            putValue(LimeAction.ICON_NAME, "SEND_HASH");
            //putValue(Action.SMALL_ICON, GUIMediator.getThemeImage("share"));
        }

        public void performAction() {
            BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();
            if (downloaders.length != 1) {
                return;
            }
            BTDownload btDownload = downloaders[0];
            if (btDownload instanceof BittorrentDownload) {
                TorrentInfo t = ((BittorrentDownload) btDownload).getTorrentInfo();
                if (t != null) { // avoid NPE due to an invalid torrent handle
                    new ShareTorrentDialog(GUIMediator.getAppFrame(), t).setVisible(true);
                }
            }
        }
    }

    static final class PlaySingleMediaFileAction extends AbstractAction {
        PlaySingleMediaFileAction() {
            super(I18n.tr("Play file"));
            putValue(Action.LONG_DESCRIPTION, I18n.tr("Play media file"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            File file = BTDownloadMediator.instance().getSelectedDownloaders()[0].getSaveLocation();
            if (file.isDirectory() && LibraryUtils.directoryContainsASinglePlayableFile(file)) {
                try {
                    file = Objects.requireNonNull(file.listFiles())[0];
                } catch (Throwable t) {
                    file = null;
                }
            }
            if (file != null && MediaPlayer.isPlayableFile(file)) {
                MediaPlayer.instance().loadMedia(new MediaSource(file), false, false);
            }
        }
    }

}
