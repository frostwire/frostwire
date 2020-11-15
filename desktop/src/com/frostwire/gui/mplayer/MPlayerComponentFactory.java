/*
 * Created by Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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
 * @author aldenml
 */
public final class MPlayerComponentFactory {
    private static final String JMPLAYER_LIBRARY = "JMPlayer";
    private static final String OS_NAME = System.getProperty("os.name");
    private static final boolean IS_OS_WINDOWS = isCurrentOS("Windows");
    private static final boolean IS_OS_LINUX = isCurrentOS("Linux");
    private static final boolean IS_OS_MAC = isCurrentOS("Mac");
    private static boolean nativeLibLoaded = false;
    private static MPlayerComponentFactory instance;

    private MPlayerComponentFactory() {
    }

    public static synchronized MPlayerComponentFactory instance() {
        if (instance == null) {
            instance = new MPlayerComponentFactory();
        }
        return instance;
    }

    /**
     * Used to check whether the current operating system matches a operating
     * system name (osName).
     *
     * @param osName Name of an operating system we are looking for as being part
     *               of the System property os.name
     * @return true, if osName matches the current operating system,false if not
     * and if osName is null
     */
    private static boolean isCurrentOS(String osName) {
        return osName != null && (OS_NAME.contains(osName));
    }

    MPlayerComponent createPlayerComponent() {
        if (loadLibrary()) {
            if (IS_OS_WINDOWS) {
                return new MPlayerComponentJava();
            } else if (IS_OS_MAC) {
                return new MPlayerComponentOSX2();
            } else if (IS_OS_LINUX) {
                return new MPlayerComponentJava();
            }
        }
        return null;
    }

    private boolean loadLibrary() {
        if (!nativeLibLoaded) {
            try {
                //force loading of libjawt.so/jawt.dll
                Toolkit.getDefaultToolkit();
                if (IS_OS_MAC) {
                    System.loadLibrary(JMPLAYER_LIBRARY);
                }
                nativeLibLoaded = true;
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return nativeLibLoaded;
    }
}
