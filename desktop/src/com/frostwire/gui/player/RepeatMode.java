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

public enum RepeatMode {
    NONE(0), ALL(1), SONG(2);
    private static final RepeatMode[] vals = RepeatMode.values();
    private final int value;

    RepeatMode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /*
     * get next value sequentially in this enumeration
     */
    public RepeatMode getNextState() {
        return (vals[((getValue() + 1) % vals.length)]);
    }
}
