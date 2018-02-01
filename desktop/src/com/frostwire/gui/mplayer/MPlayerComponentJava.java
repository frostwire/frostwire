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

import java.awt.*;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class MPlayerComponentJava extends Canvas implements MPlayerComponent {

	public MPlayerComponentJava() {
		
    }
	
	@Override
	public Component getComponent() {
		return this;
	}

	@SuppressWarnings("deprecation")
	@Override
	public long getWindowID() {
		long hWnd = 0;
		
		try {
			Class<?> cl = Class.forName("sun.awt.windows.WComponentPeer");
	        java.lang.reflect.Field f = cl.getDeclaredField("hwnd");
	        f.setAccessible(true); //little reflection hack to access the hwnd from windows.
	        hWnd = f.getLong(MPlayerWindow.getPeer(this));
    	
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return hWnd;
	}

	@Override
	public boolean toggleFullScreen() {
		return false;
	}
}
