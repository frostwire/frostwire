/*
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

package com.limegroup.gnutella.gui.tables;

import org.limewire.util.CommonUtils;

/**
 * simple class to store the numeric value of time remaining (or ETA)
 * used so we can sort by a value, but display a human-readable time.
 *
 * @author sberlin
 */
public final class TimeRemainingHolder implements Comparable<TimeRemainingHolder> {
    private final long _timeRemaining;

    public TimeRemainingHolder(long intValue) {
        _timeRemaining = intValue;
    }

    public int compareTo(TimeRemainingHolder o) {
        return (int) (o._timeRemaining - _timeRemaining);
    }

    public String toString() {
        if (_timeRemaining < 0) {
            return "\u221E";
        } else {
            return _timeRemaining == 0 ? "" : CommonUtils.seconds2time(_timeRemaining);
        }
    }
}
