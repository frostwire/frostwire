/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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

package com.frostwire.transfers;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public interface BittorrentDownload extends Transfer {
    String getInfoHash();

    /**
     * Generates a magnet URI using the current information in
     * the torrent. If the underlying torrent handle is invalid,
     * null is returned.
     *
     * @return
     */
    String magnetUri();

    int getConnectedPeers();

    int getTotalPeers();

    int getConnectedSeeds();

    int getTotalSeeds();

    /**
     * For multi files torrents, returns the folder containing the files (savePath/torrentName)
     * For single file torrents, returns the path to the single file of the torrent (savePath/singleFile)
     *
     * @return
     */
    File getContentSavePath();

    boolean isPaused();

    boolean isSeeding();

    boolean isFinished();

    void pause();

    void resume();

    void remove(boolean deleteTorrent, boolean deleteData);

    /**
     * Adds up the number of bytes per file extension and returns
     * the winning file extension for the torrent.
     * <p>
     * If the files are not known, then it returns "torrent"
     *
     * @return
     */
    String getPredominantFileExtension();
}
