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

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public final class HistoHashMap<K> {

    private final Map<K, Integer> map = new HashMap<>();

    /**
     * (Cheap operation)
     *
     * @param key
     * @return
     */
    public int update(K key) {
        int r = 1;
        if (map.containsKey(key)) {
            r = 1 + map.get(key);
        }
        map.put(key, r);
        return r;
    }

    public Integer get(K key) {
        return map.get(key);
    }

    /**
     * (Expensive operation)
     * Returns the inner map as a sorted Entry array.
     *
     * @return
     */
    public Entry<K, Integer>[] histogram() {
        Set<Entry<K, Integer>> entrySet = new HashSet<>(map.entrySet());
        @SuppressWarnings("unchecked")
        Entry<K, Integer>[] array = entrySet.toArray(new Entry[0]);
        Arrays.sort(array, new Comparator<Entry<K, Integer>>() {
            @Override
            public int compare(Entry<K, Integer> o1, Entry<K, Integer> o2) {
                return o1.getValue().compareTo(o2.getValue());
            }
        });
        return array;
    }

    public int getKeyCount() {
        return map.size();
    }

    public void reset() {
        map.clear();
    }
}
