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

package com.frostwire.alexandria.db;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

public class DumpDatabase {

    private static final String TABLE_SEPARATOR = "------------------------------------------------------------------------------";

    private final LibraryDatabase db;
    private final File file;

    public DumpDatabase(LibraryDatabase db, File file) {
        this.db = db;
        this.file = file;
    }

    public void dump() {
        PrintWriter out = null;

        try {
            out = new PrintWriter(file);
            dumpPlaylists(out);
            dumpPlaylistItems(out);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    private void dumpTable(PrintWriter out, String columns, String table) {
        List<List<Object>> rows = db.query("SELECT " + columns + " FROM " + table);
        out.println(columns);
        dumpRows(out, rows);
        out.println(TABLE_SEPARATOR);
    }

    private void dumpRows(PrintWriter out, List<List<Object>> rows) {
        for (List<Object> row : rows) {
            dumpRow(out, row);
        }
    }

    private void dumpRow(PrintWriter out, List<Object> row) {
        int lastIndex = row.size() - 1;
        for (int i = 0; i < lastIndex; i++) {
            out.print(row.get(i));
            out.print(", ");
        }
        out.print(row.get(lastIndex));
        out.println();
    }

    private void dumpPlaylists(PrintWriter out) {
        String columns = "playlistId, name, description";
        dumpTable(out, columns, "Playlists");
    }

    private void dumpPlaylistItems(PrintWriter out) {
        String columns = "playlistItemId, filePath, fileName, fileSize, fileExtension, trackTitle, duration, artistName, albumName, coverArtPath, bitrate, comment, genre, track, year";
        dumpTable(out, columns, "PlaylistItems");
    }
}
