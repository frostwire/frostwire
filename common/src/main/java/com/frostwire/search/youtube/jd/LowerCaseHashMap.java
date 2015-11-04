package com.frostwire.search.youtube.jd;

import java.util.LinkedHashMap;
import java.util.Locale;

public class LowerCaseHashMap<V> extends LinkedHashMap<String, V> {

    /**
     * 
     */
    private static final long serialVersionUID = 4571590512548374247L;

    @Override
    public V get(final Object key) {
        if (key != null && key.getClass() == String.class) { return super.get(((String) key).toLowerCase(Locale.ENGLISH)); }
        return super.get(key);
    }

    @Override
    public V put(final String key, final V value) {
        if (key != null) {
            return super.put(key.toLowerCase(Locale.ENGLISH), value);
        } else {
            return super.put(key, value);
        }
    }

    @Override
    public V remove(final Object key) {
        if (key != null && key.getClass() == String.class) { return super.remove(((String) key).toLowerCase(Locale.ENGLISH)); }
        return super.remove(key);
    }
}
