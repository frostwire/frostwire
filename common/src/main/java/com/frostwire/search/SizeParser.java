/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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
    private static final long KILOBYTE = 1024;
    private static final long MEGABYTE = 1024 * 1024;
    private static final long GIGABYTE = 1024 * 1024 * 1024;
    private static final long TERABYTE = 1024 * 1024 * 1024 * 1024;
    private static final long PETABYTE = 1024 * 1024 * 1024 * 1024 * 1024;

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
     * @return size in bytes, or -1 if unit is unrecognized
     */
    public static long convertToBytes(double value, String unit) {
        if (unit == null) {
            return -1;
        }

        return switch (unit) {
            case "B" -> (long) value;
            case "KB", "KIB" -> (long) (value * KILOBYTE);
            case "MB", "MIB" -> (long) (value * MEGABYTE);
            case "GB", "GIB" -> (long) (value * GIGABYTE);
            case "TB", "TIB" -> (long) (value * TERABYTE);
            case "PB", "PIB" -> (long) (value * PETABYTE);
            default -> -1;
        };
    }
}
