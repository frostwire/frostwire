/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.settings;

import org.limewire.setting.BooleanSetting;

/**
 * @author gubatron
 * @author aldenml
 */
public class SearchEnginesSettings extends LimeProps {
    // In the near future, we will refactor this code to allow a configurable amount of
    // search providers.
    public static final BooleanSetting TPB_SEARCH_ENABLED = FACTORY.createBooleanSetting("TPB_SEARCH2_ENABLED", true);
    public static final BooleanSetting SOUNDCLOUD_SEARCH_ENABLED = FACTORY.createBooleanSetting("SOUNDCLOUD_SEARCH2_ENABLED", true);
    public static final BooleanSetting ARCHIVEORG_SEARCH_ENABLED = FACTORY.createBooleanSetting("ARCHIVEORG_SEARCH2_ENABLED", true);
    public static final BooleanSetting FROSTCLICK_SEARCH_ENABLED = FACTORY.createBooleanSetting("FROSTCLICK_SEARCH_ENABLED", true);
    public static final BooleanSetting TORLOCK_SEARCH_ENABLED = FACTORY.createBooleanSetting("TORLOCK_SEARCH_ENABLED", true);
    public static final BooleanSetting TORRENTDOWNLOADS_SEARCH_ENABLED = FACTORY.createBooleanSetting("TORRENTDOWNLOADS_SEARCH_ENABLED", true);
    public static final BooleanSetting LIMETORRENTS_SEARCH_ENABLED = FACTORY.createBooleanSetting("LIMETORRENTS_SEARCH_ENABLED", true);
    public static final BooleanSetting EZTV_SEARCH_ENABLED = FACTORY.createBooleanSetting("EZTV_SEARCH_ENABLED", true);
    public static final BooleanSetting YIFY_SEARCH_ENABLED = FACTORY.createBooleanSetting("YIFY_SEARCH_ENABLED", true);
    public static final BooleanSetting ZOOQLE_SEARCH_ENABLED = FACTORY.createBooleanSetting("ZOOQLE_SEARCH_ENABLED", true);
    public static final BooleanSetting TORRENTZ2_SEARCH_ENABLED = FACTORY.createBooleanSetting("TORRENTZ2_SEARCH_ENABLED", true);
    public static final BooleanSetting NYAA_SEARCH_ENABLED = FACTORY.createBooleanSetting("NYAA_SEARCH_ENABLED", true);
}
