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

import com.frostwire.alexandria.Playlist;
import com.frostwire.gui.library.LibraryMediator;
import com.frostwire.gui.library.LibraryUtils;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.player.MediaSource;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;
import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.actions.LimeAction;
import com.limegroup.gnutella.gui.iTunesMediator;
import com.limegroup.gnutella.settings.ApplicationSettings;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
final class BTDownloadActions {

    static final ExploreAction EXPLORE_ACTION = new ExploreAction();
    static final ShowInLibraryAction SHOW_IN_LIBRARY_ACTION = new ShowInLibraryAction();
    static final ResumeAction RESUME_ACTION = new ResumeAction();
    static final PauseAction PAUSE_ACTION = new PauseAction();
    static final ClearInactiveAction CLEAR_INACTIVE_ACTION = new ClearInactiveAction();
    static final RemoveAction REMOVE_ACTION = new RemoveAction(false, false);
    static final RemoveAction REMOVE_YOUTUBE_ACTION = new RemoveYouTubeAction();
    static final RemoveAction REMOVE_TORRENT_ACTION = new RemoveAction(true, false);
    static final RemoveAction REMOVE_TORRENT_AND_DATA_ACTION = new RemoveAction(true, true);
    static final CopyMagnetAction COPY_MAGNET_ACTION = new CopyMagnetAction();
    static final CopyInfoHashAction COPY_HASH_ACTION = new CopyInfoHashAction();
    static final SendBTDownloaderAudioFilesToiTunes SEND_TO_ITUNES_ACTION = new SendBTDownloaderAudioFilesToiTunes();
    static final ToggleSeedsVisibilityAction TOGGLE_SEEDS_VISIBILITY_ACTION = new ToggleSeedsVisibilityAction();
    static final ShareTorrentAction SHARE_TORRENT_ACTION = new ShareTorrentAction();
    static final PlaySingleMediaFileAction PLAY_SINGLE_AUDIO_FILE_ACTION = new PlaySingleMediaFileAction();

    private static class SendBTDownloaderAudioFilesToiTunes extends AbstractAction {

        public SendBTDownloaderAudioFilesToiTunes() {
            putValue(Action.NAME, I18n.tr("Send to iTunes"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Send files to iTunes"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();

            if (downloaders != null && downloaders.length > 0) {
                try {
                    final BTDownload downloader = downloaders[0];
                    File saveLocation = downloader.getSaveLocation();

                    if (downloader instanceof BittorrentDownload) {
                        BittorrentDownload btDownload = (BittorrentDownload) downloader;
                        saveLocation = new File(btDownload.getSaveLocation(), btDownload.getName());
                    }

                    System.out.println("Sending to iTunes " + saveLocation.getAbsolutePath());

                    iTunesMediator.instance().scanForSongs(saveLocation);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

    }

    private static abstract class RefreshingAction extends AbstractAction {

        public final void actionPerformed(ActionEvent e) {
            performAction(e);
            BTDownloadMediator.instance().doRefresh();
        }

        protected abstract void performAction(ActionEvent e);

    }


    private static class ShowDetailsAction extends RefreshingAction {

        /**
         *
         */
        private static final long serialVersionUID = 6100070262538050091L;

        public ShowDetailsAction() {
            putValue(Action.NAME, I18n.tr("Details"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Show Details"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Show Torrent Details"));
            putValue(LimeAction.ICON_NAME, "LIBRARY_EXPLORE");
        }

        protected void performAction(ActionEvent e) {
            System.out.println("Pending implementation");
        }
    }

    private static class ExploreAction extends RefreshingAction {
        /**
         *
         */
        private static final long serialVersionUID = -4648558721588938475L;

        public ExploreAction() {
            putValue(Action.NAME, I18n.tr("Explore"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Explore"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Open Folder Containing the File"));
            putValue(LimeAction.ICON_NAME, "LIBRARY_EXPLORE");
        }

        public void performAction(ActionEvent e) {
            BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();
            if (downloaders.length > 0) {
                // when the downloader is a single file, this is appending a folder to the actual file path
                // treating it like a bittorrent download.
                File toExplore = new File(downloaders[0].getSaveLocation(), downloaders[0].getDisplayName());

                if (toExplore != null) {
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
    }

    private static class ShowInLibraryAction extends RefreshingAction {
        private static final long serialVersionUID = -4648558721588938475L;

        public ShowInLibraryAction() {
            putValue(Action.NAME, I18n.tr("Show"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Show"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Shows the contents of this transfer in the Library Tab"));
            putValue(LimeAction.ICON_NAME, "DOWNLOAD_SHOW_IN_LIBRARY");
        }

        public void performAction(ActionEvent e) {
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
        public ResumeAction() {
            putValue(Action.NAME, I18n.tr("Resume Download"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Resume"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Reattempt Selected Downloads"));
            putValue(LimeAction.ICON_NAME, "DOWNLOAD_FILE_MORE_SOURCES");
        }

        public void performAction(ActionEvent e) {
            BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();
            TorrentUtil.askForPermissionToSeedAndSeedDownloads(downloaders);
        }
    }

    private static class PauseAction extends RefreshingAction {
        public PauseAction() {
            putValue(Action.NAME, I18n.tr("Pause Download"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Pause"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Pause Selected Downloads"));
            putValue(LimeAction.ICON_NAME, "DOWNLOAD_PAUSE");
        }

        public void performAction(ActionEvent e) {
            BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();
            for (int i = 0; i < downloaders.length; i++) {
                downloaders[i].pause();
            }
            UXStats.instance().log(UXAction.DOWNLOAD_PAUSE);
        }
    }

    public static class RemoveAction extends RefreshingAction {

        /**
         *
         */
        private static final long serialVersionUID = -1742554445891016991L;

        private final boolean _deleteTorrent;
        private final boolean _deleteData;

        public RemoveAction(boolean deleteTorrent, boolean deleteData) {
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
        }

        public void performAction(ActionEvent e) {
            if (_deleteData) {

                DialogOption result = GUIMediator.showYesNoMessage(I18n.tr("Are you sure you want to remove the data files from your computer?\n\nYou won't be able to recover the files."), I18n.tr("Are you sure?"), JOptionPane.QUESTION_MESSAGE);

                if (result != DialogOption.YES)
                    return;
            }

            BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();
            for (int i = 0; i < downloaders.length; i++) {
                downloaders[i].setDeleteTorrentWhenRemove(_deleteTorrent);
                downloaders[i].setDeleteDataWhenRemove(_deleteData);
            }
            BTDownloadMediator.instance().removeSelection();
            UXStats.instance().log(UXAction.DOWNLOAD_REMOVE);
        }
    }

    public static class RemoveYouTubeAction extends RemoveAction {

        private static final long serialVersionUID = 4101890173830827703L;

        public RemoveYouTubeAction() {
            super(true, true);
            putValue(Action.NAME, I18n.tr("Remove Download and Data"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Remove Download and Data"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Remove Download and Data from selected downloads"));
        }
    }

    private static class ClearInactiveAction extends RefreshingAction {

        private static final long serialVersionUID = 808308856961429212L;

        public ClearInactiveAction() {
            putValue(Action.NAME, I18n.tr("Clear Inactive"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Clear Inactive"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Clear Inactive (completed) transfers from the Transfers list."));
            putValue(LimeAction.ICON_NAME, "DOWNLOAD_CLEAR_INACTIVE");
        }

        @Override
        protected void performAction(ActionEvent e) {
            BTDownloadMediator.instance().removeCompleted();
        }

    }

    private static class CopyMagnetAction extends RefreshingAction {

        /**
         *
         */
        private static final long serialVersionUID = 1602974659454016547L;

        public CopyMagnetAction() {
            putValue(Action.NAME, I18n.tr("Copy Magnet"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Copy Magnet"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Copy Magnet"));
            putValue(LimeAction.ICON_NAME, "COPY_MAGNET");
        }

        public void performAction(ActionEvent e) {
            BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();
            String str = "";
            for (int i = 0; i < downloaders.length; i++) {
                BTDownload d = downloaders[i];
                if (d instanceof BittorrentDownload) {
                    String magnetUri = ((BittorrentDownload) d).makeMagnetUri();
                    str += magnetUri;
                    if (i < downloaders.length - 1) {
                        str += System.lineSeparator();
                    }
                }
            }

            GUIMediator.setClipboardContent(str);
        }
    }

    private static class CopyInfoHashAction extends RefreshingAction {

        /**
         *
         */
        private static final long serialVersionUID = 1138409323772464985L;

        public CopyInfoHashAction() {
            putValue(Action.NAME, I18n.tr("Copy Infohash"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Copy Infohash"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Copy Infohash"));
            putValue(LimeAction.ICON_NAME, "COPY_HASH");
        }

        public void performAction(ActionEvent e) {
            BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();
            String str = "";
            for (int i = 0; i < downloaders.length; i++) {
                str += downloaders[i].getHash();
                if (i < downloaders.length - 1) {
                    str += "\n";
                }
            }
            GUIMediator.setClipboardContent(str);
        }
    }

    private static class ShareTorrentAction extends RefreshingAction {

        /**
         *
         */
        private static final long serialVersionUID = 1138409323772464985L;

        public ShareTorrentAction() {
            putValue(Action.NAME, I18n.tr("Send to friend"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Send to friend"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Send to friend"));
            putValue(LimeAction.ICON_NAME, "SEND_HASH");
            //putValue(Action.SMALL_ICON, GUIMediator.getThemeImage("share"));
        }

        public void performAction(ActionEvent e) {
            BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();
            if (downloaders.length != 1) {
                return;
            }

            BTDownload btDownload = downloaders[0];

            if (btDownload instanceof BittorrentDownload) {
                TorrentInfo t = ((BittorrentDownload) btDownload).getTorrentInfo();
                if (t != null) { // avoid NPE due to an invalid torrent handle
                    new ShareTorrentDialog(t).setVisible(true);
                }
            }
        }
    }

    static class ToggleSeedsVisibilityAction extends RefreshingAction {
        private static final long serialVersionUID = -1632629016830943795L;

        public ToggleSeedsVisibilityAction() {
            updateName();
            putValue(LimeAction.SHORT_NAME, I18n.tr("Show Details"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Show Torrent Details"));
        }

        private void updateName() {
            if (ApplicationSettings.SHOW_SEEDING_TRANSFERS.getValue()) {
                putValue(Action.NAME, I18n.tr("Hide Seeding Transfers"));
                putValue(LimeAction.SHORT_NAME, I18n.tr("Hide Seeding Transfers"));
                putValue(Action.SHORT_DESCRIPTION, I18n.tr("Don't show Seeding Transfers"));
            } else {
                putValue(Action.NAME, I18n.tr("Show Seeding Transfers"));
                putValue(LimeAction.SHORT_NAME, I18n.tr("Show Seeding Transfers"));
                putValue(Action.SHORT_DESCRIPTION, I18n.tr("Show Seeding Transfers"));
            }

        }

        @Override
        protected void performAction(ActionEvent e) {
            //toggle the setting
            ApplicationSettings.SHOW_SEEDING_TRANSFERS.setValue(!ApplicationSettings.SHOW_SEEDING_TRANSFERS.getValue());
            updateName();
            BTDownloadMediator.instance().updateTableFilters();
        }
    }

    static class CreateNewPlaylistAction extends AbstractAction {

        private static final long serialVersionUID = 3460908036485828909L;

        public CreateNewPlaylistAction() {
            super(I18n.tr("Create New Playlist"));
            putValue(Action.LONG_DESCRIPTION, I18n.tr("Create and add to a new playlist"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();

            List<File> playlistFiles = new ArrayList<File>(downloaders.length);

            for (BTDownload d : downloaders) {
                if (!d.isCompleted()) {
                    return;
                }

                File downloadFolder = new File(d.getSaveLocation(), d.getDisplayName());
                if (downloadFolder.exists()) {
                    playlistFiles.add(downloadFolder);
                }
            }

            LibraryUtils.createNewPlaylist(playlistFiles.toArray(new File[0]));

        }
    }

    static final class PlaySingleMediaFileAction extends AbstractAction {

        private static final long serialVersionUID = -3628469680044329612L;

        public PlaySingleMediaFileAction() {
            super(I18n.tr("Play file"));
            putValue(Action.LONG_DESCRIPTION, I18n.tr("Play media file"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            File file = BTDownloadMediator.instance().getSelectedDownloaders()[0].getSaveLocation();

            if (file.isDirectory() && LibraryUtils.directoryContainsASinglePlayableFile(file, 4)) {
                try {
                    file = file.listFiles()[0];
                } catch (Throwable t) {
                    file = null;
                }
            }

            if (file != null && MediaPlayer.isPlayableFile(file)) {
                MediaPlayer.instance().loadMedia(new MediaSource(file), true, false, false);
            }
        }
    }

    /**
     * NOTE: Make sure to check out AbstractLibraryTableMediator.AddToPlaylistAction, which is a similar action to this one.
     *
     * @author gubatron
     */
    static class AddToPlaylistAction extends AbstractAction {
        private static final int MAX_VISIBLE_PLAYLIST_NAME_LENGTH_IN_MENU = 80;
        private Playlist playlist;

        public AddToPlaylistAction(Playlist playlist) {
            super(getTruncatedString(playlist.getName(), MAX_VISIBLE_PLAYLIST_NAME_LENGTH_IN_MENU));
            putValue(Action.LONG_DESCRIPTION, I18n.tr("Add to playlist") + " \"" + getValue(Action.NAME) + "\"");
            System.out.println("Truncated playlist name was:" + " " + getValue(Action.NAME));
            this.playlist = playlist;
        }

        private static String getTruncatedString(String string, int MAX_LENGTH) {
            return string.length() > MAX_LENGTH ? (string.substring(0, MAX_LENGTH - 1) + "...") : string;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();

            List<File> playlistFiles = new ArrayList<File>(downloaders.length);

            for (BTDownload d : downloaders) {
                if (!d.isCompleted()) {
                    return;
                }

                playlistFiles.add(d.getSaveLocation());
            }

            LibraryUtils.asyncAddToPlaylist(playlist, playlistFiles.toArray(new File[0]));
            GUIMediator.instance().setWindow(GUIMediator.Tabs.LIBRARY);
        }
    }
}
