/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

import com.frostwire.search.SearchResult;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class TemporaryDownloadTransfer<T extends SearchResult> implements DownloadTransfer {

    protected File savePath;
    protected HttpDownload delegate;
    protected T sr;

    @Override
    public File getSavePath() {
        File path = savePath;

        if (savePath == null && delegate != null) {
            path = delegate.getSavePath();
        }

        return path;
    }

    @Override
    public String getDetailsUrl() {
        return sr.getDetailsUrl();
    }

    public T getTag() {
        return sr;
    }
}
