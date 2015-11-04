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

public interface MPlayerUIEventListener {

	public void onUIVolumeChanged(float volume);
	public void onUIVolumeIncremented();
	public void onUIVolumeDecremented();
    public void onUISeekToTime(float seconds);
    public void onUIPlayPressed();
    public void onUIPausePressed();
    public void onUITogglePlayPausePressed();
    public void onUIFastForwardPressed();
    public void onUIRewindPressed();
    public void onUIToggleFullscreenPressed();
	public void onUIProgressSlideStart();
	public void onUIProgressSlideEnd();
	
}
