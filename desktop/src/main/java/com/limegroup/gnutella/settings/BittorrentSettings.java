/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.limegroup.gnutella.settings;

import org.limewire.setting.BooleanSetting;
import org.limewire.setting.IntSetting;

/**
 * Bittorrent settings
 */
public final class BittorrentSettings extends LimeProps {
    /**
     * Records what was the last sorting order of the sort column for the transfer manager.
     * false -> Descending
     * true -> Ascending
     */
    public static final BooleanSetting BTMEDIATOR_COLUMN_SORT_ORDER = FACTORY.createBooleanSetting("BTMEDIATOR_COLUMN_SORT_ORDER", true);
    /**
     * Records what was the last column you used to sort the transfers table.
     */
    public static final IntSetting BTMEDIATOR_COLUMN_SORT_INDEX = FACTORY.createIntSetting("BTMEDIATOR_COLUMN_SORT_INDEX", -1);

    private BittorrentSettings() {
    }
}
