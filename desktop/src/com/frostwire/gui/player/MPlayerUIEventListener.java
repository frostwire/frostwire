/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Erich Pleny (erichpleny)
 * Copyright (c) 2012, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.player;

interface MPlayerUIEventListener {
    void onUIVolumeChanged(float volume);

    void onUIVolumeIncremented();

    void onUIVolumeDecremented();

    void onUISeekToTime(float seconds);

    void onUIPlayPressed();

    void onUIPausePressed();

    void onUITogglePlayPausePressed();

    void onUIFastForwardPressed();

    void onUIRewindPressed();

    void onUIToggleFullscreenPressed();

    void onUIProgressSlideStart();

    void onUIProgressSlideEnd();
}
