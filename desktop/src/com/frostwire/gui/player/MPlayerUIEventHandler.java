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

import java.util.LinkedList;

public class MPlayerUIEventHandler {
    private static MPlayerUIEventHandler instance = null;
    private final LinkedList<MPlayerUIEventListener> listeners = new LinkedList<>();

    private MPlayerUIEventHandler() {
    }

    public static MPlayerUIEventHandler instance() {
        if (instance == null) {
            instance = new MPlayerUIEventHandler();
        }
        return instance;
    }

    void addListener(MPlayerUIEventListener listener) {
        listeners.add(listener);
    }

    public void onVolumeChanged(float volume) {
        for (MPlayerUIEventListener listener : listeners) {
            listener.onUIVolumeChanged(volume);
        }
    }

    public void onVolumeIncremented() {
        for (MPlayerUIEventListener listener : listeners) {
            listener.onUIVolumeIncremented();
        }
    }

    public void onVolumeDecremented() {
        for (MPlayerUIEventListener listener : listeners) {
            listener.onUIVolumeDecremented();
        }
    }

    public void onSeekToTime(float seconds) {
        for (MPlayerUIEventListener listener : listeners) {
            listener.onUISeekToTime(seconds);
        }
    }

    public void onTogglePlayPausePressed() {
        for (MPlayerUIEventListener listener : listeners) {
            listener.onUITogglePlayPausePressed();
        }
    }

    public void onPlayPressed() {
        for (MPlayerUIEventListener listener : listeners) {
            listener.onUIPlayPressed();
        }
    }

    public void onPausePressed() {
        for (MPlayerUIEventListener listener : listeners) {
            listener.onUIPausePressed();
        }
    }

    public void onFastForwardPressed() {
        for (MPlayerUIEventListener listener : listeners) {
            listener.onUIFastForwardPressed();
        }
    }

    public void onRewindPressed() {
        for (MPlayerUIEventListener listener : listeners) {
            listener.onUIRewindPressed();
        }
    }

    public void onToggleFullscreenPressed() {
        for (MPlayerUIEventListener listener : listeners) {
            listener.onUIToggleFullscreenPressed();
        }
    }

    public void onProgressSlideStart() {
        for (MPlayerUIEventListener listener : listeners) {
            listener.onUIProgressSlideStart();
        }
    }

    public void onProgressSlideEnd() {
        for (MPlayerUIEventListener listener : listeners) {
            listener.onUIProgressSlideEnd();
        }
    }
}
