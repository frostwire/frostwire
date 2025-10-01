/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.util;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Thread-safe date parser utility that caches SimpleDateFormat instances to eliminate
 * per-result allocations during search result parsing.
 * 
 * Uses ThreadLocal to ensure thread safety without synchronization overhead.
 * 
 * @author gubatron
 * @author copilot
 */
public final class DateParser {
    
    // Common date format patterns cached in ThreadLocal for thread safety
    private static final ThreadLocal<SimpleDateFormat> FORMAT_YYYY_MM_DD_HH_MM_SS = 
        ThreadLocal.withInitial(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            sdf.setLenient(false);
            return sdf;
        });
    
    private static final ThreadLocal<SimpleDateFormat> FORMAT_YYYY_MM_DD_T_HH_MM_SS_Z = 
        ThreadLocal.withInitial(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            sdf.setLenient(false);
            return sdf;
        });
    
    private static final ThreadLocal<SimpleDateFormat> FORMAT_YYYY_MM_DD_T_HH_MM_SS_SSS_Z = 
        ThreadLocal.withInitial(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            sdf.setLenient(false);
            return sdf;
        });
    
    private static final ThreadLocal<SimpleDateFormat> FORMAT_YYYY_MM_DD = 
        ThreadLocal.withInitial(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            sdf.setLenient(false);
            return sdf;
        });
    
    private static final ThreadLocal<SimpleDateFormat> FORMAT_DD_MM_YYYY = 
        ThreadLocal.withInitial(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            sdf.setLenient(false);
            return sdf;
        });
    
    private static final ThreadLocal<SimpleDateFormat> FORMAT_MM_DD_YYYY = 
        ThreadLocal.withInitial(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
            sdf.setLenient(false);
            return sdf;
        });
    
    private static final ThreadLocal<SimpleDateFormat> FORMAT_YYYY_MM_DD_SLASH = 
        ThreadLocal.withInitial(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
            sdf.setLenient(false);
            return sdf;
        });
    
    // Array of all cached formatters for fallback parsing
    private static final ThreadLocal<SimpleDateFormat>[] CACHED_FORMATTERS = new ThreadLocal[]{
        FORMAT_YYYY_MM_DD_HH_MM_SS,
        FORMAT_YYYY_MM_DD_T_HH_MM_SS_Z,
        FORMAT_YYYY_MM_DD_T_HH_MM_SS_SSS_Z,
        FORMAT_YYYY_MM_DD,
        FORMAT_DD_MM_YYYY,
        FORMAT_MM_DD_YYYY,
        FORMAT_YYYY_MM_DD_SLASH
    };
    
    private DateParser() {
        // Utility class, no instances
    }
    
    /**
     * Parse a date string using multiple common torrent site date formats.
     * Returns current time if parsing fails.
     * 
     * @param dateString the date string to parse
     * @return timestamp in milliseconds, or current time if parsing fails
     */
    public static long parseTorrentDate(String dateString) {
        long result = System.currentTimeMillis();
        if (dateString == null || dateString.trim().isEmpty()) {
            return result;
        }
        
        try {
            String trimmed = dateString.trim();
            // Try each cached formatter in order
            for (ThreadLocal<SimpleDateFormat> formatterLocal : CACHED_FORMATTERS) {
                try {
                    result = formatterLocal.get().parse(trimmed).getTime();
                    break;
                } catch (Exception ignored) {
                    // Try next format
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
    }
    
    /**
     * Parse an ISO 8601 date string (common in APIs like Archive.org).
     * Format: yyyy-MM-dd'T'HH:mm:ss'Z'
     * 
     * @param dateString the ISO 8601 date string to parse
     * @return timestamp in milliseconds, or -1 if parsing fails
     */
    public static long parseIsoDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return -1;
        }
        
        try {
            return FORMAT_YYYY_MM_DD_T_HH_MM_SS_Z.get().parse(dateString.trim()).getTime();
        } catch (Throwable e) {
            // Try with milliseconds format
            try {
                return FORMAT_YYYY_MM_DD_T_HH_MM_SS_SSS_Z.get().parse(dateString.trim()).getTime();
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }
    
    /**
     * Parse a simple date string in yyyy-MM-dd format.
     * 
     * @param dateString the date string to parse
     * @return timestamp in milliseconds, or current time if parsing fails
     */
    public static long parseSimpleDate(String dateString) {
        long result = System.currentTimeMillis();
        if (dateString == null || dateString.trim().isEmpty()) {
            return result;
        }
        
        try {
            result = FORMAT_YYYY_MM_DD.get().parse(dateString.trim()).getTime();
        } catch (Throwable ignored) {
        }
        return result;
    }
    
    /**
     * Parse relative age strings like "3 days ago", "2 months ago", "1 year ago", etc.
     * 
     * @param ageString the relative age string to parse
     * @return timestamp in milliseconds, or current time if parsing fails
     */
    public static long parseRelativeAge(String ageString) {
        long now = System.currentTimeMillis();
        if (ageString == null || ageString.trim().isEmpty()) {
            return now;
        }
        
        try {
            String lower = ageString.toLowerCase().trim();
            
            // Handle special cases
            if (lower.contains("yesterday")) {
                return now - 24L * 60L * 60L * 1000L;
            }
            if (lower.contains("1 year+") || lower.contains("last year")) {
                return now - 365L * 24L * 60L * 60L * 1000L;
            }
            if (lower.contains("last month")) {
                return now - 31L * 24L * 60L * 60L * 1000L;
            }
            
            // Parse numeric values
            String[] parts = lower.split("\\s+");
            if (parts.length >= 2) {
                try {
                    int value = Integer.parseInt(parts[0]);
                    String unit = parts[1];
                    
                    if (unit.contains("hour")) {
                        return now - (value * 60L * 60L * 1000L);
                    } else if (unit.contains("day")) {
                        return now - (value * 24L * 60L * 60L * 1000L);
                    } else if (unit.contains("week")) {
                        return now - (value * 7L * 24L * 60L * 60L * 1000L);
                    } else if (unit.contains("month")) {
                        return now - (value * 31L * 24L * 60L * 60L * 1000L);
                    } else if (unit.contains("year")) {
                        return now - (value * 365L * 24L * 60L * 60L * 1000L);
                    } else if (unit.contains("minute")) {
                        return now - (value * 60L * 1000L);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            
            // Try to parse as a date if it doesn't match relative format
            return parseSimpleDate(ageString);
        } catch (Throwable ignored) {
        }
        return now;
    }
}
