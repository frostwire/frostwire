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
