/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.search.glotorrents;

import com.frostwire.search.torrent.AbstractTorrentSearchResult;

public class GloTorrentsSearchResult extends AbstractTorrentSearchResult {
    private final String magnetURL;
    private final String detailURL;
    private final String infoHash;
    private final String filename;
    private final String fileSizeMagnitude;
    private final String fileSizeUnit;
    private final int seeds;
    private final String usualTorrentTrackersMagnetUrlParameters;
    private final long fileSize;

    public GloTorrentsSearchResult(String _magnetURL,
                                   String _detailsURL,
                                   String _infoHash,
                                   String _filename,
                                   String _fileSizeMagnitude,
                                   String _fileSizeUnit,
                                   int _seeds,
                                   String _usualTorrentTrackersMagnetUrlParameters) {
        magnetURL = _magnetURL;
        detailURL = _detailsURL;
        infoHash = _infoHash;
        filename = _filename;
        fileSizeMagnitude = _fileSizeMagnitude;
        fileSizeUnit = _fileSizeUnit;
        fileSize = parseSize(fileSizeMagnitude + " " + fileSizeUnit);
        seeds = _seeds;
        usualTorrentTrackersMagnetUrlParameters = _usualTorrentTrackersMagnetUrlParameters;
    }

    @Override
    public String getTorrentUrl() {
        return magnetURL;
    }

    @Override
    public int getSeeds() {
        return seeds;
    }

    @Override
    public String getHash() {
        return infoHash;
    }

    @Override
    public String getFilename() {
        return filename + ".torrent";
    }

    @Override
    public long getSize() {
        return fileSize;
    }

    @Override
    public String getDisplayName() {
        return filename;
    }

    @Override
    public String getDetailsUrl() {
        return detailURL;
    }

    @Override
    public String getSource() {
        return "GloTorrents";
    }
}
