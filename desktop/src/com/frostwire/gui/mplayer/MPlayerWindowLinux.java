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

package com.frostwire.gui.mplayer;

import org.limewire.util.SystemUtils;

import java.awt.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class MPlayerWindowLinux extends MPlayerWindow {
    private static boolean isFullScreen = false;

    @Override
    public long getCanvasComponentHwnd() {
        return SystemUtils.getWindowHandle(videoCanvas);
    }

    @Override
    public long getHwnd() {
        return SystemUtils.getWindowHandle(this);
    }

    @Override
    public void toggleFullScreen() {
        isFullScreen = !isFullScreen;
        setExtendedState(isFullScreen ? MAXIMIZED_BOTH : NORMAL);
        super.toggleFullScreen();
    }

    // on linux, alpha composite trick not working to get desired background color.  however,
    // changing default paint behavior of window does work.
    @Override
    public void paint(Graphics g) {
    }
}
