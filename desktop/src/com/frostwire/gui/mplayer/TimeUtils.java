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

package com.frostwire.gui.mplayer;

class TimeUtils {
    private static int getHours(int rawSeconds) {
        return rawSeconds / 3600;
    }

    private static int getMinutes(int rawSeconds) {
        return (rawSeconds - (TimeUtils.getHours(rawSeconds) * 3600)) / 60;
    }

    private static int getSeconds(int rawSeconds) {
        return rawSeconds - (TimeUtils.getHours(rawSeconds) * 3600)
                - (TimeUtils.getMinutes(rawSeconds) * 60);
    }

    public static String getTimeFormatedString(int rawSeconds) {
        String time;
        int hours = getHours(rawSeconds);
        int minutes = getMinutes(rawSeconds);
        int seconds = getSeconds(rawSeconds);
        if (hours > 0)
            time = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        else
            time = String.format("%02d:%02d", minutes, seconds);
        return time;
    }
}
