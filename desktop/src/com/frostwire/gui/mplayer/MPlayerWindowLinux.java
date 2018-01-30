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

package com.frostwire.gui.mplayer;

import sun.awt.X11.XComponentPeer;

import java.awt.*;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class MPlayerWindowLinux extends MPlayerWindow {

	private static boolean isFullScreen = false;
	
	@Override
	public long getCanvasComponentHwnd() {
		@SuppressWarnings("deprecation")
        XComponentPeer cp = (XComponentPeer) getPeer(videoCanvas);
        if ((cp instanceof XComponentPeer)) {
            return ((XComponentPeer) cp).getWindow();
        } else {
            return 0;
        }
	}

	@Override
	public long getHwnd() {
		@SuppressWarnings("deprecation")
        XComponentPeer cp = (XComponentPeer) getPeer(this);
        if ((cp instanceof XComponentPeer)) {
            return ((XComponentPeer) cp).getWindow();
        } else {
            return 0;
        }
	}
	
	@Override
	public void toggleFullScreen() {
		
		isFullScreen = !isFullScreen;
		
		setExtendedState( isFullScreen ? MAXIMIZED_BOTH : NORMAL);
		super.toggleFullScreen();
	}
	
	// on linux, alpha composite trick not working to get desired background color.  however,
	// changing default paint behavior of window does work.
	@Override
	public void paint(Graphics g) {
    }
}
