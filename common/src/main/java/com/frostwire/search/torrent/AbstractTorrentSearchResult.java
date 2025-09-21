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

package com.frostwire.search.torrent;

import com.frostwire.search.AbstractFileSearchResult;

import java.util.HashMap;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractTorrentSearchResult extends AbstractFileSearchResult implements TorrentCrawlableSearchResult {
    private final static long[] BYTE_MULTIPLIERS = new long[]{
            1,
            2 << 9,
            2 << 19,
            2 << 29,
            2L << 39,
            2L << 49,  // PB = 1024^5 == 2 << 49
            2L << 59}; // EB = 1024^6 == 2 << 59
    private final static Map<String, Integer> UNIT_TO_BYTE_MULTIPLIERS_MAP;

    static {
        UNIT_TO_BYTE_MULTIPLIERS_MAP = new HashMap<>();
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("B", 0);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("octets", 0);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("KB", 1);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("MB", 2);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("GB", 3);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("TB", 4);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("PB", 5);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("EB", 6);
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public String getReferrerUrl() {
        return getDetailsUrl();
    }

    protected long parseSize(String group) {
        String[] size = group.trim().split(" ");
        String amount = size[0].trim();
        String unit = size[1].trim();
        return calculateSize(amount, unit);
    }

    protected long calculateSize(String amount, String unit) {
        if (amount == null || unit == null) {
            return -1;
        }
        amount = amount.replaceAll(",", "");
        final Integer unitMultiplier = UNIT_TO_BYTE_MULTIPLIERS_MAP.get(unit);
        long multiplier = 1;
        if (unitMultiplier != null) {
            multiplier = BYTE_MULTIPLIERS[unitMultiplier];
        }
        //fractional size
        if (amount.indexOf(".") > 0) {
            float floatAmount = Float.parseFloat(amount);
            return (long) (floatAmount * multiplier);
        }
        //integer based size
        else {
            int intAmount = Integer.parseInt(amount);
            return intAmount * multiplier;
        }
    }
}
