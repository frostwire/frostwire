/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.transfers;

/**
 * @author gubatron
 * @author aldenml
 */
final class SpeedStat {

    private static final int INTERVAL_MILLISECONDS = 1000;

    private long totalBytes;
    private long averageSpeed; // in bytes

    private long speedMarkTimestamp;
    private long lastTotalBytes;

    public SpeedStat() {
    }

    public long totalBytes() {
        return totalBytes;
    }

    public long averageSpeed() {
        return averageSpeed;
    }

    public void update(long numBytes) {
        long now = System.currentTimeMillis();

        totalBytes += numBytes;

        if (now - speedMarkTimestamp > INTERVAL_MILLISECONDS) {
            averageSpeed = ((totalBytes - lastTotalBytes) * 1000) / (now - speedMarkTimestamp);
            speedMarkTimestamp = now;
            lastTotalBytes = totalBytes;
        }
    }
}
