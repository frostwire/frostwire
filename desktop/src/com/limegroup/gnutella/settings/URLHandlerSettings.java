/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
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

import org.limewire.setting.StringSetting;

/**
 * Settings for programs LimeWire should open to view files on unix.
 */
public final class URLHandlerSettings extends LimeProps {
    /**
     * Setting for which browser to use
     */
    public static final StringSetting BROWSER =
            FACTORY.createStringSetting("BROWSER", "firefox $URL$");
    /**
     * Setting for which movie player to use
     */
    public static final StringSetting VIDEO_PLAYER =
            FACTORY.createStringSetting("VIDEO_PLAYER", "mplayer $URL$");
    /**
     * Setting for which image viewer to use
     */
    public static final StringSetting IMAGE_VIEWER =
            FACTORY.createStringSetting("IMAGE_VIEWER", "firefox $URL$");
    /**
     * Setting for which audio player to use
     */
    public static final StringSetting AUDIO_PLAYER =
            FACTORY.createStringSetting("AUDIO_PLAYER", "mplayer $URL$");

    private URLHandlerSettings() {
    }
}

