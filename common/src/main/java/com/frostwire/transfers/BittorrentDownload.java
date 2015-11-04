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

package com.frostwire.transfers;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public interface BittorrentDownload extends DownloadTransfer, UploadTransfer {

    public String getInfoHash();

    public int getConnectedPeers();

    public int getTotalPeers();

    public int getConnectedSeeds();

    public int getTotalSeeds();

    /**
     * For multi files torrents, returns the folder containing the files (savePath/torrentName)
     * For single file torrents, returns the path to the single file of the torrent (savePath/singleFile)
     *
     * @return
     */
    public File getContentSavePath();

    public boolean isPaused();

    public boolean isSeeding();

    public boolean isFinished();

    public void pause();

    public void resume();

    public void remove(boolean deleteTorrent, boolean deleteData);
}
