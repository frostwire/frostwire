/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Erich Pleny (erichpleny)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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
