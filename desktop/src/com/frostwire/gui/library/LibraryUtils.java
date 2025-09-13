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

import com.frostwire.concurrent.concurrent.ExecutorsHelper;
import com.frostwire.gui.bittorrent.TorrentUtil;
import com.frostwire.gui.library.tags.TagsData;
import com.frostwire.gui.library.tags.TagsReader;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.util.HistoHashMap;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.GUIMediator;
import org.limewire.util.FileUtils;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * @author gubatron
 * @author aldenml
 */
public class LibraryUtils {
    @SuppressWarnings("unused")
    private static final Logger LOG = Logger.getLogger(LibraryUtils.class);
    private static final ExecutorService executor;

    // Zero behavior change unless you start with -Dfw.debug.assertNoEdtIO=true
    private static final boolean ASSERT_NO_EDT_IO = Boolean.getBoolean("fw.debug.assertNoEdtIO");

    static {
        executor = ExecutorsHelper.newProcessingQueue("LibraryUtils-Executor");
    }

    public static ExecutorService getExecutor() {
        return executor;
    }

    public static String getSecondsInDDHHMMSS(int s) {
        if (s < 0) {
            s = 0;
        }
        StringBuilder result = new StringBuilder();
        String DD;
        String HH;
        String MM;
        String SS;
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

    public static boolean directoryContainsPlayableExtensions(File directory) {
        return directoryContainsAudio(directory);
    }

    public static boolean directoryContainsASinglePlayableFile(File directory) {
        final File[] files = directory.listFiles();
        return directoryContainsPlayableExtensions(directory) && (files != null && files.length == 1);
    }

    public static boolean directoryContainsAudio(File directory) {
        Set<File> ignore = TorrentUtil.getIgnorableFiles();
        return directoryContainsExtension(directory, 4, ignore, MediaPlayer.getPlayableExtensions());
    }

    @SuppressWarnings("SameParameterValue")
    public static boolean directoryContainsExtension(File directory, String extensionWithoutDot) {
        // Optional safety net to prevent future EDT misuse (off by default)
        if (ASSERT_NO_EDT_IO && EventQueue.isDispatchThread()) {
            throw new IllegalStateException("directoryContainsExtension called on EDT");
        }
        Set<File> ignore = TorrentUtil.getIgnorableFiles();
        return directoryContainsExtension(directory, 4, ignore, extensionWithoutDot);
    }

    private static boolean directoryContainsExtension(File directory, int depth, Set<File> ignore, String... extensionWithoutDot) {
        try {
            if (directory == null || !directory.isDirectory()) {
                return false;
            }
            File[] files = directory.listFiles();
            if (files == null || files.length == 0) {
                return false;
            }
            for (File childFile : files) {
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
        // TODO: refactor this
        List<Entry<String, Integer>> histogramArtistNames = artistNames.histogram();
        List<Entry<String, Integer>> histogramArtistsAlbums = artistsAlbums.histogram();
        List<Entry<String, Integer>> histogramAlbumNames = albumNames.histogram();
        List<Entry<String, Integer>> histogramGenres = genres.histogram();
        String topArtistName = histogramArtistNames.get(histogramArtistNames.size() - 1).getKey();
        int topArtistNameCount = histogramArtistNames.get(histogramArtistNames.size() - 1).getValue();
        String topArtistAlbum = histogramArtistsAlbums.get(histogramArtistsAlbums.size() - 1).getKey();
        int topArtistAlbumCount = histogramArtistsAlbums.get(histogramArtistsAlbums.size() - 1).getValue();
        String topAlbumName = histogramAlbumNames.get(histogramAlbumNames.size() - 1).getKey();
        int topAlbumNameCount = histogramAlbumNames.get(histogramAlbumNames.size() - 1).getValue();
        String topGenre = histogramGenres.get(histogramGenres.size() - 1).getKey();
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

    static boolean isRefreshKeyEvent(KeyEvent e) {
        int keyCode = e.getKeyCode();
        boolean ctrlCmdDown = e.isControlDown() || e.isAltGraphDown() || e.isMetaDown();
        return keyCode == KeyEvent.VK_F5 || (ctrlCmdDown && keyCode == KeyEvent.VK_R);
    }

    static void asyncParseLyrics(final TagsReader tagsReader, final OnLyricsParsedUICallback uiCallback) {
        File audioFile = tagsReader.getFile();
        if (audioFile == null || !audioFile.isFile() || !audioFile.canRead() || uiCallback == null) {
            if (uiCallback != null) {
                uiCallback.setLyrics("");
                GUIMediator.safeInvokeLater(uiCallback);
            }
            return;
        }
        executor.submit(() -> {
            TagsData tagsData = tagsReader.parse();
            if (tagsData != null) {
                uiCallback.setLyrics(tagsData.getLyrics()); // can't be null, only ""
                GUIMediator.safeInvokeLater(uiCallback);
            }
        });
    }

    public abstract static class OnLyricsParsedUICallback implements Runnable {
        private String lyrics;

        String getLyrics() {
            return lyrics;
        }

        void setLyrics(String lyrics) {
            this.lyrics = lyrics;
        }

        public abstract void run();
    }
}
