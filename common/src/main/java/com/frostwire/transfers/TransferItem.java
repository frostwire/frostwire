/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.frostwire.transfers;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public interface TransferItem {
    String getName();

    String getDisplayName();

    /**
     * Actual file in the file system to which the data is saved. Ideally it should be
     * inside the save path of the parent transfer.
     *
     * @return
     */
    File getFile();

    long getSize();

    boolean isSkipped();

    long getDownloaded();

    /**
     * [0..100]
     *
     * @return
     */
    int getProgress();

    boolean isComplete();
}
