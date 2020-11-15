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

import org.apache.commons.io.IOUtils;
import org.limewire.util.FileUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

final class M3UPlaylist {
    private static final String M3U_HEADER = "#EXTM3U";
    private static final String SONG_DELIM = "#EXTINF";
    private static final String SEC_DELIM = ":";

    /**
     * @throws IOException Thrown if load failed.<p>
     *                     <p>
     *                     Format of playlist (.m3u) files is:<br>
     *                     ----------------------<br>
     *                     #EXTM3U<br>
     *                     #EXTINF:numSeconds<br>
     *                     /path/of/file/1<br>
     *                     #EXTINF:numSeconds<br>
     *                     /path/of/file/2<br>
     *                     ----------------------<br>
     */
    public static List<File> load(String fileName) throws IOException {
        List<File> files = new ArrayList<>();
        File playListFile = new File(fileName);
        BufferedReader m3uFile = null;
        try {
            m3uFile = new BufferedReader(new FileReader(playListFile));
            String currLine = null;
            currLine = m3uFile.readLine();
            if (currLine == null || !(currLine.startsWith(M3U_HEADER) || currLine.startsWith(SONG_DELIM)))
                throw new IOException();
            if (currLine.startsWith(M3U_HEADER))
                currLine = m3uFile.readLine();
            for (; currLine != null; currLine = m3uFile.readLine()) {
                if (currLine.startsWith(SONG_DELIM)) {
                    currLine = m3uFile.readLine();
                    if (currLine == null)
                        break;
                    File toAdd = new File(currLine);
                    if (toAdd.exists() && !toAdd.isDirectory())
                        files.add(toAdd);
                    else {
                        // try relative path to the playlist
                        toAdd = new File(playListFile.getParentFile().getAbsolutePath(), toAdd.getPath());
                        if (toAdd.exists() && !toAdd.isDirectory() && FileUtils.isReallyInParentPath(playListFile.getParentFile(), toAdd))
                            files.add(toAdd);
                    }
                }
            }
        } finally {
            IOUtils.closeQuietly(m3uFile);
        }
        return files;
    }

    /**
     * Call this when you want to save the contents of the playlist.
     * NOTE: only local files can be saved in M3U format, filters out URLs
     * that are not part of the local filesystem
     *
     * @throws IOException Throw when save failed.
     * @throws IOException Throw when save failed.
     */
    public static void save(String fileName, List<File> files) throws IOException {
        File playListFile = new File(fileName);
        // if all songs are new, just get rid of the old file.  this may
        // happen if a delete was done....
        if (files.size() == 0) {
            //            if (playListFile.exists()) {
            //                playListFile.delete();
            //            }
            return;
        }
        PrintWriter m3uFile = null;
        try {
            m3uFile = new PrintWriter(new FileWriter(playListFile.getCanonicalPath(), false));
            m3uFile.write(M3U_HEADER);
            m3uFile.println();
            for (File currFile : files) {
                // only save files that are local to the file system
                if (currFile.isFile()) {
                    File locFile;
                    locFile = new File(currFile.toURI());
                    // first line of song description...
                    m3uFile.write(SONG_DELIM);
                    m3uFile.write(SEC_DELIM);
                    // try to write out seconds info....
                    //if( currFile.getProperty(PlayListItem.LENGTH) != null )
                    //    m3uFile.write("" + currFile.getProperty(PlayListItem.LENGTH) + ",");
                    //else
                    m3uFile.write("" + -1 + ",");
                    m3uFile.write(currFile.getName());
                    m3uFile.println();
                    // canonical path follows...
                    m3uFile.write(locFile.getCanonicalPath());
                    m3uFile.println();
                }
            }
        } finally {
            IOUtils.closeQuietly(m3uFile);
        }
    }
}
