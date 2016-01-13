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

package com.frostwire.gui.library;

import com.frostwire.alexandria.Playlist;
import com.frostwire.alexandria.PlaylistItem;
import com.frostwire.alexandria.db.LibraryDatabase;
import com.frostwire.gui.bittorrent.TorrentUtil;
import com.frostwire.gui.library.LibraryPlaylistsTableTransferable.Item;
import com.frostwire.gui.library.tags.TagsData;
import com.frostwire.gui.library.tags.TagsReader;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.theme.DialogFinishedListener;
import com.frostwire.gui.theme.FrostwireInputDialog;
import com.frostwire.logging.Logger;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import org.apache.commons.io.FilenameUtils;
import org.limewire.concurrent.ExecutorsHelper;
import org.limewire.util.FileUtils;
import org.limewire.util.StringUtils;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * @author gubatron
 * @author aldenml
 */
public class LibraryUtils {

    public static final Icon FILE_UNSHARED_ICON;
    public static final Icon FILE_SHARING_ICON;
    public static final Icon FILE_SHARED_ICON;

    static {
        FILE_UNSHARED_ICON = GUIMediator.getThemeImage("file_unshared");
        FILE_SHARING_ICON = GUIMediator.getThemeImage("file_sharing");
        FILE_SHARED_ICON = GUIMediator.getThemeImage("file_shared");
    }

    private static final Logger LOG = Logger.getLogger(LibraryUtils.class);

    private static final ExecutorService executor;

    static {
        executor = ExecutorsHelper.newProcessingQueue("LibraryUtils-Executor");
    }

    private static void addPlaylistItem(Playlist playlist, File file, boolean starred) {
        addPlaylistItem(playlist, file, starred, -1);
    }

    private static void addPlaylistItem(Playlist playlist, File file, boolean starred, int index) {
        try {
            LibraryMediator.instance().getLibrarySearch().pushStatus(I18n.tr("Importing") + " " + file.getName());
            TagsData mt = new TagsReader(file).parse();
            PlaylistItem item = playlist.newItem(file.getAbsolutePath(), file.getName(), file.length(), FilenameUtils.getExtension(file.getName()), mt.getTitle(), mt.getDuration(), mt.getArtist(), mt.getAlbum(), "",// TODO: cover art path
                    mt.getBitrate(), mt.getComment(), mt.getGenre(), mt.getTrack(), mt.getYear(), starred);

            List<PlaylistItem> items = playlist.getItems();
            if (index != -1 && index < items.size()) {

                // insert item
                items.add(index, item);

                // update all sort indexes from insertion point onwards
                for (int i = index; i < items.size(); i++) {
                    PlaylistItem curItem = items.get(i);
                    curItem.setSortIndexByTrackNumber(i+1);
                    curItem.save();
                }

            } else {
                items.add(item);
                item.setSortIndexByTrackNumber(items.size()); // fall back index would be it being the last track.
                item.save();
            }

            if (isPlaylistSelected(playlist)) {
                // refresh UI
                LibraryMediator.instance().getLibraryPlaylists().refreshSelection();
            }
        } finally {
            LibraryMediator.instance().getLibrarySearch().revertStatus();
        }
    }

    public static String getSecondsInDDHHMMSS(int s) {
        if (s < 0) {
            s = 0;
        }

        StringBuilder result = new StringBuilder();

        String DD = "";
        String HH = "";
        String MM = "";
        String SS = "";

        //math
        int days = s / 86400;
        int r = s % 86400;

        int hours = r / 3600;
        r = s % 3600;
        int minutes = r / 60;
        int seconds = r % 60;

        //padding
        DD = String.valueOf(days);
        HH = (hours < 10) ? "0" + hours : String.valueOf(hours);
        MM = (minutes < 10) ? "0" + minutes : String.valueOf(minutes);
        SS = (seconds < 10) ? "0" + seconds : String.valueOf(seconds);

        //lazy formatting
        if (days > 0) {
            result.append(DD);
            result.append(" day");
            if (days > 1) {
                result.append("s");
            }
            return result.toString();
        }

        if (hours > 0) {
            result.append(HH);
            result.append(":");
        }

        result.append(MM);
        result.append(":");
        result.append(SS);

        return result.toString();
    }

    public static void createNewPlaylist(final List<? extends AbstractLibraryTableDataLine<?>> lines) {
        DialogFinishedListener dialogListener = new DialogFinishedListener() {
            @Override
            public void onDialogCancelled() {
            }

            @Override
            public void onDialogOk(String playlistName) {
                if (playlistName != null && playlistName.length() > 0) {
                    final Playlist playlist = LibraryMediator.getLibrary().newPlaylist(playlistName, playlistName);
                    playlist.save();
                    LibraryMediator.instance().getLibraryPlaylists().addPlaylist(playlist);
                    LibraryMediator.instance().getLibraryPlaylists().markBeginImport(playlist);
                    Thread t = new Thread(new Runnable() {
                        public void run() {
                            addToPlaylist(playlist, lines);
                            playlist.save();
                            asyncAddToPlaylistFinalizer(playlist);
                        }
                    }, "createNewPlaylist");
                    t.setDaemon(true);
                    t.start();
                    UXStats.instance().log(UXAction.LIBRARY_PLAYLIST_CREATED);
                }
            }
        };

        FrostwireInputDialog.showInputDialog(GUIMediator.getAppFrame(),
                I18n.tr("Playlist name"),
                I18n.tr("Playlist name"),
                GUIMediator.getThemeImage("playlist"),
                suggestPlaylistName(lines),
                dialogListener);
    }

    public static void createNewPlaylist(File[] files) {
        createNewPlaylist(files, false);
    }

    public static void createNewPlaylist(final File[] files, final boolean starred) {
        final DialogFinishedListener listener = new DialogFinishedListener() {
            @Override
            public void onDialogCancelled() {
            }

            @Override
            public void onDialogOk(String playlistName) {
                if (StringUtils.isNullOrEmpty(playlistName, true)) {
                    return;
                }
                if (playlistName != null && playlistName.length() > 0) {
                    GUIMediator.instance().setWindow(GUIMediator.Tabs.LIBRARY);
                    final Playlist playlist = LibraryMediator.getLibrary().newPlaylist(playlistName, playlistName);
                    playlist.save();

                    GUIMediator.safeInvokeLater(new Runnable() {
                        @Override
                        public void run() {
                            LibraryMediator.instance().getLibraryPlaylists().addPlaylist(playlist);
                            LibraryMediator.instance().getLibraryPlaylists().markBeginImport(playlist);
                        }
                    });

                    Thread t = new Thread(new Runnable() {
                        public void run() {
                            try {
                                Set<File> ignore = TorrentUtil.getIgnorableFiles();
                                addToPlaylist(playlist, files, starred, ignore);
                                playlist.save();
                            } finally {
                                asyncAddToPlaylistFinalizer(playlist);
                            }
                        }
                    }, "createNewPlaylist");
                    t.setDaemon(true);
                    t.start();
                    UXStats.instance().log(UXAction.LIBRARY_PLAYLIST_CREATED);
                }
            }
        };

        GUIMediator.safeInvokeAndWait(new Runnable() {
            @Override
            public void run() {
                File[] mediaFiles = files;

                if (files.length == 1 && files[0].isDirectory()) {
                    mediaFiles = FileUtils.getFilesRecursive(files[0], null);
                }
                FrostwireInputDialog.showInputDialog(GUIMediator.getAppFrame(),
                        I18n.tr("Playlist name"),
                        I18n.tr("Playlist name"),
                        GUIMediator.getThemeImage("playlist"),
                        suggestPlaylistName(mediaFiles),
                        listener);
            }
        });
    }

    public static void createNewPlaylist(final PlaylistItem[] playlistItems) {
        createNewPlaylist(playlistItems, false);
    }

    public static void createNewPlaylist(final PlaylistItem[] playlistItems, boolean starred) {
        if (starred) {
            createStarredPlaylist(playlistItems);
        } else {
            DialogFinishedListener listener = new DialogFinishedListener() {
                @Override
                public void onDialogCancelled() {
                }

                @Override
                public void onDialogOk(String playlistName) {
                    if (playlistName != null && playlistName.length() > 0) {
                        final Playlist playlist = LibraryMediator.getLibrary().newPlaylist(playlistName, playlistName);

                        Thread t = new Thread(new Runnable() {
                            public void run() {
                                try {
                                    playlist.save();
                                    addToPlaylist(playlist, playlistItems);
                                    playlist.save();
                                    GUIMediator.safeInvokeLater(new Runnable() {
                                        public void run() {
                                            LibraryMediator.instance().getLibraryPlaylists().addPlaylist(playlist);
                                        }
                                    });
                                    UXStats.instance().log(UXAction.LIBRARY_PLAYLIST_CREATED);
                                } finally {
                                    asyncAddToPlaylistFinalizer(playlist);
                                }
                            }
                        }, "createNewPlaylist");
                        t.setDaemon(true);
                        t.start();
                    }

                }
            };

            FrostwireInputDialog.showInputDialog(GUIMediator.getAppFrame(), I18n.tr("Playlist name"), I18n.tr("Playlist name"), GUIMediator.getThemeImage("playlist"), suggestPlaylistName(playlistItems), listener);
        }

    }

    private static void createStarredPlaylist(final PlaylistItem[] playlistItems) {
        Thread t = new Thread(new Runnable() {
            public void run() {
                Playlist playlist = LibraryMediator.getLibrary().getStarredPlaylist();
                addToPlaylist(playlist, playlistItems, true, -1);
                GUIMediator.safeInvokeLater(new Runnable() {
                    public void run() {
                        DirectoryHolder dh = LibraryMediator.instance().getLibraryExplorer().getSelectedDirectoryHolder();
                        if (dh instanceof StarredDirectoryHolder) {
                            LibraryMediator.instance().getLibraryExplorer().refreshSelection();
                        } else {
                            LibraryMediator.instance().getLibraryExplorer().selectStarred();
                        }
                    }
                });
            }
        }, "createNewPlaylist");
        t.setDaemon(true);
        t.start();
    }

    public static void createNewPlaylist(File m3uFile) {
        createNewPlaylist(m3uFile, false);
    }

    public static void createNewPlaylist(File m3uFile, boolean starred) {
        try {
            List<File> files = M3UPlaylist.load(m3uFile.getAbsolutePath());
            createNewPlaylist(files.toArray(new File[0]), starred);
            UXStats.instance().log(UXAction.LIBRARY_PLAYLIST_CREATED);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void asyncAddToPlaylist(final Playlist playlist, final List<? extends AbstractLibraryTableDataLine<?>> lines) {
        LibraryMediator.instance().getLibraryPlaylists().markBeginImport(playlist);
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    addToPlaylist(playlist, lines);
                } finally {
                    asyncAddToPlaylistFinalizer(playlist);
                }
            }
        }, "asyncAddToPlaylist");
        t.setDaemon(true);
        t.start();
    }

    public static void asyncAddToPlaylist(Playlist playlist, File[] files) {
        asyncAddToPlaylist(playlist, files, -1);
    }

    public static void asyncAddToPlaylist(final Playlist playlist, final File[] files, final int index) {
        LibraryMediator.instance().getLibraryPlaylists().markBeginImport(playlist);
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    Set<File> ignore = TorrentUtil.getIgnorableFiles();
                    addToPlaylist(playlist, files, false, index, ignore);
                    playlist.save();
                } finally {
                    asyncAddToPlaylistFinalizer(playlist);
                }
            }
        }, "asyncAddToPlaylist");
        t.setDaemon(true);
        t.start();
    }

    private static void asyncAddToPlaylistFinalizer(final Playlist playlist) {
        GUIMediator.safeInvokeLater(new Runnable() {
            public void run() {
                LibraryMediator.instance().getLibraryPlaylists().markEndImport(playlist);
                LibraryMediator.instance().getLibraryPlaylists().refreshSelection();
                LibraryMediator.instance().getLibraryPlaylists().selectPlaylist(playlist);
            }
        });
    }

    public static void asyncAddToPlaylist(Playlist playlist, PlaylistItem[] playlistItems) {
        asyncAddToPlaylist(playlist, playlistItems, -1);
    }

    public static void asyncAddToPlaylist(final Playlist playlist, final PlaylistItem[] playlistItems, final int index) {
        Thread t = new Thread(new Runnable() {
            public void run() {
                addToPlaylist(playlist, playlistItems, index);
                playlist.save();
                GUIMediator.safeInvokeLater(new Runnable() {
                    public void run() {
                        LibraryMediator.instance().getLibraryPlaylists().refreshSelection();
                    }
                });
            }
        }, "asyncAddToPlaylist");
        t.setDaemon(true);
        t.start();
    }

    public static void asyncAddToPlaylist(Playlist playlist, File m3uFile) {
        asyncAddToPlaylist(playlist, m3uFile, -1);
    }

    public static void asyncAddToPlaylist(Playlist playlist, File m3uFile, int index) {
        try {
            List<File> files = M3UPlaylist.load(m3uFile.getAbsolutePath());
            asyncAddToPlaylist(playlist, files.toArray(new File[0]), index);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<LibraryPlaylistsTableTransferable.Item> convertToItems(List<PlaylistItem> playlistItems) {
        List<LibraryPlaylistsTableTransferable.Item> items = new ArrayList<>(playlistItems.size());
        for (PlaylistItem playlistItem : playlistItems) {
            Item item = new LibraryPlaylistsTableTransferable.Item();
            item.id = playlistItem.getId();
            item.filePath = playlistItem.getFilePath();
            item.fileName = playlistItem.getFileName();
            item.fileSize = playlistItem.getFileSize();
            item.fileExtension = playlistItem.getFileExtension();
            item.trackTitle = playlistItem.getTrackTitle();
            item.trackDurationInSecs = playlistItem.getTrackDurationInSecs();
            item.trackArtist = playlistItem.getTrackArtist();
            item.trackAlbum = playlistItem.getTrackAlbum();
            item.coverArtPath = playlistItem.getCoverArtPath();
            item.trackBitrate = playlistItem.getTrackBitrate();
            item.trackComment = playlistItem.getTrackComment();
            item.trackGenre = playlistItem.getTrackGenre();
            item.trackNumber = playlistItem.getTrackNumber();
            item.trackYear = playlistItem.getTrackYear();
            item.starred = playlistItem.isStarred();
            items.add(item);
        }
        return items;
    }

    public static PlaylistItem[] convertToPlaylistItems(LibraryPlaylistsTableTransferable.Item[] items) {
        List<PlaylistItem> playlistItems = new ArrayList<PlaylistItem>(items.length);
        for (LibraryPlaylistsTableTransferable.Item item : items) {
            PlaylistItem playlistItem = new PlaylistItem(null, item.id, item.filePath, item.fileName, item.fileSize, item.fileExtension, item.trackTitle, item.trackDurationInSecs, item.trackArtist, item.trackAlbum, item.coverArtPath, item.trackBitrate, item.trackComment, item.trackGenre,
                    item.trackNumber, item.trackYear, item.starred);
            playlistItems.add(playlistItem);
        }
        return playlistItems.toArray(new PlaylistItem[0]);
    }

    public static PlaylistItem[] convertToPlaylistItems(LibraryPlaylistsTableTransferable.PlaylistItemContainer itemContainer) {
        List<PlaylistItem> playlistItems = new ArrayList<>(itemContainer.items.size());
        for (LibraryPlaylistsTableTransferable.Item item : itemContainer.items) {
            PlaylistItem playlistItem = new PlaylistItem(null, item.id, item.filePath, item.fileName, item.fileSize, item.fileExtension, item.trackTitle, item.trackDurationInSecs, item.trackArtist, item.trackAlbum, item.coverArtPath, item.trackBitrate, item.trackComment, item.trackGenre,
                    item.trackNumber, item.trackYear, item.starred);
            playlistItems.add(playlistItem);
        }
        return playlistItems.toArray(new PlaylistItem[0]);
    }

    private static void addToPlaylist(Playlist playlist, List<? extends AbstractLibraryTableDataLine<?>> lines) {
        for (int i = 0; i < lines.size() && !playlist.isDeleted(); i++) {
            AbstractLibraryTableDataLine<?> line = lines.get(i);
            if (MediaPlayer.isPlayableFile(line.getFile())) {
                LibraryUtils.addPlaylistItem(playlist, line.getFile(), false);
            }
        }
    }

    private static int addToPlaylist(Playlist playlist, File[] files, boolean starred, Set<File> ignore) {
        return addToPlaylist(playlist, files, starred, -1, ignore);
    }

    private static int addToPlaylist(Playlist playlist, File[] files, boolean starred, int index, Set<File> ignore) {
        int count = 0;
        for (int i = 0; i < files.length && !playlist.isDeleted(); i++) {
            if (MediaPlayer.isPlayableFile(files[i]) && !ignore.contains(files[i])) {
                LibraryUtils.addPlaylistItem(playlist, files[i], starred, index + count);
                count++;
            } else if (files[i].isDirectory()) {
                count += addToPlaylist(playlist, files[i].listFiles(), starred, index + count, ignore);
            }
        }

        return count;
    }

    private static void addToPlaylist(Playlist playlist, PlaylistItem[] playlistItems) {
        addToPlaylist(playlist, playlistItems, false, -1);
    }

    private static void addToPlaylist(Playlist playlist, PlaylistItem[] playlistItems, int index) {
        addToPlaylist(playlist, playlistItems, false, index);
    }

    private static void addToPlaylist(Playlist playlist, PlaylistItem[] playlistItems, boolean starred, int index) {
        List<PlaylistItem> items = playlist.getItems();
        if (index != -1 && index <= items.size()) {
            List<Integer> toRemove = new ArrayList<>(playlistItems.length);
            for (int i = 0; i < playlistItems.length && !playlist.isDeleted(); i++) {
                toRemove.add(playlistItems[i].getId());
                playlistItems[i].setId(LibraryDatabase.OBJECT_NOT_SAVED_ID);
                playlistItems[i].setPlaylist(playlist);
                items.add(index + i, playlistItems[i]);
                if (starred) {
                    playlistItems[i].setStarred(true);
                    playlistItems[i].save();
                }
            }
            for (int i = 0; i < toRemove.size() && !playlist.isDeleted(); i++) {
                int id = toRemove.get(i);
                for (int j = 0; j < items.size() && !playlist.isDeleted(); j++) {
                    if (items.get(j).getId() == id) {
                        items.remove(j);
                        break;
                    }
                }
            }

            // update sort indexes now that the ordering in the list is correct
            items = playlist.getItems();
            for (int i = 0; i < items.size(); i++) {
                PlaylistItem item = items.get(i);
                item.setSortIndexByTrackNumber(i + 1); // set index 1-based
                item.save();
            }

        } else {
            for (int i = 0; i < playlistItems.length && !playlist.isDeleted(); i++) {

                playlistItems[i].setPlaylist(playlist);
                items.add(playlistItems[i]);
                playlistItems[i].setSortIndexByTrackNumber(items.size()); // set sort index to be at the end (1-based)

                if (starred) {
                    playlistItems[i].setStarred(true);
                }

                playlistItems[i].save();
            }
        }
    }

    public static String getPlaylistDurationInDDHHMMSS(Playlist playlist) {
        List<PlaylistItem> items = playlist.getItems();
        float totalSecs = 0;
        for (PlaylistItem item : items) {
            totalSecs += item.getTrackDurationInSecs();
        }

        return getSecondsInDDHHMMSS((int) totalSecs);
    }

    public static boolean directoryContainsPlayableExtensions(File directory, int depth) {
        Set<File> ignore = TorrentUtil.getIgnorableFiles();
        return directoryContainsExtension(directory, depth, ignore, MediaPlayer.getPlayableExtensions());
    }

    public static boolean directoryContainsASinglePlayableFile(File directory, int depth) {
        return directoryContainsPlayableExtensions(directory, depth) && (directory.listFiles().length == 1);
    }

    public static boolean directoryContainsAudio(File directory) {
        Set<File> ignore = TorrentUtil.getIgnorableFiles();
        return directoryContainsExtension(directory, 4, ignore, MediaPlayer.getPlayableExtensions());
    }

    public static boolean directoryContainsExtension(File directory, int depth, String extensionWithoutDot) {
        Set<File> ignore = TorrentUtil.getIgnorableFiles();
        return directoryContainsExtension(directory, depth, ignore, extensionWithoutDot);
    }

    private static boolean directoryContainsExtension(File directory, int depth, Set<File> ignore, String... extensionWithoutDot) {
        try {
            if (directory == null || !directory.isDirectory()) {
                return false;
            }

            for (File childFile : directory.listFiles()) {
                if (!childFile.isDirectory()) {
                    if (FileUtils.hasExtension(childFile.getAbsolutePath(), extensionWithoutDot) && !ignore.contains(childFile)) {
                        return true;
                    }
                } else {
                    if (depth > 0) {
                        if (directoryContainsExtension(childFile, depth - 1, ignore, extensionWithoutDot)) {
                            return true;
                        }
                    }
                }
            }
        } catch (NullPointerException e) {
            // NPE reported in bug manager, ignore until refactor
        }

        return false;
    }

    private static String suggestPlaylistName(File[] mediaFiles) {
        HistoHashMap<String> artistNames = new HistoHashMap<>();
        HistoHashMap<String> artistsAlbums = new HistoHashMap<>();
        HistoHashMap<String> albumNames = new HistoHashMap<>();
        HistoHashMap<String> genres = new HistoHashMap<>();

        for (File mf : mediaFiles) {
            if (MediaPlayer.isPlayableFile(mf)) {
                TagsData mt = new TagsReader(mf).parse();
                artistNames.update(mt.getArtist());
                artistsAlbums.update(mt.getArtist() + " - " + mt.getAlbum());
                albumNames.update(mt.getAlbum());
                genres.update("(" + mt.getGenre() + ")");
            }
        }

        Entry<String, Integer>[] histogramArtistNames = artistNames.histogram();
        Entry<String, Integer>[] histogramArtistsAlbums = artistsAlbums.histogram();
        Entry<String, Integer>[] histogramAlbumNames = albumNames.histogram();
        Entry<String, Integer>[] histogramGenres = genres.histogram();

        String topArtistName = histogramArtistNames[histogramArtistNames.length - 1].getKey();
        int topArtistNameCount = histogramArtistNames[histogramArtistNames.length - 1].getValue();

        String topArtistAlbum = histogramArtistsAlbums[histogramArtistsAlbums.length - 1].getKey();
        int topArtistAlbumCount = histogramArtistsAlbums[histogramArtistsAlbums.length - 1].getValue();

        String topAlbumName = histogramAlbumNames[histogramAlbumNames.length - 1].getKey();
        int topAlbumNameCount = histogramAlbumNames[histogramAlbumNames.length - 1].getValue();

        String topGenre = histogramGenres[histogramGenres.length - 1].getKey();

        String suggestedPlaylistName = topArtistName;
        if (topArtistAlbumCount >= topArtistNameCount) {
            suggestedPlaylistName = topArtistAlbum;
        } else if (topAlbumNameCount >= topArtistNameCount) {
            suggestedPlaylistName = topAlbumName;
            if (topArtistNameCount > 3) {
                suggestedPlaylistName = topArtistName + " - " + suggestedPlaylistName;
            }
        }

        if (!topGenre.equals("()")) {
            suggestedPlaylistName = suggestedPlaylistName + " " + topGenre;
        }

        return suggestedPlaylistName;
    }

    private static String suggestPlaylistName(List<? extends AbstractLibraryTableDataLine<?>> lines) {
        File[] files = new File[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            files[i] = lines.get(i).getFile();
        }
        return suggestPlaylistName(files);
    }

    private static String suggestPlaylistName(PlaylistItem[] playlistItems) {
        File[] files = new File[playlistItems.length];
        for (int i = 0; i < files.length; i++) {
            files[i] = new File(playlistItems[i].getFilePath());
        }
        return suggestPlaylistName(files);
    }

    public static void cleanup(Playlist playlist) {
        if (playlist == null) {
            return;
        }
        try {
            for (PlaylistItem item : playlist.getItems()) {
                if (!new File(item.getFilePath()).exists()) {
                    item.delete();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void refreshID3Tags(Playlist playlist) {
        refreshID3Tags(playlist, playlist.getItems());
    }

    public static void refreshID3Tags(final Playlist playlist, final List<PlaylistItem> items) {
        executor.execute(new Runnable() {
            public void run() {
                for (PlaylistItem item : items) {
                    try {
                        LibraryMediator.instance().getLibrarySearch().pushStatus(I18n.tr("Refreshing") + " " + item.getTrackAlbum() + " - " + item.getTrackTitle());
                        File file = new File(item.getFilePath());
                        if (file.exists()) {
                            TagsData mt = new TagsReader(file).parse();
                            LibraryMediator.getLibrary().updatePlaylistItemProperties(item.getFilePath(), mt.getTitle(), mt.getArtist(), mt.getAlbum(), mt.getComment(), mt.getGenre(), mt.getTrack(), mt.getYear());
                        }
                    } catch (Exception e) {
                        // ignore, skip
                    } finally {
                        LibraryMediator.instance().getLibrarySearch().revertStatus();
                    }
                }
                GUIMediator.safeInvokeLater(new Runnable() {
                    public void run() {
                        if (playlist != null) {
                            if (playlist.getId() == LibraryDatabase.STARRED_PLAYLIST_ID) {
                                DirectoryHolder dh = LibraryMediator.instance().getLibraryExplorer().getSelectedDirectoryHolder();
                                if (dh instanceof StarredDirectoryHolder) {
                                    LibraryMediator.instance().getLibraryExplorer().refreshSelection();
                                }
                            } else {
                                Playlist selectedPlaylist = LibraryMediator.instance().getLibraryPlaylists().getSelectedPlaylist();
                                if (selectedPlaylist != null && selectedPlaylist.equals(playlist)) {
                                    LibraryMediator.instance().getLibraryPlaylists().refreshSelection();
                                }
                            }
                        }
                    }
                });
            }
        });
    }

    private static boolean isPlaylistSelected(Playlist playlist) {
        Playlist selectedPlaylist = LibraryMediator.instance().getLibraryPlaylists().getSelectedPlaylist();
        return selectedPlaylist != null && selectedPlaylist.equals(playlist);
    }

    public static boolean isRefreshKeyEvent(KeyEvent e) {
        int keyCode = e.getKeyCode();
        boolean ctrlCmdDown = e.isControlDown() || e.isAltGraphDown() || e.isMetaDown();
        return keyCode == KeyEvent.VK_F5 || (ctrlCmdDown && keyCode == KeyEvent.VK_R);
    }

    public static void movePlaylistItemsToIndex(Playlist playlist, int[] selectedIndexes, int index) {

        List<PlaylistItem> items = playlist.getItems();
        int targetIndex = index;

        // first, order items in list correctly
        for (int i = 0; i < selectedIndexes.length; i++) {
            int sourceIndex = selectedIndexes[i];

            if (sourceIndex != targetIndex) {
                items.add(targetIndex, items.get(sourceIndex));
                items.remove(sourceIndex < targetIndex ? sourceIndex : sourceIndex + 1);

                // adjust remaining selected indexes if insertion point is greater than their location
                for (int j = i + 1; j < selectedIndexes.length; j++) {
                    if (targetIndex > selectedIndexes[j]) {
                        selectedIndexes[j]--;
                    }
                }

                // update insertion point
                if (sourceIndex > targetIndex) {
                    targetIndex++;
                }
            }
        }

        // second, generate new indexes based list order
        for (int i = 0; i < items.size(); i++) {
            PlaylistItem item = items.get(i);
            item.setSortIndexByTrackNumber(i + 1); // set index (1-based)
            item.save();
        }

        // initiate UI refresh
        GUIMediator.safeInvokeLater(new Runnable() {
            public void run() {
                LibraryMediator.instance().getLibraryPlaylists().refreshSelection();
            }
        });
    }
}
