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

package com.limegroup.gnutella.gui;

import com.frostwire.gui.bittorrent.TorrentUtil;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.settings.SharingSettings;
import com.limegroup.gnutella.settings.iTunesImportSettings;
import com.limegroup.gnutella.settings.iTunesSettings;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.limewire.concurrent.ExecutorsHelper;
import org.limewire.util.CommonUtils;
import org.limewire.util.FileUtils;
import org.limewire.util.OSUtils;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Handles sending completed downloads into iTunes.
 */
public final class iTunesMediator {
    private static final Logger LOG = Logger.getLogger(iTunesMediator.class);
    private static final String JS_IMPORT_SCRIPT_NAME = "itunes_import.js";
    private static final String JS_REMOVE_PLAYLIST_SCRIPT_NAME = "itunes_remove_playlist.js";
    private static iTunesMediator INSTANCE;
    /**
     * The queue that will process the tunes to add.
     */
    private final ExecutorService QUEUE = ExecutorsHelper.newFixedSizeThreadPool(4, "iTunesAdderThread");

    /**
     * Initializes iTunesMediator with the script file.
     */
    private iTunesMediator() {
        if (OSUtils.isWindows()) {
            createiTunesJavaScript(JS_IMPORT_SCRIPT_NAME);
            createiTunesJavaScript(JS_REMOVE_PLAYLIST_SCRIPT_NAME);
        }
    }

    /**
     * Returns the sole instance of this class.
     */
    public static synchronized iTunesMediator instance() {
        if (INSTANCE == null) {
            INSTANCE = new iTunesMediator();
        }
        return INSTANCE;
    }

    /**
     * Returns true if the extension of name is a supported file type.
     */
    private static boolean isSupported(String extension) {
        if (extension == null) {
            return false;
        }
        String[] types = iTunesSettings.ITUNES_SUPPORTED_FILE_TYPES.getValue();
        for (String type : types) {
            if (extension.equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    private static String getItunesMusicAppName() {
        return OSUtils.isMacOSCatalina105OrNewer() ? "Music" : "iTunes";
    }

    /**
     * Constructs and returns a osascript command.
     */
    private static String[] createOSAScriptCommand(String playlist, File[] files) {
        List<String> command = new ArrayList<>();
        command.add("osascript");
        command.add("-e");
        command.add("tell application \"Finder\"");
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            String path = f.getAbsolutePath();
            command.add("-e");
            command.add("set hfsFile" + i + " to (POSIX file \"" + path + "\")");
        }
        command.add("-e");
        command.add("set thePlaylist to \"" + playlist + "\"");
        command.add("-e");

        command.add("tell application \"" + getItunesMusicAppName() + "\"");
        command.add("-e");
        command.add("launch");
        command.add("-e");
        command.add("if not (exists playlist thePlaylist) then");
        command.add("-e");
        command.add("set thisPlaylist to make new playlist");
        command.add("-e");
        command.add("set name of thisPlaylist to thePlaylist");
        command.add("-e");
        command.add("end if");
        for (int i = 0; i < files.length; i++) {
            command.add("-e");
            command.add("add hfsFile" + i + " to playlist thePlaylist");
        }
        command.add("-e");
        command.add("end tell");
        command.add("-e");
        command.add("end tell");
        return command.toArray(new String[0]);
    }

    private static String[] createWSHScriptCommand(String playlist, File[] files) {
        ArrayList<String> command = new ArrayList<>();
        command.add("wscript");
        command.add("//B");
        command.add("//NoLogo");
        command.add(new File(CommonUtils.getUserSettingsDir(), JS_IMPORT_SCRIPT_NAME).getAbsolutePath());
        command.add(playlist);
        for (File file : files) {
            command.add(file.getAbsolutePath());
        }
        return command.toArray(new String[0]);
    }

    private static void createiTunesJavaScript(String scriptName) {
        File fileJS = new File(CommonUtils.getUserSettingsDir(), scriptName);
        if (fileJS.exists()) {
            return;
        }
        URL url = ResourceManager.getURLResource(scriptName);
        InputStream is = null;
        OutputStream out = null;
        try {
            if (url != null) {
                is = new BufferedInputStream(url.openStream());
                out = new FileOutputStream(fileJS);
                IOUtils.copy(is, out);
            }
        } catch (IOException e) {
            LOG.error("Error creating " + getItunesMusicAppName() + ".app javascript", e);
        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(out);
        }
    }

    // This could be moved to a utils class if we ever needed elsewhere.
    // I use this to split list of files to be imported to itunes
    // because the script interpreters can only handle so many characters as arguments.
    private static <T> List<T[]> splitArray(T[] items, int maxSubArraySize) {
        List<T[]> result = new ArrayList<>();
        if (items == null || items.length == 0) {
            return result;
        }
        int from = 0;
        int to = 0;
        int slicedItems = 0;
        while (slicedItems < items.length) {
            to = from + Math.min(maxSubArraySize, items.length - to);
            T[] slice = Arrays.copyOfRange(items, from, to);
            result.add(slice);
            slicedItems += slice.length;
            from = to;
        }
        return result;
    }

    /**
     * If running on OSX, iTunes integration is enabled and the downloaded file
     * is a supported type, send it to iTunes.
     */
    private void addSongsITunes(String playlist, File file) {
        // Make sure we convert any uppercase to lowercase or vice versa.
        try {
            file = FileUtils.getCanonicalFile(file);
        } catch (IOException ignored) {
        }
        // Verify that we're adding a real file.
        if (!file.exists()) {
            LOG.warn("File: '" + file + "' does not exist");
            return;
        }
        File[] files;
        if (file.isDirectory()) {
            files = FileUtils.getFilesRecursive(file, iTunesSettings.ITUNES_SUPPORTED_FILE_TYPES.getValue());
        } else if (file.isFile() && isSupported(FilenameUtils.getExtension(file.getName()))) {
            files = new File[]{file};
        } else {
            return;
        }
        if (files.length == 0) {
            return;
        }
        addSongsiTunes(playlist, files);
    }

    public void addSongsiTunes(String playlist, File[] files) {
        //remove incomplete files from files.
        Set<File> ignorableFiles = TorrentUtil.getIgnorableFiles();
        List<File> completeFiles = new ArrayList<>(files.length);
        for (File f : files) {
            if (ignorableFiles.contains(f)) {
                continue;
            }
            if (f.exists() && f.isFile() && isSupported(FilenameUtils.getExtension(f.getName()))) {
                completeFiles.add(f);
            }
        }
        files = completeFiles.toArray(new File[0]);
        if (files.length == 0) {
            return;
        }
        if (OSUtils.isMacOSX()) {
            QUEUE.execute(new ExecOSAScriptCommand(playlist, files));
        } else {
            LOG.info("Will add '" + files.length + " files" + "' to Playlist");
            QUEUE.execute(new ExecWSHScriptCommand(playlist, files));
        }
    }

    public void scanForSongs(File file) {
        scanForSongs(iTunesSettings.ITUNES_PLAYLIST.getValue(), file);
    }

    public void scanForSongs(File[] files) {
        if (OSUtils.isMacOSX() || OSUtils.isWindows()) {
            for (File f : files) {
                iTunesImportSettings.IMPORT_FILES.add(f);
            }
            addSongsiTunes(iTunesSettings.ITUNES_PLAYLIST.getValue(), files);
        }
    }

    private void scanForSongs(String playlist, File file) {
        iTunesImportSettings.IMPORT_FILES.add(file);
        if (OSUtils.isMacOSX() || OSUtils.isWindows()) {
            addSongsITunes(playlist, file);
        }
    }

    public boolean isScanned(File file) {
        return iTunesImportSettings.IMPORT_FILES.contains(file);
    }

    private void deleteFrostWirePlaylist() {
        String playlistName = iTunesSettings.ITUNES_PLAYLIST.getValue();
        try {
            if (OSUtils.isMacOSX()) {
                String[] command = new String[]{"osascript", "-e", "tell application \"" + getItunesMusicAppName() + "\"", "-e", "delete playlist \"" + playlistName + "\"", "-e", "end tell"};
                Runtime.getRuntime().exec(command);
            } else if (OSUtils.isWindows()) {
                ArrayList<String> command = new ArrayList<>();
                command.add("wscript");
                command.add("//B");
                command.add("//NoLogo");
                command.add(new File(CommonUtils.getUserSettingsDir(), JS_REMOVE_PLAYLIST_SCRIPT_NAME).getAbsolutePath());
                command.add(playlistName);
                Runtime.getRuntime().exec(command.toArray(new String[0]));
            }
        } catch (IOException e) {
            LOG.error("Error executing itunes command", e);
        }
    }

    public void resetFrostWirePlaylist() {
        deleteFrostWirePlaylist();
        QUEUE.execute(() -> {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
            iTunesMediator.instance().scanForSongs(SharingSettings.TORRENT_DATA_DIR_SETTING.getValue());
        });
    }

    /**
     * Executes the osascript CLI command
     */
    private class ExecOSAScriptCommand implements Runnable {
        private final String playlist;
        /**
         * The file to add.
         */
        private final File[] files;

        /**
         * Constructs a new ExecOSAScriptCommand for the specified file.
         */
        ExecOSAScriptCommand(String playlist, File[] files) {
            this.playlist = playlist;
            this.files = files;
        }

        /**
         * Runs the osascript command
         */
        public void run() {
            try {
                int MAX_SCRIPT_FILE_NUMBER_OF_ARGUMENTS = 300;
                if (files.length > MAX_SCRIPT_FILE_NUMBER_OF_ARGUMENTS) {
                    List<File[]> fileArrays = splitArray(files, MAX_SCRIPT_FILE_NUMBER_OF_ARGUMENTS);
                    for (File[] fileSubset : fileArrays) {
                        Runtime.getRuntime().exec(createOSAScriptCommand(playlist, fileSubset));
                    }
                } else {
                    Runtime.getRuntime().exec(createOSAScriptCommand(playlist, files));
                }
            } catch (Throwable e) {
                LOG.error(e.getMessage(), e);
            }
        }
    }

    private class ExecWSHScriptCommand implements Runnable {
        private final String playlist;
        /**
         * The file to add.
         */
        private final File[] files;

        /**
         * Constructs a new ExecOSAScriptCommand for the specified file.
         */
        ExecWSHScriptCommand(String playlist, File[] files) {
            this.playlist = playlist;
            this.files = files;
        }

        /**
         * Runs the osascript command
         */
        public void run() {
            try {
                int MAX_SCRIPT_FILE_NUMBER_OF_ARGUMENTS = 100;
                if (files.length > MAX_SCRIPT_FILE_NUMBER_OF_ARGUMENTS) {
                    List<File[]> fileArrays = splitArray(files, MAX_SCRIPT_FILE_NUMBER_OF_ARGUMENTS);
                    for (File[] fileSubset : fileArrays) {
                        Runtime.getRuntime().exec(createWSHScriptCommand(playlist, fileSubset));
                    }
                } else {
                    Runtime.getRuntime().exec(createWSHScriptCommand(playlist, files));
                }
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
            }
        }
    }
}
