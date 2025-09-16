/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.settings;

import org.limewire.setting.BooleanSetting;
import org.limewire.setting.FloatSetting;
import org.limewire.setting.IntSetting;

/**
 * Settings for Music Player
 */
public class PlayerSettings extends LimeProps {
    public static final IntSetting LOOP_PLAYLIST = FACTORY.createIntSetting("LOOP_PLAYLIST", 0);//RepeatMode.NONE == 0
    public static final BooleanSetting SHUFFLE_PLAYLIST = FACTORY.createBooleanSetting("SHUFFLE_PLAYLIST", false);
    public static final FloatSetting PLAYER_VOLUME = FACTORY.createFloatSetting("PLAYER_VOLUME", 0.5f);
    public static final BooleanSetting USE_OS_DEFAULT_PLAYER = FACTORY.createBooleanSetting("USE_OS_DEFAULT_PLAYER", false);
    public static final BooleanSetting USE_FW_PLAYER_FOR_CLOUD_VIDEO_PREVIEWS = FACTORY.createBooleanSetting("USE_FW_PLAYER_FOR_CLOUD_VIDEO_PREVIEWS", false);

    private PlayerSettings() {
    }
}
