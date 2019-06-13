/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Erich Pleny (erichpleny)
 * Copyright (c) 2012-2018, FrostWire(R). All rights reserved.
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

import com.limegroup.gnutella.util.FrostWireUtils;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class MediaPlayerWindows extends MediaPlayer {
    private static String decode(String s) {
        if (s == null) {
            return "";
        }
        return (URLDecoder.decode(s, StandardCharsets.UTF_8));
    }

    protected String getPlayerPath() {
        boolean isRelease = !FrostWireUtils.getFrostWireJarPath().contains("desktop\\build\\libs");
        String playerPath = (isRelease) ? FrostWireUtils.getFrostWireJarPath() + File.separator + "fwplayer.exe" : "lib/native/fwplayer.exe";
        playerPath = decode(playerPath);
        if (!new File(playerPath).exists()) {
            playerPath = decode("lib/native/fwplayer.exe");
        }
        return playerPath;
    }
}
