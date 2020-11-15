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

package com.limegroup.gnutella.gui;

import com.frostwire.gui.mplayer.MPlayerWindow;

public class MPlayerMediator {
    private static MPlayerMediator instance;
    private final MPlayerWindow mplayerWindow;

    private MPlayerMediator() {
        mplayerWindow = MPlayerWindow.createMPlayerWindow();
    }

    public static MPlayerMediator instance() {
        if (instance == null) {
            try {
                GUIMediator.safeInvokeAndWait(() -> instance = new MPlayerMediator());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return instance;
    }

    public MPlayerWindow getMPlayerWindow() {
        return mplayerWindow;
    }

    public long getCanvasComponentHwnd() {
        return mplayerWindow.getCanvasComponentHwnd();
    }

    public void showPlayerWindow(final boolean visible) {
        try {
            GUIMediator.safeInvokeAndWait(() -> {
                if (mplayerWindow != null) {
                    mplayerWindow.setVisible(visible);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void toggleFullScreen() {
        try {
            GUIMediator.safeInvokeAndWait(mplayerWindow::toggleFullScreen);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
