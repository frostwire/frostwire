/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    public final static long[] BYTE_MULTIPLIERS = new long[]{1, 2 << 9, 2 << 19, 2 << 29, 2 << 39, 2 << 49};
    public static final Map<String, Integer> UNIT_TO_BYTE_MULTIPLIERS_MAP;
    static {
        UNIT_TO_BYTE_MULTIPLIERS_MAP = new HashMap<>();
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("B", 0);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("octets", 0);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("KB", 1);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("MB", 2);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("GB", 3);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("TB", 4);
        UNIT_TO_BYTE_MULTIPLIERS_MAP.put("PB", 5);
    }

    private int uid = -1;

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public int uid() {
        if (uid == -1) {
            String key = getDisplayName() + getDetailsUrl() + getSource() + getHash();
            uid = key.hashCode();
        }
        return uid;
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
            return  -1;
        }

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