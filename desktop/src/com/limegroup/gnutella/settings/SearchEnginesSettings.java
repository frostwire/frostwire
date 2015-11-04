/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014,, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.settings;

import org.limewire.setting.BooleanSetting;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class SearchEnginesSettings extends LimeProps {
    // In the near future, we will refactor this code to allow a configurable amount of
    // search providers.

    public static final BooleanSetting CLEARBITS_SEARCH_ENABLED = FACTORY.createBooleanSetting("CLEARBITS_SEARCH2_ENABLED", true);

    public static final BooleanSetting MININOVA_SEARCH_ENABLED = FACTORY.createBooleanSetting("MININOVA_SEARCH2_ENABLED", true);

    public static final BooleanSetting KAT_SEARCH_ENABLED = FACTORY.createBooleanSetting("KAT_SEARCH2_ENABLED", true);

    public static final BooleanSetting EXTRATORRENT_SEARCH_ENABLED = FACTORY.createBooleanSetting("EXTRATORRENT_SEARCH2_ENABLED", true);

    public static final BooleanSetting TPB_SEARCH_ENABLED = FACTORY.createBooleanSetting("TPB_SEARCH2_ENABLED", true);

    public static final BooleanSetting MONOVA_SEARCH_ENABLED = FACTORY.createBooleanSetting("MONOVA_SEARCH2_ENABLED", true);

    public static final BooleanSetting YOUTUBE_SEARCH_ENABLED = FACTORY.createBooleanSetting("YOUTUBE_SEARCH2_ENABLED", true);

    public static final BooleanSetting SOUNDCLOUD_SEARCH_ENABLED = FACTORY.createBooleanSetting("SOUNDCLOUD_SEARCH2_ENABLED", true);

    public static final BooleanSetting ARCHIVEORG_SEARCH_ENABLED = FACTORY.createBooleanSetting("ARCHIVEORG_SEARCH2_ENABLED", true);

    public static final BooleanSetting FROSTCLICK_SEARCH_ENABLED = FACTORY.createBooleanSetting("FROSTCLICK_SEARCH_ENABLED", true);

    public static final BooleanSetting BITSNOOP_SEARCH_ENABLED = FACTORY.createBooleanSetting("BITSNOOP_SEARCH_ENABLED", true);

    public static final BooleanSetting TORLOCK_SEARCH_ENABLED = FACTORY.createBooleanSetting("TORLOCK_SEARCH_ENABLED", true);

    public static final BooleanSetting EZTV_SEARCH_ENABLED = FACTORY.createBooleanSetting("EZTV_SEARCH_ENABLED", true);

    public static final BooleanSetting TORRENTS_SEARCH_ENABLED = FACTORY.createBooleanSetting("TORRENTS_SEARCH_ENABLED", true);

    public static final BooleanSetting YIFY_SEARCH_ENABLED = FACTORY.createBooleanSetting("YIFY_SEARCH_ENABLED", true);

    public static final BooleanSetting BTJUNKIE_SEARCH_ENABLED = FACTORY.createBooleanSetting("BTJUNKIE_SEARCH_ENABLED", true);

}