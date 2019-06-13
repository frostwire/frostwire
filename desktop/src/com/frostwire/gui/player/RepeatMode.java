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
