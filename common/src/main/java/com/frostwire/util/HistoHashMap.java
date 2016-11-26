package com.frostwire.util;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class HistoHashMap<K> {
    private final Map<K,Integer> map = new HashMap<>();

    /**
     * (Cheap operation)
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
     * @return
     */
    public Entry<K,Integer>[] histogram() {
        Set<Entry<K, Integer>> entrySet = map.entrySet();
        @SuppressWarnings("unchecked")
        Entry<K, Integer>[] array = entrySet.toArray(new Entry[0]);
        Arrays.sort(array, new Comparator<Entry<K,Integer>>() {
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
