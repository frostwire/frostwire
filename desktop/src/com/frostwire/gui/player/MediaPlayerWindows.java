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

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import com.limegroup.gnutella.util.FrostWireUtils;

public class MediaPlayerWindows extends MediaPlayer {

	protected String getPlayerPath() {
		String playerPath;
		
		boolean isRelease = !FrostWireUtils.getFrostWireJarPath().contains("frostwire-desktop");

		playerPath = (isRelease) ? FrostWireUtils.getFrostWireJarPath() + File.separator + "fwplayer.exe" : "lib/native/fwplayer.exe";
        playerPath = decode(playerPath);

        if (!new File(playerPath).exists()) {
            playerPath = decode("../lib/native/fwplayer.exe");
        }
        
        return playerPath;
	}
	
    private static String decode(String s) {
        if (s == null) {
            return "";
        }
        try {
            return (URLDecoder.decode(s, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }
}
