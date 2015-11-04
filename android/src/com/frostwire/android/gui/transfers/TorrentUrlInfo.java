/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.transfers;

import org.apache.commons.io.FilenameUtils;

/**
 * @author gubatron
 * @author aldenml
 * 
 */
class TorrentUrlInfo implements TorrentDownloadInfo {

    private final String url;

    public TorrentUrlInfo(String url) {
        this.url = url;
    }

    @Override
    public String getTorrentUrl() {
        return url;
    }

    @Override
    public String getDetailsUrl() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return FilenameUtils.getName(url);
    }

    @Override
    public long getSize() {
        return -1;
    }

    @Override
    public String getHash() {
        return null;
    }

    @Override
    public String makeMagnetUri() {
        return null;
    }


    @Override
    public String getRelativePath() {
        return null;
    }

    @Override
    public String getReferrerUrl() {
        return null;
    }
}
