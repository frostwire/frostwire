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

package com.frostwire.android.gui;

import android.content.Context;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Finger {
    public interface FingerCallback {
        void onFinger(final Context context, final Finger finger);
    }

    public final int numTotalAudioFiles;
    public final int numTotalVideoFiles;
    public final int numTotalPictureFiles;
    public final int numTotalDocumentFiles;
    public final int numTotalTorrentFiles;
    public final int numTotalRingtoneFiles;

    public Finger(int numAudio, int numVideo, int numPics, int numDocs, int numTorrents, int numRingtones) {
        numTotalAudioFiles = numAudio;
        numTotalVideoFiles = numVideo;
        numTotalPictureFiles = numPics;
        numTotalDocumentFiles = numDocs;
        numTotalTorrentFiles = numTorrents;
        numTotalRingtoneFiles = numRingtones;
    }

    @Override
    public String toString() {
        return "(" +
                "aud:" + numTotalAudioFiles + ", " +
                "vid:" + numTotalVideoFiles + ", " +
                "pic:" + numTotalPictureFiles + ", " +
                "doc:" + numTotalDocumentFiles + ", " +
                "app:" + numTotalTorrentFiles + ", " +
                "rng:" + numTotalRingtoneFiles +
                ")";
    }
}
