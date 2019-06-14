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

package org.limewire.util;

/**
 * Converts and splits strings to a normalized versions for internationalization.
 * Implementations should use the provided string composition method.
 */
abstract class AbstractI18NConverter {
    /**
     * copy from Character.java
     * the boundaries for each of the unicode blocks
     */
    private static final char[] blockStarts = {
            '\u0000',
            '\u0080',
            '\u0100',
            '\u0180',
            '\u0250',
            '\u02B0',
            '\u0300',
            '\u0370',
            '\u0400',
            '\u0500', // unassigned
            '\u0530',
            '\u0590',
            '\u0600',
            '\u0700', // unassigned
            '\u0900',
            '\u0980',
            '\u0A00',
            '\u0A80',
            '\u0B00',
            '\u0B80',
            '\u0C00',
            '\u0C80',
            '\u0D00',
            '\u0D80', // unassigned
            '\u0E00',
            '\u0E80',
            '\u0F00',
            '\u0FC0', // unassigned
            '\u10A0',
            '\u1100',
            '\u1200', // unassigned
            '\u13A0',
            '\u1400',
            '\u1680',
            '\u16A0',
            '\u1700',
            '\u1720',
            '\u1740',
            '\u1760',
            '\u1780',
            '\u1800',
            '\u1900',
            '\u1950',
            '\u19E0',
            '\u1D00',
            '\u1E00',
            '\u1F00',
            '\u2000',
            '\u2070',
            '\u20A0',
            '\u20D0',
            '\u2100',
            '\u2150',
            '\u2190',
            '\u2200',
            '\u2300',
            '\u2400',
            '\u2440',
            '\u2460',
            '\u2500',
            '\u2580',
            '\u25A0',
            '\u2600',
            '\u2700',
            '\u27C0', // unassigned
            '\u3000',
            '\u3040',
            '\u30A0',
            '\u3100',
            '\u3130',
            '\u3190',
            '\u3200',
            '\u3300',
            '\u3400', // unassigned
            '\u4E00',
            '\uA000', // unassigned
            '\uAC00',
            '\uD7A4', // unassigned
            '\uD800',
            '\uE000',
            '\uF900',
            '\uFB00',
            '\uFB50',
            '\uFE00', // unassigned
            '\uFE20',
            '\uFE30',
            '\uFE50',
            '\uFE70',
            '\uFEFF', // special
            '\uFF00',
            '\uFFF0'
    };

    /**
     * This method should return the converted form of the string s
     * this method should also split s into the different
     * unicode blocks
     *
     * @param s String to be converted
     * @return the converted string
     */
    public abstract String getNorm(String s);

    /**
     * Simple composition of a string.
     */
    public abstract String compose(String s);

    /**
     * Returns a string split according to the unicode blocks.  A
     * space '\u0020' will be splaced between the blocks.
     * The index to the blockStarts array will be used to compare
     * when splitting the string.
     *
     * @return string split into blocks with '\u0020' as the delim
     */
    String blockSplit(String s) {
        if (s.length() == 0) return s;
        else {
            int blockb4 = of(s.charAt(0));
            int curBlock;
            StringBuilder buf = new StringBuilder();
            buf.append(s.charAt(0));
            for (int i = 1, n = s.length(); i < n; i++) {
                curBlock = of(s.charAt(i));
                //compare the blocks of the current char and the char
                //right before. Also, make sure we don't add too many
                //'\u0020' chars
                if (curBlock != blockb4 &&
                        (s.charAt(i) != '\u0020' && s.charAt(i - 1) != '\u0020'))
                    buf.append("\u0020");
                buf.append(s.charAt(i));
                blockb4 = curBlock;
            }
            //get rid of trailing space (if any)
            return buf.toString().trim();
        }
    }

    /**
     * Returns which unicode block the parameter c
     * belongs to. The returned int is the index to the blockStarts
     * array.
     *
     * @return index to array
     */
    private int of(char c) {
        int top, bottom, current;
        bottom = 0;
        top = blockStarts.length;
        current = top / 2;
        while (top - bottom > 1) {
            if (c >= blockStarts[current]) {
                bottom = current;
            } else {
                top = current;
            }
            current = (top + bottom) / 2;
        }
        return current;
    }
}
