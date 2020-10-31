/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
 *
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
    private final double fileSize;

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
    public double getSize() {
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
