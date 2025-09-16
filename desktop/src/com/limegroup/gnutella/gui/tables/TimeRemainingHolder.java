/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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
