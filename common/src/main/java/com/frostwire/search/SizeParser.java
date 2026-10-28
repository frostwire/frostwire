/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.search;

import com.frostwire.util.StringUtils;

/**
 * Unified size parser for torrent and file search results.
 * Handles various size formats and unit systems used across different search engines.
 *
 * Supports:
 * - Standard units: B, KB, MB, GB, TB, PB
 * - Binary units: KiB, MiB, GiB, TiB, PiB
 * - Various separators: space, &nbsp; (HTML entity)
 * - Non-breaking spaces (char 160)
 *
 * @author gubatron
 */
public final class SizeParser {
    // Long arithmetic: in int, 1024^4 and 1024^5 overflowed to 0, so every TB and PB size was 0.
    private static final long KILOBYTE = 1024L;
    private static final long MEGABYTE = 1024L * 1024;
    private static final long GIGABYTE = 1024L * 1024 * 1024;
    private static final long TERABYTE = 1024L * 1024 * 1024 * 1024;
    private static final long PETABYTE = 1024L * 1024 * 1024 * 1024 * 1024;

    private SizeParser() {
        // Utility class - no instances
    }

    /**
     * Parse size string with space separator (standard format).
     * Examples: "1.23 GB", "456 MB", "789 KB", "1 B"
     *
     * @param sizeStr the size string to parse
     * @return size in bytes, or -1 if parsing fails
     */
    public static long parseSize(String sizeStr) {
        return parseSize(sizeStr, "\\s+");
    }

    /**
     * Parse size string with custom separator.
     * Examples with "&nbsp;" separator: "1.23&nbsp;GB"
     *
     * @param sizeStr the size string to parse
     * @param separator regex pattern for separator
     * @return size in bytes, or -1 if parsing fails
     */
    public static long parseSize(String sizeStr, String separator) {
        if (StringUtils.isNullOrEmpty(sizeStr)) {
            return -1;
        }

        try {
            // Handle non-breaking space (char 160) and normalize
            sizeStr = sizeStr.replace((char) 160, ' ').trim().toUpperCase();

            // Split by separator
            String[] parts = sizeStr.split(separator);
            if (parts.length < 2) {
                return -1;
            }

            double value = Double.parseDouble(parts[0].trim());
            String unit = parts[1].trim();

            return convertToBytes(value, unit);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Convert a numeric value from a given unit to bytes.
     *
     * @param value the numeric value
     * @param unit the size unit (B, KB, MB, GB, TB, PB, KiB, MiB, GiB, TiB, PiB)
     * @return size in bytes, or -1 if the unit is unrecognized or the size is negative, not a
     * number or too large for a long. Sizes come from search engines' pages.
     */
    public static long convertToBytes(double value, String unit) {
        if (unit == null || !(value >= 0) || Double.isInfinite(value)) {
            return -1;
        }

        long multiplier = switch (unit) {
            case "B", "BYTES" -> 1;
            case "KB", "KIB" -> KILOBYTE;
            case "MB", "MIB" -> MEGABYTE;
            case "GB", "GIB" -> GIGABYTE;
            case "TB", "TIB" -> TERABYTE;
            case "PB", "PIB" -> PETABYTE;
            default -> -1;
        };
        if (multiplier < 0) {
            return -1;
        }
        double bytes = value * multiplier;
        // (long) of anything at or past 2^63 would silently become Long.MAX_VALUE.
        return bytes < 0x1p63 ? (long) bytes : -1;
    }
}
