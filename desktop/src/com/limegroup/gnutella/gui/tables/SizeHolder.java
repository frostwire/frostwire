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
