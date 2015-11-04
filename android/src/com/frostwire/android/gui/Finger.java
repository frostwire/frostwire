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

package com.frostwire.android.gui;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Finger {
    // total data

    public int numTotalAudioFiles;

    public int numTotalVideoFiles;

    public int numTotalPictureFiles;

    public int numTotalDocumentFiles;

    public int numTotalTorrentFiles;

    public int numTotalRingtoneFiles;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("(");
        sb.append("aud:" + numTotalAudioFiles + ", ");
        sb.append("vid:" + numTotalVideoFiles + ", ");
        sb.append("pic:" + numTotalPictureFiles + ", ");
        sb.append("doc:" + numTotalDocumentFiles + ", ");
        sb.append("app:" + numTotalTorrentFiles + ", ");
        sb.append("rng:" + numTotalRingtoneFiles);
        sb.append(")");

        return sb.toString();
    }
}
