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
