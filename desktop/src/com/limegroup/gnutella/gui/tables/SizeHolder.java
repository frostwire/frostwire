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

import com.limegroup.gnutella.gui.GUIUtils;

/**
 * Wrapper class that holds on to the size integer for a file so that
 * we don't have to read it from disk every time while sorting.
 */
public final class SizeHolder implements Comparable<SizeHolder> {
    /**
     * Variable for the string representation of the file size.
     */
    private final String _string;
    /**
     * Variable for the size of the file in kilobytes.
     */
    private final double _size;

    /**
     * The constructor sets the size and string variables, creating a
     * formatted string in kilobytes from the size value.
     *
     * @param size the size of the file in kilobytes
     */
    public SizeHolder(double size, String moreInfo) {
        if (size >= 0) {
            _string = GUIUtils.getBytesInHuman(size) + moreInfo;
            _size = size;
        } else {
            _string = "--";
            _size = -1;
        }
    }

    public SizeHolder(double size) {
        this(size, "");
    }

    public int compareTo(SizeHolder o) {
        double otherSize = o.getSize();
        if (_size > otherSize)
            return 1;
        else if (_size < otherSize)
            return -1;
        else
            return 0;
    }

    /**
     * Returns the string value of this size, formatted with commas and
     * "KB" appended to the end.
     *
     * @return the formatted string representing the size
     */
    public String toString() {
        return _string;
    }

    /**
     * Returns the size of the file in kilobytes.
     *
     * @return the size of the file in kilobytes
     */
    public double getSize() {
        return _size;
    }
}
