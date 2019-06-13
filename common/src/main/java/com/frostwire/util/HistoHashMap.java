/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.util;

import java.util.*;
import java.util.Map.Entry;

public final class HistoHashMap<K> {
    private final HashMap<K, Integer> map = new HashMap<>();
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
        return map.get(key);
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
