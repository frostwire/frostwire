/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa)
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

public final class HistoHashMap<K> {
    // Pre-size for typical usage: ~50 keys at 0.75 load factor = 64 capacity
    private final HashMap<K, Integer> map = new HashMap<>(64);
    // creates the comparator as a field to avoid GC pressure every
    // time histogram is called, but still not static (no need)
    private final Comparator<Entry<K, Integer>> cmp = (o1, o2) -> o2.getValue().compareTo(o1.getValue());

    /**
     * (Cheap operation)
     *
     * @param key the key
     * @return the frequency
     */
    public int update(K key) {
        int r = 1;
        // This is a problematic operation in light of the heavy concurrency to which
        // an instance of this class is subject to. The problem is that when the reset()
        // is called, the following operations are not atomic and can yield a NPE.
        // Still, there is no need of synchronization, since a simple call to map.getOrDefault
        // could do the trick, but that method is not in android API 19.
        if (map.containsKey(key)) {
            Integer n = map.get(key);
            if (n == null) {
                // a reset was called in the middle, this key can't be considered
                // since this update was called before the reset
                r = 0;
            } else {
                r = 1 + n;
            }
        }
        map.put(key, r);
        return r;
    }

    public int get(K key) {
        try {
            //noinspection ConstantConditions
            return map.get(key);
        } catch (NullPointerException npe) {
            return 0;
        }
    }

    /**
     * (Expensive operation)
     * Returns sorted list of entries.
     *
     * @return the list
     */
    public List<Entry<K, Integer>> histogram() {
        try {
            ArrayList<Entry<K, Integer>> list = new ArrayList<>(map.entrySet());
            Collections.sort(list, cmp);
            return Collections.unmodifiableList(list);
        } catch (ConcurrentModificationException e) {
            // working with no synchronized structures, even with the copies
            // it's possible that this exception can happens, but the cost
            // of synchronization is bigger than the lack of accuracy
            // ignore
            return Collections.emptyList();
        }
    }

    public int getKeyCount() {
        return map.size();
    }

    public void reset() {
        map.clear();
    }
}
