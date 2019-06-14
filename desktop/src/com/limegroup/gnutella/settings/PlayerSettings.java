/*
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
