/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2007-2020, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.player;

import com.frostwire.util.Logger;
import com.limegroup.gnutella.util.FrostWireUtils;

import java.io.File;

public class MediaPlayerOSX extends MediaPlayer {
    private static Logger LOG = Logger.getLogger(MediaPlayerOSX.class);
    @Override
    protected String getPlayerPath() {
        //System.out.println("MediaPlayerOSX: getFrostWireJarPath() -> " + FrostWireUtils.getFrostWireJarPath());
        // Path running from command line:  .../frostwire/build/libs
        // Path running from IntelliJ:      .../frostwire/build/classes
        boolean isRelease = !FrostWireUtils.getFrostWireJarPath().contains("frostwire/desktop");
        return (isRelease) ? getReleasePlayerPath() : getNonReleasePlayerPath();
    }

    @Override
    protected float getVolumeGainFactor() {
        return 30.0f;
    }

    private String getReleasePlayerPath() {
        String javaHome = System.getProperty("java.home");
        File f = new File(javaHome).getAbsoluteFile();
        f = f.getParentFile(); // Contents
        f = f.getParentFile(); // jre
        f = f.getParentFile(); // PlugIns
        f = f.getParentFile(); // Contents
        f = new File(f, "MacOS" + File.separator + "fwplayer_osx");
        return f.getAbsolutePath();
    }

    private String getNonReleasePlayerPath() {
        return FrostWireUtils.getDevelopmentFrostWireDesktopFolderPath() + "/lib/native/fwplayer_osx";
    }



    public static void main(String[] args) {
        var player = new MediaPlayerOSX();
        LOG.info("getNonReleasePlayerPath() -> "  + player.getNonReleasePlayerPath());
    }
}
